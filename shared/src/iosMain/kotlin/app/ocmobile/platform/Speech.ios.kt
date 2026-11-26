@file:OptIn(ExperimentalForeignApi::class)

package app.ocmobile.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
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
 * A session runs from [start] to the first final result: the recognizer settles a chunk after a
 * pause, the engine tears its session down and the caller starts a new one for the next chunk.
 * Every callback is delivered on the main queue because the composer's state is Compose state.
 * [session] makes callbacks from an older run harmless when the user stops and starts quickly.
 */
private var sharedEngine: SpeechEngine? = null

actual fun speechEngine(): SpeechEngine? = sharedEngine ?: IosSpeechEngine().also { sharedEngine = it }

private class IosSpeechEngine : SpeechEngine {

    private var audio: AVAudioEngine? = null
    private var recognizer: SFSpeechRecognizer? = null
    private var request: SFSpeechAudioBufferRecognitionRequest? = null
    private var task: SFSpeechRecognitionTask? = null
    private var session = 0

    override fun start(
        langTag: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onLevel: (Float) -> Unit,
        onError: (String) -> Unit,
    ) {
        stop()
        val id = ++session
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
        val speech = SFSpeechRecognizer(locale = NSLocale(localeIdentifier = langTag))
        if (!speech.available) {
            onError("Speech recognition is not available")
            return
        }
        val audioSession = AVAudioSession.sharedInstance()
        audioSession.setCategory(AVAudioSessionCategoryRecord, error = null)
        audioSession.setActive(true, null)

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
            audioSession.setActive(false, null)
            onError("The microphone could not start")
            return
        }

        audio = engine
        recognizer = speech
        request = chunk
        task = speech.recognitionTaskWithRequest(chunk) { result, error ->
            onMain {
                if (id != session) return@onMain
                val text = result?.bestTranscription?.formattedString.orEmpty()
                if (result?.final == true) {
                    teardown()
                    if (text.isNotBlank()) onFinal(text.trim())
                } else if (result != null) {
                    onPartial(text)
                }
                if (error != null) {
                    teardown()
                    if (result?.final != true) onError(error.localizedDescription ?: "Speech recognition stopped")
                }
            }
        }
    }

    override fun stop() {
        teardown()
    }

    private fun teardown() {
        session++
        audio?.stop()
        audio?.inputNode?.removeTapOnBus(0u)
        audio = null
        request?.endAudio()
        request = null
        task?.cancel()
        task = null
        recognizer = null
        AVAudioSession.sharedInstance().setActive(false, null)
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
