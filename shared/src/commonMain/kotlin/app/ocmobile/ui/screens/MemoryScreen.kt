package app.ocmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.data.MemFile
import app.ocmobile.data.Memory
import app.ocmobile.ui.Nav
import app.ocmobile.ui.components.EmptyState
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.MemoryGraphView
import app.ocmobile.ui.components.GraphColors
import app.ocmobile.ui.components.MemNode
import app.ocmobile.ui.components.OcDialog
import app.ocmobile.ui.components.OcSheet
import app.ocmobile.ui.components.OcSwitch
import app.ocmobile.ui.components.OcTextField
import app.ocmobile.ui.components.Pill
import app.ocmobile.ui.components.SectionLabel
import app.ocmobile.ui.components.Segmented
import app.ocmobile.ui.components.SettingsGroup
import app.ocmobile.ui.components.SettingsRow
import app.ocmobile.ui.components.SmallButton
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.UsageBar
import app.ocmobile.ui.components.tap
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import app.ocmobile.ui.theme.screenBackdrop
import kotlin.math.round

private fun pretty(f: MemFile) = f.nameWithoutExtension.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun shelfIcon(name: String): ImageVector = when (name.removeSuffix(".md")) {
    "about-me" -> Ic.User; "work" -> Ic.Laptop; "projects" -> Ic.Folder; "people" -> Ic.Chat
    "routines" -> Ic.Clock; "health" -> Ic.Drop; "ideas" -> Ic.Sparkle; "music" -> Ic.Music; "inbox" -> Ic.Mail
    else -> Ic.File
}

/** "%.1f KB"-style formatting without java.util.Formatter. */
private fun kb(b: Long): String {
    if (b < 1024) return "$b B"
    val tenths = round(b / 102.4).toLong()
    return "${tenths / 10}.${tenths % 10} KB"
}

