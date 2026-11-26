package app.ocmobile.data

import app.ocmobile.Graph
import app.ocmobile.api.arr
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Free fallback routes for when Zen's built-in free models hit the server limit (it is counted on
 * Zen's side, does not always reset and does not depend on the client):
 *  - GitHub Copilot: signed in, but the models that answer on the free plan are added as our own provider;
 *  - Pollinations: an anonymous OpenAI-compatible gateway, no account needed.
 *
 * Ported from the Android `FreeModels.kt`, which wrote `opencode.jsonc` inside the environment the
 * phone itself ran. On iOS the provider lives on the computer, so only the part the server's API
 * covers is here: reading whether a provider is on, and turning one off.
 *
 * Left out on purpose:
 *  - `apply(providerId, block)`, `enableCopilot` and `routeThroughFix`: they write a `provider` block
 *    whose `baseURL` points at the phone's localhost proxy (`ToolCallFix`), which a computer cannot
 *    reach, and they read the Copilot token from `auth.json` on the phone;
 *  - `enablePollinations`: it already exists as [Chats.enablePollinations], which builds the block
 *    on the server; do not duplicate it here.
 */
object FreeModels {
    const val COPILOT_ID = "copilot-chat"
    const val POLLINATIONS_ID = "pollinations"
    const val COPILOT_DEFAULT = "gpt-4.1-2025-04-14"
    const val POLLINATIONS_DEFAULT = "openai-fast"

    /** Whether the provider is in the catalog: a provider the server lists is one that is on. */
    fun isEnabled(providerId: String): Boolean = Graph.catalog.providers.value.any { it.id == providerId }

    /**
     * Turns a free route off through the server's global config: adds it to `disabled_providers`
     * (editing the file by hand would need a restart) and reloads the catalog so its models leave
     * the list. Failures are swallowed: disconnecting must not throw at the caller.
     */
    suspend fun disable(providerId: String) {
        runCatching {
            val current = (Graph.api.config().arr("disabled_providers") ?: JsonArray(emptyList()))
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            if (providerId !in current) {
                Graph.api.patchGlobalConfig(
                    buildJsonObject {
                        put("disabled_providers", JsonArray((current + providerId).map { JsonPrimitive(it) }))
                    },
                )
            }
        }
        runCatching { Graph.api.disposeAll() }
        runCatching { Graph.catalog.refresh() }
    }
}
