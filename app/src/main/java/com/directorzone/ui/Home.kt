package com.directorzone.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.directorzone.catalog.*
import com.directorzone.data.Prefs
import com.directorzone.data.Project

private fun ago(ms: Long): String {
    val d = System.currentTimeMillis() - ms
    return when {
        d < 60_000 -> "just now"; d < 3_600_000 -> "${d / 60_000} min ago"; d < 86_400_000 -> "${d / 3_600_000} h ago"
        d < 7 * 86_400_000L -> "${d / 86_400_000} d ago"; else -> java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(ms))
    }
}
private fun mb(b: Long) = if (b >= 1L shl 30) "%.1f GB".format(b / 1073741824.0) else "%.0f MB".format(b / 1048576.0)

@Composable
fun HomeScreen(vm: EditorVM) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { vm.startFromPicker(it) }
    fun pick(kind: Pending, tpl: TemplateDef? = null) { vm.pending = kind; vm.pendingTemplate = tpl; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
    LaunchedEffect(vm.tab) { if (vm.tab == Tab.Assets || vm.tab == Tab.Profile) vm.refreshAssets() }

    Box(Modifier.fillMaxSize().background(DZ.bg)) {
        Crossfade(vm.tab, animationSpec = Motion.tween(220), label = "tab") { tab ->
            when (tab) {
                Tab.Home -> HomeTab(vm, ::pick)
                Tab.Projects -> ProjectsTab(vm)
                Tab.Assets -> AssetsTab(vm)
                Tab.Templates -> TemplatesTab(vm, ::pick)
                Tab.Profile -> ProfileTab(vm, ::pick)
            }
        }
        FloatingNav(vm.tab, { vm.tab = it }, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp))
        vm.toast?.let { msg ->
            LaunchedEffect(msg) { kotlinx.coroutines.delay(2000); vm.toast = null }
            Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 92.dp), contentAlignment = Alignment.BottomCenter) {
                Text(msg, color = DZ.text, modifier = Modifier.clip(RoundedCornerShape(50)).background(DZ.card2).border(1.dp, DZ.line, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 10.dp), fontSize = 13.sp)
            }
        }
        if (vm.importing) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DZ.text) }
    }
}

// ============================================================ floating pill navigation
@Composable
fun FloatingNav(tab: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val icons = mapOf(Tab.Home to Icons.Rounded.Home, Tab.Projects to Icons.Rounded.VideoLibrary, Tab.Assets to Icons.Rounded.PermMedia, Tab.Templates to Icons.Rounded.Dashboard, Tab.Profile to Icons.Rounded.Person)
    val haptic = rememberHaptic()
    val shape = RoundedCornerShape(34.dp)
    Row(modifier.shadow(18.dp, shape).clip(shape).background(DZ.panel).border(1.dp, DZ.line, shape).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        Tab.values().forEach { t ->
            val on = t == tab
            val bg by animateColorAsState(if (on) DZ.accent else Color.Transparent, Motion.tween(240), label = "nav")
            Row(Modifier.clip(RoundedCornerShape(28.dp)).background(bg).clickable { if (!on) { haptic(); onSelect(t) } }.padding(horizontal = if (on) 16.dp else 13.dp, vertical = 11.dp).animateContentSize(Motion.tween(240)), verticalAlignment = Alignment.CenterVertically) {
                Icon(icons.getValue(t), t.label, tint = if (on) DZ.onAccent else DZ.sub, modifier = Modifier.size(22.dp))
                if (on) { Spacer(Modifier.width(7.dp)); Text(t.label, color = DZ.onAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1) }
            }
        }
    }
}

// ============================================================ shared pieces
@Composable
private fun Wordmark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(DZ.accent), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Movie, null, tint = DZ.onAccent, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(10.dp))
        Text("Director Zone", color = DZ.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DZ.card).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Search, null, tint = DZ.sub, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
        BasicTextField(value, onChange, Modifier.weight(1f), singleLine = true, textStyle = TextStyle(color = DZ.text, fontSize = 15.sp), cursorBrush = SolidColor(DZ.accent),
            decorationBox = { if (value.isEmpty()) Text(hint, color = DZ.sub, fontSize = 15.sp); it() })
        if (value.isNotEmpty()) Icon(Icons.Rounded.Close, null, tint = DZ.sub, modifier = Modifier.size(18.dp).clickable { onChange("") })
    }
}

