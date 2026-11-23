package app.ocmobile.net

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

/** OkHttp, the engine the phone already ships. */
actual fun httpEngineFactory(): HttpClientEngineFactory<*> = OkHttp
