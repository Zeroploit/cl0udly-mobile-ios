package app.ocmobile.net

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

/** OkHttp, the engine the app's networking was written against. */
actual fun httpEngineFactory(): HttpClientEngineFactory<*> = OkHttp
