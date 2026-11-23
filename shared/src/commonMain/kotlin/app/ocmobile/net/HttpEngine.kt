package app.ocmobile.net

import io.ktor.client.engine.HttpClientEngineFactory

/** Where the OpenCode server is, and the password it expects. */
data class ServerEndpoint(val url: String, val password: String)

/**
 * The engine behind every HTTP request: OkHttp on Android and on the JVM (so
 * the Windows canary runs the engine the phone runs), NSURLSession on iOS.
 */
expect fun httpEngineFactory(): HttpClientEngineFactory<*>
