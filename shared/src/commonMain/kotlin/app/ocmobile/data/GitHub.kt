package app.ocmobile.data

import app.ocmobile.Graph
import app.ocmobile.net.httpEngineFactory
import app.ocmobile.platform.nowMillis
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * GitHub sign-in as a device code (like `gh auth login --web`): the app shows a code, the person
 * approves it in the browser, and the token comes back to us.
 *
 * Android went further and installed git and the GitHub CLI inside the Ubuntu container, then
 * handed the token to `gh` and set the git identity. None of that applies on iOS: the repositories,
 * git and gh live on the computer that runs the server, and a client has no way to run commands
 * there. So [setup] stops after asking GitHub who the person is, and the sign-in is only remembered
 * in [Graph.prefs] for the account rows in the app.
 */
object GitHub {
    /** GitHub CLI's public client id. */
    private const val CLIENT_ID = "178c6fc778ccc68e1d6a"
    private const val SCOPES = "repo read:org gist workflow"

    val prompt = """
# Git and GitHub

- Commits are made as the user (git config user.name / user.email are already set). Never add "Co-Authored-By", "Generated with", "Signed-off-by" for yourself, or any other AI or assistant attribution to commit messages, pull requests, issues, release notes or code comments.
- If the user signed in to GitHub in the app, the gh CLI is logged in and git can push over https. Use gh for repos, pull requests, issues and releases.
""".trim()

    sealed interface State {
        data object Idle : State
        data class Code(val userCode: String, val url: String) : State
        data class Working(val step: String, val log: String = "") : State
        data object Done : State
        data class Failed(val message: String) : State
    }

    val state = MutableStateFlow<State>(State.Idle)
    private val json = Json { ignoreUnknownKeys = true }

    /** Ktor in place of OkHttp: the same engine the rest of the client uses. */
    private val http by lazy {
        HttpClient(httpEngineFactory()) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 30_000
                requestTimeoutMillis = 60_000
            }
        }
    }

    val signedIn get() = Graph.prefs.githubLogin.isNotBlank()

    private suspend fun post(url: String, form: Map<String, String>): JsonObject {
        val body = FormDataContent(Parameters.build { form.forEach { (k, v) -> append(k, v) } })
        val text = http.post(url) {
            header(HttpHeaders.Accept, "application/json")
            setBody(body)
        }.bodyAsText()
        return json.parseToJsonElement(text).jsonObject
    }

    private suspend fun user(token: String): JsonObject {
        val res = http.get("https://api.github.com/user") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, "application/vnd.github+json")
        }
        val text = res.bodyAsText()
        if (!res.status.isSuccess()) error("GitHub said ${res.status.value}")
        return json.parseToJsonElement(text).jsonObject
    }

    private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.takeIf { it.isString }?.content

    /** The whole sign-in: code, wait for confirmation in the browser, then remember the account. */
    suspend fun signIn() {
        try {
            val dc = post("https://github.com/login/device/code", mapOf("client_id" to CLIENT_ID, "scope" to SCOPES))
            val device = dc.s("device_code") ?: error(dc.s("error_description") ?: "GitHub did not give a code")
            val code = dc.s("user_code") ?: error("GitHub did not give a code")
            var interval = (dc["interval"]?.jsonPrimitive?.int ?: 5).coerceAtLeast(5)
            val until = nowMillis() + (dc["expires_in"]?.jsonPrimitive?.long ?: 900) * 1000
            state.value = State.Code(code, dc.s("verification_uri") ?: "https://github.com/login/device")
            var token: String? = null
            while (token == null) {
                if (state.value !is State.Code) return   // cancelled
                if (nowMillis() > until) error("The code expired. Try again.")
                delay(interval * 1000L)
                val r = post(
                    "https://github.com/login/oauth/access_token",
                    mapOf("client_id" to CLIENT_ID, "device_code" to device, "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"),
                )
                token = r.s("access_token")
                when (r.s("error")) {
                    null, "authorization_pending" -> {}
                    "slow_down" -> interval += 5
                    "access_denied" -> error("You declined the request on GitHub.")
                    "expired_token" -> error("The code expired. Try again.")
                    else -> if (token == null) error(r.s("error_description") ?: "GitHub sign-in failed")
                }
            }
            setup(token)
        } catch (e: Exception) {
            state.value = State.Failed(e.message ?: "GitHub sign-in failed")
        }
    }

    /** Asks who the account is and stores it. Installing gh and setting git up is the computer's job. */
    private suspend fun setup(token: String) {
        state.value = State.Working("Checking your account")
        val u = user(token)
        val login = u.s("login") ?: error("No login in the GitHub answer")
        val name = u.s("name")?.takeIf { it.isNotBlank() } ?: login
        Graph.prefs.githubLogin = login
        Graph.prefs.githubName = name
        Graph.prefs.githubAvatar = u.s("avatar_url").orEmpty()
        Memory.sync()
        state.value = State.Done
    }

    fun cancel() { state.value = State.Idle }

    /** Forgets the account here. The token in gh on the computer is not ours to remove. */
    suspend fun signOut() {
        Graph.prefs.githubLogin = ""
        Graph.prefs.githubName = ""
        Graph.prefs.githubAvatar = ""
        state.value = State.Idle
    }

    /**
     * Who commits are signed by. On Android this read `git config` inside the container; the git
     * identity now lives on the computer and the client has no command to read it, so the screen
     * shows a placeholder instead of guessing.
     */
    suspend fun gitIdentity(): Pair<String, String>? = null
}
