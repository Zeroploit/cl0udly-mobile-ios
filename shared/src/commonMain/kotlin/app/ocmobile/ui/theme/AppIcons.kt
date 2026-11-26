package app.ocmobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * One entry of the icon set: the id saved in the settings, its label and the tile colour.
 *
 * Android ships each icon as an `activity-alias` with its own drawable and switches between
 * them in the launcher. iOS has one icon chosen at build time and no API to change it, so
 * [apply] is a no-op here and only the colour is used to draw the preview.
 */
class AppIcon(val id: String, val label: String, val bg: Color)

/** The app's icon set, in the order the picker shows them. */
object AppIcons {
    val all = listOf(
        // for the interface looks
        AppIcon("look_cloud", "cl0udly", Color(0xFF232428)),
        AppIcon("cloud_light", "cl0udly · light", Color(0xFFF4F5F7)),
        AppIcon("look_claude", "Warm", Color(0xFFD97757)),
        AppIcon("look_terminal", "OpenCode · Terminal", Color(0xFF0A0A0A)),
        AppIcon("look_desktop", "OpenCode · Desktop", Color(0xFF1E1E1E)),
        AppIcon("look_hairline", "Hairline", Color(0xFF000000)),
        AppIcon("look_chatgpt", "ChatGPT", Color(0xFF000000)),
        AppIcon("look_kimi", "Kimi", Color(0xFF111111)),
        AppIcon("look_notebook", "Notebook", Color(0xFFF7F3EA)),
        // colored
        AppIcon("terracotta", "Terracotta", Color(0xFFDE7356)),
        AppIcon("classic", "Classic", Color(0xFF0E0F13)),
        AppIcon("blue", "Blue", Color(0xFF4A7DFB)),
        AppIcon("cream", "Cream", Color(0xFFF3ECE0)),
        AppIcon("cream_blue", "Cream & blue", Color(0xFFF3ECE0)),
        AppIcon("graphite", "Graphite", Color(0xFF1C1D22)),
        AppIcon("green", "Green", Color(0xFF3F8F6B)),
        AppIcon("mustard", "Mustard", Color(0xFFE5A93D)),
        AppIcon("pink", "Pink", Color(0xFFE46A8F)),
        AppIcon("teal", "Teal", Color(0xFF2B8A96)),
        AppIcon("violet", "Violet", Color(0xFF7B61D9)),
    )

    const val DEFAULT = "look_cloud"

    /** iOS keeps a single icon: the choice is remembered for the previews and nothing else happens. */
    fun apply(id: String) {
        // The launcher icon cannot be swapped at runtime on iOS; the setting is only cosmetic here.
    }
}
