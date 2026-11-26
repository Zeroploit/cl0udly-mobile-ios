@file:OptIn(ExperimentalForeignApi::class)

package app.ocmobile.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSLocale
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognitionTask
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import kotlin.math.sqrt

/**
 * Live dictation with SFSpeechRecognizer over an AVAudioEngine tap.
 *
 * Apple's recognizer keeps listening until it decides the utterance ended (up to a minute), which
 * would leave the composer's bar running long after the user stopped talking. So a chunk ends on
 * the app's own silence: no new words for [SILENCE_MS] means the phrase is done, it goes out as
 * final and the caller starts a new session for the next phrase.
 *
 * Every callback is delivered on the main queue, because the callers hold Compose state. [session]
 * makes callbacks from a stopped run harmless when the user stops and starts quickly.
 */
private const val SILENCE_MS = 1400L

private var sharedEngine: SpeechEngine? = null

actual fun speechEngine(): SpeechEngine? = sharedEngine ?: IosSpeechEngine().also { sharedEngine = it }

private class IosSpeechEngine : SpeechEngine {

    private var audio: AVAudioEngine? = null
    private var request: SFSpeechAudioBufferRecognitionRequest? = null
    private var task: SFSpeechRecognitionTask? = null
    private var session = 0
    private var silence: Job? = null
    private var heard = ""
    private var onFinalRef: ((String) -> Unit)? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun start(
        langTag: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onLevel: (Float) -> Unit,
        onError: (String) -> Unit,
    ) {
        stop()
        val id = ++session
        onFinalRef = onFinal
        SFSpeechRecognizer.requestAuthorization { status ->
            onMain {
                if (id != session) return@onMain
                if (status != SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized) {
                    onError("Speech recognition is not allowed")
                    return@onMain
                }
                askMicrophone(id, langTag, onPartial, onFinal, onLevel, onError)
            }
        }
    }

    private fun askMicrophone(
        id: Int,
        langTag: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onLevel: (Float) -> Unit,
        onError: (String) -> Unit,
    ) {
        val audioSession = AVAudioSession.sharedInstance()
        if (audioSession.recordPermission == AVAudioSessionRecordPermissionGranted) {
            begin(id, langTag, onPartial, onFinal, onLevel, onError)
            return
        }
        audioSession.requestRecordPermission { granted ->
            onMain {
                if (id != session) return@onMain
                if (granted) begin(id, langTag, onPartial, onFinal, onLevel, onError)
                else onError("Microphone access is not allowed")
            }
        }
    }

    private fun begin(
        id: Int,
        langTag: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onLevel: (Float) -> Unit,
        onError: (String) -> Unit,
    ) {
        // the device's recognizer must know the language: ask it which locales it has
        val supported = SFSpeechRecognizer.supportedLocales().mapNotNull { (it as? NSLocale)?.localeIdentifier }
        val locale = supported.firstOrNull { it.equals(langTag, ignoreCase = true) }
            ?: supported.firstOrNull { it.startsWith(langTag.substringBefore('-') + "-") }
            ?: langTag
        val speech = SFSpeechRecognizer(locale = NSLocale(localeIdentifier = locale))
        if (speech == null || !speech.available) {
            onError("Speech recognition is not available")
            return
        }

        val audioSession = AVAudioSession.sharedInstance()
        audioSession.setCategory(AVAudioSessionCategoryRecord, error = null)
        audioSession.setActive(true, error = null)

        val chunk = SFSpeechAudioBufferRecognitionRequest()
        chunk.shouldReportPartialResults = true

        val engine = AVAudioEngine()
        val input = engine.inputNode
        val format = input.outputFormatForBus(0u)
        input.installTapOnBus(0u, bufferSize = 1024u, format = format) { buffer, _ ->
            if (buffer != null) {
                chunk.appendAudioPCMBuffer(buffer)
                val level = levelOf(buffer)
                onMain { if (id == session) onLevel(level) }
            }
        }
        engine.prepare()
        val started = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            engine.startAndReturnError(error.ptr)
        }
        if (!started) {
            input.removeTapOnBus(0u)
            audioSession.setActive(false, error = null)
            onError("The microphone could not start")
            return
        }

        audio = engine
        request = chunk
        heard = ""
        task = speech.recognitionTaskWithRequest(chunk) { result, error ->
            onMain {
                if (id != session) return@onMain
                val text = result?.bestTranscription?.formattedString.orEmpty().trim()
                if (text.isNotEmpty() && text != heard) {
                    heard = text
                    onPartial(text)
                    armSilence(id, onFinal)
                }
                if (error != null) {
                    val reason = error.localizedDescription
                    finishChunk(id, onFinal)
                    if (heard.isBlank()) onError(reason ?: "Speech recognition stopped")
                }
            }
        }
    }

    /** No new words for a moment: this phrase is over. */
    private fun armSilence(id: Int, onFinal: (String) -> Unit) {
        silence?.cancel()
        silence = scope.launch {
            delay(SILENCE_MS)
            if (id == session && heard.isNotBlank()) finishChunk(id, onFinal)
        }
    }

    private fun finishChunk(id: Int, onFinal: (String) -> Unit) {
        if (id != session) return
        val text = heard.trim()
        teardown()
        session++
        if (text.isNotBlank()) onFinal(text)
    }

    override fun stop() {
        session++
        silence?.cancel()
        silence = null
        teardown()
    }

    private fun teardown() {
        audio?.stop()
        audio?.inputNode?.removeTapOnBus(0u)
        audio = null
        request?.endAudio()
        request = null
        task?.cancel()
        task = null
        heard = ""
        AVAudioSession.sharedInstance().setActive(false, error = null)
    }

    /** Loudness for the wave: the quartic root of the mean square, so quiet speech still moves it. */
    private fun levelOf(buffer: AVAudioPCMBuffer): Float {
        val channel = buffer.floatChannelData?.get(0) ?: return 0f
        val frames = buffer.frameLength.toInt()
        if (frames == 0) return 0f
        var sum = 0.0
        for (i in 0 until frames) {
            val sample = channel[i].toDouble()
            sum += sample * sample
        }
        return sqrt(sqrt(sum / frames)).toFloat().coerceIn(0f, 1f)
    }
}