@Composable
private fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = DZ.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) Text(action, color = DZ.sub, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp))
    }
}

@Composable
private fun ProjectThumb(vm: EditorVM, p: Project, modifier: Modifier = Modifier) {
    val first = p.clips.firstOrNull()
    LaunchedEffect(p.id, first?.uri) { first?.let { vm.loadThumb(it) } }
    Box(modifier.background(Brush.linearGradient(listOf(DZ.card2, DZ.card)))) {
        val img = first?.let { vm.thumbs[it.uri] }
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Rounded.Movie, null, tint = DZ.sub.copy(alpha = 0.5f), modifier = Modifier.align(Alignment.Center).size(34.dp))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), startY = 120f)))
    }
}

@Composable
private fun ProjectCard(vm: EditorVM, p: Project, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var folder by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DZ.card).clickable { vm.openProject(p) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.35f)) {
            ProjectThumb(vm, p, Modifier.fillMaxSize())
            if (p.favorite) Icon(Icons.Rounded.Star, null, tint = DZ.gold, modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(20.dp))
            Text(fmtTime(p.durationMs).drop(3), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, color = DZ.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${ago(p.updated)} · ${p.ratio}${if (p.folder.isNotEmpty()) " · ${p.folder}" else ""}", color = DZ.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.MoreVert, null, tint = DZ.sub, modifier = Modifier.size(20.dp)) }
                DropdownMenu(menu, { menu = false }, containerColor = DZ.card2) {
                    DropdownMenuItem(text = { Text("Rename", color = DZ.text) }, leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = DZ.sub) }, onClick = { menu = false; rename = true })
                    DropdownMenuItem(text = { Text(if (p.favorite) "Remove favorite" else "Favorite", color = DZ.text) }, leadingIcon = { Icon(Icons.Rounded.Star, null, tint = DZ.sub) }, onClick = { menu = false; vm.toggleFavorite(p) })
                    DropdownMenuItem(text = { Text("Move to folder", color = DZ.text) }, leadingIcon = { Icon(Icons.Rounded.Folder, null, tint = DZ.sub) }, onClick = { menu = false; folder = true })
                    DropdownMenuItem(text = { Text("Duplicate", color = DZ.text) }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, tint = DZ.sub) }, onClick = { menu = false; vm.duplicateProject(p) })
                    DropdownMenuItem(text = { Text("Delete", color = DZ.danger) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = DZ.danger) }, onClick = { menu = false; confirmDelete = true })
                }
            }
        }
    }
    if (rename) RenameDialog("Rename project", p.name, { rename = false }) { vm.renameProject(p, it) }
    if (folder) RenameDialog("Folder name (empty = none)", p.folder, { folder = false }) { vm.moveToFolder(p, it) }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, containerColor = DZ.card, title = { Text("Delete “${p.name}”?", color = DZ.text) }, text = { Text("This can't be undone. Imported media stays in Assets.", color = DZ.sub) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteProject(p.id) }) { Text("Delete", color = DZ.danger) } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = DZ.sub) } })
}