/**
 * Memory: a summary, what and how to remember, shelves with separate facts, your own rules.
 * Everything changed here goes straight into the agent's instructions (Memory.sync).
 *
 * Ported from Android. The shelves live on the computer, so they are read with `Memory.load()` and
 * the changes are made on the mirror; import/export (a zip or `content://` URIs on Android) are the
 * text sheet and the clipboard here, because a client has neither a filesystem nor a document picker.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryScreen(nav: Nav) {
    val c = Oc.c
    val prefs = Graph.prefs
    var rev by remember { mutableStateOf(0) }
    var enabled by remember { mutableStateOf(prefs.memoryEnabled) }
    var mode by remember { mutableStateOf(prefs.memoryMode) }
    var topics by remember { mutableStateOf(prefs.memoryTopics.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()) }
    var note by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf(prefs.memoryCustom) }
    var customSaved by remember { mutableStateOf(true) }
    var open by remember { mutableStateOf<MemFile?>(null) }
    var showPrompt by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    // the graph comes first; the shelves, settings and the rest are one tap away
    var more by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<MemNode?>(null) }
    // import/export: the Android file picker and the cache zip become text here (no filesystem on a client)
    var importing by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var importName by remember { mutableStateOf("notes.md") }
    var importText by remember { mutableStateOf("") }
    val shelves = remember(rev, Memory.version) { Memory.shelves() }
    val facts = remember(rev, Memory.version) { shelves.associateWith { Memory.facts(it) } }
    val total = facts.values.sumOf { it.size }
    val bytes = remember(rev, Memory.version) { shelves.sumOf { it.length() } + Memory.indexLength() }
    fun changed() { Memory.sync(); rev++ }
    val clip = LocalClipboardManager.current

    // the shelf files are on the computer: pulled once when the screen opens
    LaunchedEffect(Unit) { Memory.load(); rev++ }

    Column(Modifier.fillMaxSize().screenBackdrop("MemoryScreen1")) {
        TopBar(if (more) "Memory settings" else "Memory", left = { IconBtn(Ic.Back, { if (more) more = false else nav.pop() }) }, right = {
            // in: a memory export (Claude's zip) or .md notes; out: the shelves as text
            IconBtn(Ic.FileUp, { importing = true })
            IconBtn(Ic.Download, { exporting = true })
        })
        if (!more) Box(Modifier.weight(1f).fillMaxWidth()) {
            MemoryGraphView(
                Memory.graphFiles(), rev,
                GraphColors(c.border, c.text, c.textDim, c.accent, c.text.copy(alpha = 0.75f), c.bg),
                onSelect = { picked = it },
                onNew = { app.ocmobile.data.Sounds.play(app.ocmobile.data.Sounds.S.Pop) },
            )
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                picked?.let { n ->
                    Column(Modifier.fillMaxWidth().clip(Oc.round(20.dp)).background(c.surface).border(1.dp, c.border, Oc.round(20.dp)).clickable { n.file?.takeIf { it != "MEMORY.md" }?.let { open = Memory.shelf(it) } }.padding(16.dp)) {
                        Txt(n.label, size = 15.sp, maxLines = 4)
                        n.file?.takeIf { n.kind == 2 }?.let { Txt(it.substringBeforeLast('.'), size = 12.sp, color = c.textDim) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Txt(if (total == 0) "Nothing saved yet" else "$total facts on ${shelves.size} shelves", Modifier.weight(1f).align(Alignment.CenterVertically), size = 13.sp, color = c.textDim)
                    Pill("More", { more = true }, filled = false)
                }
            }
        } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(bottom = 32.dp)) {
            // Summary
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(Oc.round(24.dp)).background(c.surface).border(1.dp, c.border, Oc.round(24.dp)).padding(20.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Txt("$total", size = 40.sp, family = Oc.heading, weight = FontWeight.SemiBold, lineHeight = 44.sp)
                    Spacer(Modifier.width(10.dp))
                    Txt(if (total == 1) "fact" else "facts", Modifier.padding(bottom = 6.dp), size = 17.sp, color = c.textDim)
                    Spacer(Modifier.weight(1f))
                    Txt("${shelves.size} ${if (shelves.size == 1) "shelf" else "shelves"}", Modifier.padding(bottom = 6.dp), size = 15.sp, color = c.textDim)
                }
                Spacer(Modifier.height(12.dp))
                UsageBar((bytes / 24_000f).coerceIn(0f, 1f))
                Spacer(Modifier.height(8.dp))
                Txt(
                    if (enabled) "${kb(bytes)} · loaded into every chat, so OpenCode answers without opening files"
                    else "Memory is off — OpenCode neither reads nor saves it",
                    size = 13.sp, color = c.textDim, lineHeight = 18.sp,
                )
            }

            SectionLabel("Behaviour")
            SettingsGroup {
                SettingsRow("Use memory", subtitle = "Remember you across chats", icon = Ic.Scroll, trailing = {
                    OcSwitch(enabled) { enabled = it; prefs.memoryEnabled = it; Memory.sync() }
                })
            }
            SectionLabel("Saving")
            Segmented(
                listOf("ask" to "Ask", "auto" to "On its own", "off" to "When asked"), mode,
                { mode = it; prefs.memorySave = it; Memory.sync() }, Modifier.padding(horizontal = 16.dp),
            )
            Txt(
                when (mode) { "ask" -> "Offers what it heard about you as a card, you tap Remember"; "auto" -> "Notices lasting facts and saves them"; else -> "Only when you say \"remember…\"" },
                size = 13.sp, color = c.textDim, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            SectionLabel("What to remember")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Memory.topics.forEach { (id, title, _) ->
                    val on = id in topics
                    Row(
                        Modifier.clip(Oc.pill).background(if (on) c.accent else c.surface).border(1.dp, if (on) c.accent else c.border, Oc.pill)
                            .tap(scale = 0.96f) {
                                topics = if (on) topics - id else topics + id
                                prefs.memoryTopics = Memory.topics.map { it.first }.filter { it in topics }.joinToString(",")
                                Memory.sync()
                            }.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Ico(shelfIcon(id), tint = if (on) Color.White else c.textDim, size = 16.dp)
                        Spacer(Modifier.width(7.dp))
                        Txt(title, size = 14.sp, color = if (on) Color.White else c.text)
                    }
                }
            }
            Txt(
                if (topics.isEmpty()) "Nothing picked — OpenCode files facts on any topic." else "OpenCode keeps only these shelves.",
                Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp), size = 13.sp, color = c.textDim,
            )

            SectionLabel("Shelves")
            if (shelves.isEmpty()) {
                EmptyState(Ic.Sparkle, "Nothing remembered yet", "Say \"remember that…\" in a chat, or add a fact below. I'll ask before keeping anything.")
            } else Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                shelves.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { f ->
                            val list = facts[f].orEmpty()
                            Column(
                                Modifier.weight(1f).heightIn(min = 128.dp).clip(Oc.round(20.dp)).background(c.surface).border(1.dp, c.border, Oc.round(20.dp))
                                    .tap(scale = 0.97f) { open = f }.padding(14.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(32.dp).clip(Oc.round(10.dp)).background(c.progressTrack), contentAlignment = Alignment.Center) {
                                        Ico(shelfIcon(f.name), tint = c.accent, size = 17.dp)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Txt("${list.size}", size = 13.sp, color = c.textDim, family = Oc.label)
                                }
                                Spacer(Modifier.height(10.dp))
                                Txt(pretty(f), size = 16.sp, weight = FontWeight.Medium, maxLines = 1)
                                Txt(list.firstOrNull() ?: kb(f.length()), Modifier.padding(top = 3.dp), size = 13.sp, color = c.textDim, maxLines = 2, lineHeight = 17.sp)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            val notes = remember(rev, Memory.version) { Memory.projectNotes() }
            if (notes.isNotEmpty()) {
                SectionLabel("Project notes · ${notes.size}")
                Txt("Not loaded into every chat — OpenCode opens a note when the project comes up.", Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp), size = 13.sp, color = c.textDim, lineHeight = 18.sp)
                SettingsGroup {
                    notes.forEach { f ->
                        val first = runCatching { f.readLines().drop(1).firstOrNull { it.isNotBlank() } }.getOrNull().orEmpty()
                        SettingsRow(pretty(f), subtitle = first.take(70), icon = Ic.Folder, onClick = { open = f })
                    }
                }
            }

            SectionLabel("Tell it something")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OcTextField(note, { note = it }, placeholder = "I live in Kraków and work nights", singleLine = false, minLines = 2)
                Pill("Add to memory", { Memory.addNote(note); note = ""; changed() }, enabled = note.isNotBlank(), modifier = Modifier.fillMaxWidth())
                Txt("Goes to the inbox; OpenCode files it onto the right shelf in your next chat.", size = 13.sp, color = c.textDim)
            }

            SectionLabel("Your rules")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OcTextField(custom, { custom = it; customSaved = false }, placeholder = "e.g. keep each shelf under 30 lines; never store addresses", singleLine = false, minLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(if (customSaved) "Saved" else "Save", { prefs.memoryCustom = custom.trim(); Memory.sync(); customSaved = true }, filled = !customSaved)
                    SmallButton("What OpenCode sees", { showPrompt = true })
                }
            }

            if (shelves.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                SettingsGroup { SettingsRow("Clear all memory", icon = Ic.Trash, titleColor = c.danger, onClick = { confirmClear = true }) }
            }
        }
    }

    open?.let { f -> ShelfSheet(f, onClose = { open = null }, onChanged = { changed() }) }
    if (showPrompt) OcSheet({ showPrompt = false }, "What OpenCode sees", skipHalf = false) {
        Txt(
            Memory.prompt() + "\n\n" + Memory.snapshot(),
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            size = 13.sp, color = c.textDim, lineHeight = 19.sp, family = Oc.mono,
        )
    }
    if (confirmClear) {
        OcDialog("Clear all memory?", { confirmClear = false }, "Clear", {
            (Memory.shelves() + Memory.projectNotes()).forEach { it.delete() }
            Memory.index().writeText("# Memory index\n\nNothing saved yet.\n")
            confirmClear = false; changed()
        }, danger = true) { Txt("All shelves are deleted. This cannot be undone.", size = 16.sp, color = c.textDim) }
    }
    // Android picked files and zipped them; a client has no filesystem, so the same import is pasted here
    if (importing) OcSheet({ importing = false }, "Import memory", skipHalf = false) {
        Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt("Paste .md notes — one shelf per heading; facts with \"- \" lines merge into shelves that exist. A memory export from Claude works too.", size = 13.sp, color = c.textDim, lineHeight = 18.sp)
            OcTextField(importName, { importName = it }, placeholder = "about-me.md")
            OcTextField(importText, { importText = it }, singleLine = false, minLines = 8, mono = true, placeholder = "# About me\n\n- I live in Kraków\n")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SmallButton("Cancel", { importing = false })
                Spacer(Modifier.weight(1f))
                SmallButton("Import", {
                    val n = Memory.importFiles(listOf(importName.ifBlank { "notes.md" } to importText))
                    Graph.chats.toasts.tryEmit(if (n >= 0) "Imported $n notes into memory" else "Couldn't import")
                    importText = ""; importing = false; rev++
                }, filled = importText.isNotBlank())
            }
        }
    }
    // the export zip went to Download on Android; here the shelves go out as text the user can copy
    if (exporting) OcSheet({ exporting = false }, "Export memory", skipHalf = false) {
        Column(Modifier.imePadding().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt("The shelves as one text: the index, then every shelf and project note.", size = 13.sp, color = c.textDim, lineHeight = 18.sp)
            Txt(
                Memory.exportText(),
                Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).clip(Oc.round(16.dp)).background(c.surface).padding(14.dp),
                size = 13.sp, color = c.textDim, lineHeight = 19.sp, family = Oc.mono,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                SmallButton("Copy", {
                    clip.setText(AnnotatedString(Memory.exportText()))
                    Graph.chats.toasts.tryEmit("Memory copied")
                    exporting = false
                }, filled = true)
            }
        }
    }
}

/** A shelf: facts one by one (delete with the cross), add a fact, edit as text. */
@Composable
private fun ShelfSheet(f: MemFile, onClose: () -> Unit, onChanged: () -> Unit) {
    val c = Oc.c
    var rev by remember { mutableStateOf(0) }
    var raw by remember { mutableStateOf(false) }
    var text by remember(f, raw) { mutableStateOf(runCatching { f.readText() }.getOrDefault("")) }
    var add by remember { mutableStateOf("") }
    val list = remember(rev) { Memory.facts(f) }
    OcSheet(onClose, pretty(f), skipHalf = false) {
        Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (raw) {
                OcTextField(text, { text = it }, singleLine = false, minLines = 8, mono = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SmallButton("Cancel", { raw = false })
                    Spacer(Modifier.weight(1f))
                    SmallButton("Save", { f.writeText(text.trimEnd() + "\n"); raw = false; rev++; onChanged() }, filled = true)
                }
            } else {
                if (list.isEmpty()) Txt("No facts on this shelf yet.", size = 15.sp, color = c.textDim)
                list.forEach { fact ->
                    Row(
                        Modifier.fillMaxWidth().clip(Oc.round(16.dp)).background(c.surface).padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(c.accent))
                        Spacer(Modifier.width(12.dp))
                        Txt(fact, Modifier.weight(1f).padding(vertical = 6.dp), size = 15.sp, lineHeight = 21.sp)
                        IconBtn(Ic.Close, { Memory.removeFact(f, fact); rev++; onChanged() }, size = 36.dp, iconSize = 16.dp, tint = c.textDim)
                    }
                }
                Spacer(Modifier.height(4.dp))
                OcTextField(add, { add = it }, placeholder = "Add a fact to ${pretty(f).lowercase()}", singleLine = false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (f.name != "MEMORY.md") SmallButton("Delete shelf", { f.delete(); onChanged(); onClose() }, danger = true)
                    SmallButton("Edit as text", { raw = true })
                    Spacer(Modifier.weight(1f))
                    SmallButton("Add", { if (add.isNotBlank()) { Memory.addFact(f, add); add = ""; rev++; onChanged() } }, filled = add.isNotBlank())
                }
            }
        }
    }
}
