package app.ocmobile.net

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

/** NSURLSession: the system HTTP stack iOS gives an app. */
actual fun httpEngineFactory(): HttpClientEngineFactory<*> = Darwin