@Composable
private fun ProjectGrid(vm: EditorVM, list: List<Project>) {
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        list.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { Box(Modifier.weight(1f)) { ProjectCard(vm, it) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ============================================================ Home
@Composable
private fun HomeTab(vm: EditorVM, pick: (Pending, TemplateDef?) -> Unit) {
    var searching by remember { mutableStateOf(false) }
    val q = vm.search.trim()
    var tcat by remember { mutableStateOf(TEMPLATE_CATS[0]) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Wordmark(); Spacer(Modifier.weight(1f))
                IconButton(onClick = { searching = !searching; if (!searching) vm.search = "" }) { Icon(Icons.Rounded.Search, null, tint = DZ.text) }
                IconButton(onClick = { vm.showSettings = true }) { Icon(Icons.Rounded.Settings, null, tint = DZ.text) }
            }
        }
        if (searching) item { SearchField(vm.search, { vm.search = it }, "Search projects and templates", Modifier.padding(horizontal = 20.dp)) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1.25f).clip(RoundedCornerShape(22.dp)).background(DZ.accent).clickable { vm.newProject() }.padding(18.dp).heightIn(min = 100.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Icon(Icons.Rounded.Add, null, tint = DZ.onAccent, modifier = Modifier.size(28.dp))
                    Column { Text("New Project", color = DZ.onAccent, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text("Start with an empty timeline", color = DZ.onAccent.copy(alpha = 0.7f), fontSize = 12.sp) }
                }
                Column(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(DZ.card).border(1.dp, DZ.line, RoundedCornerShape(22.dp)).clickable { pick(Pending.New, null) }.padding(18.dp).heightIn(min = 100.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Icon(Icons.Rounded.FileDownload, null, tint = DZ.text, modifier = Modifier.size(28.dp))
                    Column { Text("Import", color = DZ.text, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text("Photos & videos", color = DZ.sub, fontSize = 12.sp) }
                }
            }
        }
        if (q.isNotEmpty()) {
            val found = vm.recents.filter { it.name.contains(q, true) || it.folder.contains(q, true) }
            val tf = TEMPLATES.filter { it.name.contains(q, true) || it.category.contains(q, true) || it.blurb.contains(q, true) }
            item { SectionTitle("Projects (${found.size})") }
            item { if (found.isEmpty()) Text("No projects match.", color = DZ.sub, modifier = Modifier.padding(horizontal = 20.dp)) else ProjectGrid(vm, found) }
            item { SectionTitle("Templates (${tf.size})") }
            item { LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(tf) { t -> TemplateCard(t, Modifier.width(170.dp)) { pick(Pending.Template, t) } } } }
            return@LazyColumn
        }
        val last = vm.lastProject()
        if (last != null) {
            item { SectionTitle("Continue editing") }
            item {
                Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().aspectRatio(1.7f).clip(RoundedCornerShape(24.dp)).clickable { vm.openProject(last) }) {
                    ProjectThumb(vm, last, Modifier.fillMaxSize())
                    Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                        Text(last.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("${last.clips.size} clip${if (last.clips.size == 1) "" else "s"} · ${fmtTime(last.durationMs).drop(3)} · edited ${ago(last.updated)}", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                    Box(Modifier.align(Alignment.BottomEnd).padding(16.dp).size(46.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(28.dp)) }
                }
            }
        }
        item { SectionTitle("Templates", "See all") { vm.tab = Tab.Templates } }
        item { Box(Modifier.padding(horizontal = 20.dp)) { Chips(TEMPLATE_CATS, tcat) { tcat = it } } }
        item {
            Column {
                Spacer(Modifier.height(12.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(TEMPLATES.filter { it.category == tcat }) { t -> TemplateCard(t, Modifier.width(170.dp)) { pick(Pending.Template, t) } }
                }
            }
        }
        item {
            val favs = vm.recents.count { it.favorite }
            val folders = vm.recents.map { it.folder }.filter { it.isNotEmpty() }.distinct()
            if (favs > 0 || folders.isNotEmpty()) Column {
                SectionTitle("Favorites & folders")
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (favs > 0) item { QuickTile(Icons.Rounded.Star, "Favorites", "$favs") { vm.projectFilter = "Favorites"; vm.tab = Tab.Projects } }
                    items(folders) { f -> QuickTile(Icons.Rounded.Folder, f, "${vm.recents.count { it.folder == f }}") { vm.projectFilter = f; vm.tab = Tab.Projects } }
                }
            }
        }
        if (vm.recents.isNotEmpty()) {
            item { SectionTitle("Recent projects", "All projects") { vm.projectFilter = "All"; vm.tab = Tab.Projects } }
            item { ProjectGrid(vm, vm.recents.take(6)) }
        } else item {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 24.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(DZ.card).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.MovieCreation, null, tint = DZ.sub, modifier = Modifier.size(34.dp))
                Text("No projects yet", color = DZ.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
                Text("Start with New Project, or pick a template above and drop in your clips.", color = DZ.sub, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun QuickTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, count: String, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(16.dp)).background(DZ.card).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = DZ.text, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp))
        Column { Text(title, color = DZ.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1); Text("$count project${if (count == "1") "" else "s"}", color = DZ.sub, fontSize = 11.sp) }
    }
}

// ============================================================ Projects
@Composable
private fun ProjectsTab(vm: EditorVM) {
    val folders = vm.recents.map { it.folder }.filter { it.isNotEmpty() }.distinct()
    val filters = listOf("All", "Favorites") + folders
    if (vm.projectFilter !in filters) vm.projectFilter = "All"
    var q by remember { mutableStateOf("") }
    val list = vm.recents.filter { p ->
        (vm.projectFilter == "All" || (vm.projectFilter == "Favorites" && p.favorite) || p.folder == vm.projectFilter) && (q.isBlank() || p.name.contains(q, true))
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item { Text("Projects", color = DZ.text, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) }
        item { SearchField(q, { q = it }, "Search projects", Modifier.padding(horizontal = 20.dp)) }
        item { Column { Spacer(Modifier.height(12.dp)); Box(Modifier.padding(horizontal = 20.dp)) { Chips(filters, vm.projectFilter) { vm.projectFilter = it } }; Spacer(Modifier.height(14.dp)) } }
        if (list.isEmpty()) item { Text(if (vm.recents.isEmpty()) "Your projects will appear here." else "Nothing here yet.", color = DZ.sub, modifier = Modifier.padding(horizontal = 20.dp, vertical = 30.dp)) }
        else item { ProjectGrid(vm, list) }
    }
}

// ============================================================ Assets
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AssetsTab(vm: EditorVM) {
    val st = vm.storage
    val media = vm.assets.filter { !it.isAudio }
    val audio = vm.assets.filter { it.isAudio }
    var del by remember { mutableStateOf<MediaFile?>(null) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item { Text("Assets", color = DZ.text, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) }
        item {
            Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DZ.card).padding(16.dp)) {
                Text("${mb(st.media)} of imported media", color = DZ.text, fontWeight = FontWeight.SemiBold)
                Text("${mb(st.free)} free on this device", color = DZ.sub, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(DZ.card2)) { Box(Modifier.fillMaxWidth((st.media.toFloat() / (st.media + st.free).coerceAtLeast(1)).coerceIn(0.02f, 1f)).fillMaxHeight().background(DZ.accent)) }
                Spacer(Modifier.height(12.dp))
                val unused = vm.assets.count { it.usedIn == 0 }
                Pill(if (unused == 0) "No unused media" else "Remove $unused unused file${if (unused == 1) "" else "s"}", false) { vm.removeUnusedMedia() }
            }
        }
        item { SectionTitle("Photos & videos (${media.size})") }
        item {
            if (media.isEmpty()) Text("Everything you import into a project is kept here so projects always open.", color = DZ.sub, modifier = Modifier.padding(horizontal = 20.dp), fontSize = 13.sp)
            else Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                media.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { f ->
                            LaunchedEffect(f.uri) { vm.loadThumbUri(f.uri, f.isImage) }
                            Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(DZ.card).combinedClickable(onClick = { if (vm.inEditor) vm.addExistingMedia(f) }, onLongClick = { del = f })) {
                                vm.thumbs[f.uri]?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                Text(if (f.usedIn == 0) "unused" else "in ${f.usedIn}", color = Color.White, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 5.dp, vertical = 1.dp))
                                Text(mb(f.bytes), color = Color.White, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 5.dp, vertical = 1.dp))
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Text("Long-press a file to delete it.", color = DZ.sub, fontSize = 11.sp)
            }
        }
        item { SectionTitle("Audio & voiceovers (${audio.size})") }
        if (audio.isEmpty()) item { Text("Recordings and imported audio appear here.", color = DZ.sub, modifier = Modifier.padding(horizontal = 20.dp), fontSize = 13.sp) }
        items(audio) { f ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(DZ.card).combinedClickable(onClick = {}, onLongClick = { del = f }).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.GraphicEq, null, tint = DZ.accent2); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(f.file.name, color = DZ.text, fontSize = 14.sp, maxLines = 1); Text("${mb(f.bytes)} · ${if (f.usedIn == 0) "unused" else "used in ${f.usedIn}"}", color = DZ.sub, fontSize = 11.sp) }
            }
        }
    }
    del?.let { f ->
        AlertDialog(onDismissRequest = { del = null }, containerColor = DZ.card, title = { Text("Delete this file?", color = DZ.text) },
            text = { Text(if (f.usedIn > 0) "It's used in ${f.usedIn} project(s) — those projects will lose this media." else "It isn't used by any project.", color = DZ.sub) },
            confirmButton = { TextButton(onClick = { del = null; vm.deleteMedia(f) }) { Text("Delete", color = DZ.danger) } }, dismissButton = { TextButton(onClick = { del = null }) { Text("Cancel", color = DZ.sub) } })
    }
}

