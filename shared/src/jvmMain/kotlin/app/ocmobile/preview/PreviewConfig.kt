package app.ocmobile.preview

import java.io.File

/**
 * `preview.properties`, looked up next to the working directory and up a couple
 * of levels (Gradle runs the app from the module folder):
 *
 * ```
 * server=http://127.0.0.1:4097
 * session=ses_preview_1
 * dark=true
 * ```
 *
 * `server` skips the connect form, `session` opens a chat straight away.
 */
object PreviewConfig {
    fun read(): Map<String, String> {
        val file = generateSequence(File(".").absoluteFile) { it.parentFile }
            .take(4)
            .map { File(it, "preview.properties") }
            .firstOrNull { it.isFile } ?: return emptyMap()
        println("preview: reading $file")
        return runCatching {
            java.util.Properties().apply { file.inputStream().use(::load) }
                .entries.associate { (k, v) -> k.toString() to v.toString() }
        }.getOrDefault(emptyMap())
    }
}