// ============================================================ Templates
@Composable
private fun TemplatesTab(vm: EditorVM, pick: (Pending, TemplateDef?) -> Unit) {
    var cat by remember { mutableStateOf(TEMPLATE_CATS[0]) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item { Text("Templates", color = DZ.text, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) }
        item { Column { Box(Modifier.padding(horizontal = 20.dp)) { Chips(TEMPLATE_CATS, cat) { cat = it } }; Spacer(Modifier.height(16.dp)) } }
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TEMPLATES.filter { it.category == cat }.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { t -> Box(Modifier.weight(1f)) { TemplateCard(t) { pick(Pending.Template, t) } } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Text("Pick a template, choose your clips, and it builds the cut: transitions, titles, look, music and sound effects — all editable afterwards.", color = DZ.sub, fontSize = 12.sp)
            }
        }
    }
}

// ============================================================ Profile
@Composable
private fun ProfileTab(vm: EditorVM, pick: (Pending, TemplateDef?) -> Unit) {
    val ctx = LocalContext.current
    var editName by remember { mutableStateOf(false) }
    val name = Prefs.str("name", "Creator")
    val ver = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" } }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(DZ.accent), contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), color = DZ.onAccent, fontSize = 36.sp, fontWeight = FontWeight.Bold) }
                Row(Modifier.padding(top = 12.dp).clickable { editName = true }, verticalAlignment = Alignment.CenterVertically) { Text(name, color = DZ.text, fontSize = 22.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.Edit, null, tint = DZ.sub, modifier = Modifier.size(16.dp)) }
                Text("Stored on this device only", color = DZ.sub, fontSize = 12.sp)
            }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val exports = vm.recents.sumOf { it.exports }
                listOf("Projects" to "${vm.recents.size}", "Exports" to "$exports", "Storage" to mb(vm.storage.media + vm.storage.projects)).forEach { (k, v) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(DZ.card).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(v, color = DZ.text, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(k, color = DZ.sub, fontSize = 12.sp)
                    }
                }
            }
        }
        item { Column {
            Spacer(Modifier.height(18.dp))
            Column(Modifier.padding(horizontal = 20.dp).clip(RoundedCornerShape(18.dp)).background(DZ.card)) {
                SettingRow("Settings", onClick = { vm.showSettings = true })
                HorizontalDivider(color = DZ.line)
                SettingRow("Themes", value = paletteById(Prefs.theme).name, onClick = { vm.showThemes = true })
                HorizontalDivider(color = DZ.line)
                SettingRow("Phone Studio project", subtitle = "Pick clips and start with the 3D phone on", onClick = { pick(Pending.Phone, null) })
                HorizontalDivider(color = DZ.line)
                SettingRow("Clear cache", value = mb(vm.storage.cache), trailing = { Icon(Icons.Rounded.DeleteSweep, null, tint = DZ.sub) }, onClick = { vm.clearCache() })
            }
            Text("Director Zone ${ver}", color = DZ.sub, fontSize = 12.sp, modifier = Modifier.padding(20.dp))
        } }
    }
    if (editName) RenameDialog("Your name", name, { editName = false }) { Prefs.put("name", it.trim().ifEmpty { "Creator" }) }
}
