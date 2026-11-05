package com.example.aivideoeditor

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Locale

private val Bg = Color(0xFF08090D)
private val Panel = Color(0xFF17191F)
private val Accent = Color(0xFF42D6C5)
private val Muted = Color(0xFF9297A3)
private const val PROJECT_ID = "active-project"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EditorScreen() }
    }
}

@Composable
private fun EditorScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember(context) { ProjectStore(context) }
    fun emptyProject() = EditorProject(
        id = PROJECT_ID, name = "New project",
        tracks = listOf(
            EditorTrack("video", "Video", TrackType.VIDEO),
            EditorTrack("audio", "Audio", TrackType.AUDIO),
            EditorTrack("text", "Text", TrackType.TEXT),
            EditorTrack("overlay", "Overlay", TrackType.OVERLAY)
        )
    )
    var project by remember { mutableStateOf(store.load(PROJECT_ID) ?: emptyProject()) }
    var undoStack by remember { mutableStateOf(emptyList<EditorProject>()) }
    var redoStack by remember { mutableStateOf(emptyList<EditorProject>()) }
    fun commitProject(updated: EditorProject) {
        if (updated != project) {
            undoStack = (undoStack + project).takeLast(50)
            redoStack = emptyList()
            project = updated
        }
    }
    val restoredClip = project.tracks.firstOrNull { it.type == TrackType.VIDEO }?.clips?.firstOrNull()
    var source by remember { mutableStateOf(restoredClip?.sourceUri?.let { Uri.parse(it) }) }
    var duration by remember { mutableLongStateOf(project.durationMs) }
    var position by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf(restoredClip?.id ?: "") }
    var tool by remember { mutableStateOf("Edit") }
    var status by remember { mutableStateOf(if (restoredClip == null) "Import a video to begin" else "Project restored") }
    var dialog by remember { mutableStateOf("") }
    var serial by remember { mutableIntStateOf(project.tracks.sumOf { it.clips.size } + 1) }

    val videoTrack = project.tracks.firstOrNull { it.type == TrackType.VIDEO }
    val videoClips = videoTrack?.clips.orEmpty()
    val selected = videoClips.firstOrNull { it.id == selectedId }
    val player = remember { ExoPlayer.Builder(context).build() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            source = uri
            status = "Loading video metadata…"
        }
    }

    LaunchedEffect(source) {
        val uri = source ?: return@LaunchedEffect
        status = "Reading video…"
        val result = withContext(Dispatchers.IO) {
            runCatching { withTimeout(20_000L) { readVideoDurationMs(context, uri) } }
        }
        val length = result.getOrNull() ?: 0L
        if (length > 0L) {
            duration = length
            val alreadyAdded = project.tracks.flatMap { it.clips }.any { it.sourceUri == uri.toString() }
            if (!alreadyAdded) {
                val id = "clip_$serial"
                val updated = project.tracks.map { track ->
                    if (track.type == TrackType.VIDEO) track.copy(clips = track.clips + EditorClip(
                        id = id, sourceUri = uri.toString(), sourceInMs = 0L,
                        sourceOutMs = length, timelineStartMs = project.durationMs,
                        trackId = track.id, type = TrackType.VIDEO, title = "Video $serial"
                    )) else track
                }
                commitProject(project.copy(tracks = updated))
                selectedId = id
                serial++
            }
            status = "Video imported · ${timeLabel(length)}"
        } else {
            source = null
            status = when (result.exceptionOrNull()) {
                is kotlinx.coroutines.TimeoutCancellationException -> "Video import timed out. Try a locally stored MP4."
                null -> "Unsupported or unreadable video. Try another MP4."
                else -> "Import failed: ${result.exceptionOrNull()?.javaClass?.simpleName}"
            }
        }
    }

    LaunchedEffect(videoClips) {
        val ordered = videoClips.sortedBy { it.timelineStartMs }
        duration = ordered.sumOf { it.durationMs }
        if (ordered.isEmpty()) {
            player.stop()
            player.clearMediaItems()
            position = 0L
        } else {
            val items = ordered.map { clip ->
                MediaItem.Builder()
                    .setUri(Uri.parse(clip.sourceUri))
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.sourceInMs)
                            .setEndPositionMs(clip.sourceOutMs)
                            .build()
                    )
                    .build()
            }
            val index = ordered.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
            player.setMediaItems(items, index, 0L)
            player.prepare()
            player.playWhenReady = false
            position = ordered.take(index).sumOf { it.durationMs }
        }
    }

    fun seekTimeline(targetMs: Long) {
        val ordered = videoClips.sortedBy { it.timelineStartMs }
        if (ordered.isEmpty()) return
        var remaining = targetMs.coerceIn(0L, ordered.sumOf { it.durationMs })
        var index = ordered.lastIndex
        for (i in ordered.indices) {
            if (remaining < ordered[i].durationMs || i == ordered.lastIndex) {
                index = i
                break
            }
            remaining -= ordered[i].durationMs
        }
        selectedId = ordered[index].id
        player.seekTo(index, remaining.coerceIn(0L, ordered[index].durationMs))
    }

    LaunchedEffect(project) {
        withContext(Dispatchers.IO) { runCatching { store.save(project) } }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player) {
        while (true) {
            val ordered = videoClips.sortedBy { it.timelineStartMs }
            val index = player.currentMediaItemIndex
            position = if (index in ordered.indices) {
                ordered.take(index).sumOf { it.durationMs } + player.currentPosition.coerceAtLeast(0L)
            } else 0L
            playing = player.isPlaying
            delay(120)
        }
    }

    fun applyEdit(updated: EditorProject, success: String, failure: String) {
        if (updated == project) status = failure else { commitProject(updated); status = success }
    }
    fun split() {
        val clip = selected ?: run { status = "Select a video clip first"; return }
        val before = project
        val timelineCut = clip.timelineStartMs + player.currentPosition.coerceIn(0L, clip.durationMs)
        val after = TimelineEngine.split(before, clip.id, timelineCut)
        applyEdit(after, "Split at ${timeLabel(player.currentPosition)}", "Move playhead inside the selected clip to split")
        after.tracks.flatMap { it.clips }.firstOrNull { it.id.startsWith(clip.id + "_split_") }?.let { selectedId = it.id }
    }
    fun trimStart() {
        val clip = selected ?: run { status = "Select a clip first"; return }
        val timelinePosition = clip.timelineStartMs + player.currentPosition.coerceIn(0L, clip.durationMs)
        applyEdit(TimelineEngine.trimStart(project, clip.id, timelinePosition), "Clip start trimmed", "Place playhead inside the selected clip")
    }
    fun trimEnd() {
        val clip = selected ?: run { status = "Select a clip first"; return }
        val timelinePosition = clip.timelineStartMs + player.currentPosition.coerceIn(0L, clip.durationMs)
        applyEdit(TimelineEngine.trimEnd(project, clip.id, timelinePosition), "Clip end trimmed", "Place playhead inside the selected clip")
    }
    fun deleteClip() {
        val clip = selected ?: run { status = "Select a clip first"; return }
        commitProject(TimelineEngine.delete(project, clip.id))
        val remaining = project.tracks.firstOrNull { it.type == TrackType.VIDEO }?.clips.orEmpty()
        selectedId = remaining.sortedBy { it.timelineStartMs }.firstOrNull()?.id.orEmpty()
        if (remaining.isEmpty()) { source = null; player.stop(); duration = 0L }
        else { source = Uri.parse(remaining.first().sourceUri) }
        status = "Clip deleted"
    }
    fun reorderSelected(direction: Int) {
        val ordered = videoClips.sortedBy { it.timelineStartMs }
        val index = ordered.indexOfFirst { it.id == selectedId }
        val target = index + direction
        if (index < 0 || target !in ordered.indices) {
            status = if (direction < 0) "Clip is already first" else "Clip is already last"
            return
        }
        commitProject(TimelineEngine.reorder(project, selectedId, target))
        status = "Clip order updated"
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel)) {
        Scaffold(containerColor = Bg, topBar = {
            Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { dialog = "Projects are automatically saved on this device." }) {
                    Icon(Icons.Default.ArrowBack, "Project", tint = Color.White)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(project.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("AUTO-SAVED PROJECT", color = Muted, fontSize = 9.sp)
                }
                Button(onClick = { dialog = "Project metadata is saved. Video rendering and MP4 export are the next milestone." },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)) {
                    Text("Export", color = Color(0xFF07110F), fontWeight = FontWeight.Bold)
                }
            }
        }, bottomBar = { ToolBar(tool) { tool = it } }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(8.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                    if (source != null) {
                        AndroidView(factory = { PlayerView(it).apply {
                            this.player = player
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        } }, update = { it.player = player }, modifier = Modifier.fillMaxSize())
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Movie, null, tint = Color.DarkGray, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("Your video preview", color = Color.LightGray)
                            Text("Import footage to start editing", color = Muted, fontSize = 12.sp)
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = { picker.launch(arrayOf("video/*")) },
                                colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                                Icon(Icons.Default.Add, null, tint = Color.Black)
                                Text(" Import video", color = Color.Black)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(timeLabel(position), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("/ ${timeLabel(duration)}", color = Muted, fontSize = 9.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { seekTimeline(0L); player.pause() }) { Icon(Icons.Default.SkipPrevious, "Restart", tint = Color.White) }
                        FilledIconButton(onClick = {
                            if (player.isPlaying) player.pause() else if (videoClips.isNotEmpty()) player.play() else picker.launch(arrayOf("video/*"))
                        }, modifier = Modifier.size(38.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White)) {
                            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play", tint = Color.Black)
                        }
                        IconButton(onClick = {
                            if (undoStack.isNotEmpty()) {
                                val previous = undoStack.last()
                                redoStack = (redoStack + project).takeLast(50)
                                undoStack = undoStack.dropLast(1)
                                project = previous
                                val clip = previous.tracks.firstOrNull { it.type == TrackType.VIDEO }?.clips?.minByOrNull { it.timelineStartMs }
                                selectedId = clip?.id.orEmpty()
                                source = clip?.sourceUri?.let { Uri.parse(it) }
                                status = "Undo applied"
                            }
                        }, enabled = undoStack.isNotEmpty()) { Icon(Icons.Default.Undo, "Undo", tint = if (undoStack.isNotEmpty()) Color.White else Muted) }
                        IconButton(onClick = {
                            if (redoStack.isNotEmpty()) {
                                val next = redoStack.last()
                                undoStack = (undoStack + project).takeLast(50)
                                redoStack = redoStack.dropLast(1)
                                project = next
                                val clip = next.tracks.firstOrNull { it.type == TrackType.VIDEO }?.clips?.minByOrNull { it.timelineStartMs }
                                selectedId = clip?.id.orEmpty()
                                source = clip?.sourceUri?.let { Uri.parse(it) }
                                status = "Redo applied"
                            }
                        }, enabled = redoStack.isNotEmpty()) { Icon(Icons.Default.Redo, "Redo", tint = if (redoStack.isNotEmpty()) Color.White else Muted) }
                    }
                    Text("Original", color = Muted, fontSize = 10.sp)
                }
                if (duration > 0L) {
                    Slider(value = position.coerceIn(0L, duration).toFloat(), onValueChange = { seekTimeline(it.toLong()) },
                        valueRange = 0f..duration.toFloat(), modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(24.dp),
                        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent))
                }
                Column(Modifier.fillMaxWidth().height(250.dp).background(Color(0xFF101217)).padding(top = 8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Timeline", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("${project.tracks.sumOf { it.clips.size }} clips", color = Muted, fontSize = 10.sp)
                    }
                    TrackRow("Video", videoClips.sortedBy { it.timelineStartMs }, selectedId, { clip ->
                        selectedId = clip.id
                        if (source?.toString() != clip.sourceUri) source = Uri.parse(clip.sourceUri)
                        val index = videoClips.sortedBy { it.timelineStartMs }.indexOfFirst { it.id == clip.id }
                        player.pause()
                        if (index >= 0) player.seekTo(index, 0L)
                    }) { picker.launch(arrayOf("video/*")) }
                    TrackRow("Audio", project.tracks.firstOrNull { it.type == TrackType.AUDIO }?.clips.orEmpty(), "", {}) {
                        dialog = "Audio track is in the project model. Audio import and mixing are the next phase."
                    }
                    TrackRow("Text", project.tracks.firstOrNull { it.type == TrackType.TEXT }?.clips.orEmpty(), "", {}) {
                        dialog = "Text overlays are the next phase."
                    }
                    TrackRow("Overlay", project.tracks.firstOrNull { it.type == TrackType.OVERLAY }?.clips.orEmpty(), "", {}) {
                        dialog = "Overlay editing is planned."
                    }
                    Text(status, color = Muted, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp))
                }
                ToolActions(tool) { action ->
                    when (action) {
                        "Import" -> picker.launch(arrayOf("video/*"))
                        "Split" -> split()
                        "Trim start" -> trimStart()
                        "Trim end" -> trimEnd()
                        "Delete" -> deleteClip()
                        "Move left" -> reorderSelected(-1)
                        "Move right" -> reorderSelected(1)
                        "Go to clip start" -> selected?.let { seekTimeline(videoClips.sortedBy { c -> c.timelineStartMs }.takeWhile { c -> c.id != it.id }.sumOf { c -> c.durationMs }) }
                        else -> dialog = "$action is planned for the next feature phase."
                    }
                }
            }
        }
        if (dialog.isNotEmpty()) AlertDialog(onDismissRequest = { dialog = "" },
            title = { Text("AI Video Editor") }, text = { Text(dialog) },
            confirmButton = { TextButton(onClick = { dialog = "" }) { Text("OK") } },
            containerColor = Color(0xFF22252D), titleContentColor = Color.White, textContentColor = Color.LightGray)
    }
}

@Composable
private fun TrackRow(label: String, clips: List<EditorClip>, selectedId: String, onSelect: (EditorClip) -> Unit, onEmpty: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp).height(43.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 9.sp, modifier = Modifier.width(48.dp))
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (clips.isEmpty()) {
                Box(Modifier.width(180.dp).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(Panel)
                    .border(1.dp, Color(0xFF343842), RoundedCornerShape(5.dp)).clickable { onEmpty() },
                    contentAlignment = Alignment.Center) { Text("+ Add $label", color = Muted, fontSize = 9.sp) }
            } else clips.forEach { clip ->
                val active = clip.id == selectedId
                Box(Modifier.width((clip.durationMs / 250L).coerceIn(76L, 190L).toFloat().dp).fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp)).background(if (active) Color(0xFF254A49) else Color(0xFF30343D))
                    .border(if (active) 2.dp else 1.dp, if (active) Accent else Color(0xFF444A55), RoundedCornerShape(5.dp))
                    .clickable { onSelect(clip) }.padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
                    Text(clip.title.ifBlank { label }, color = if (active) Accent else Color.White, fontSize = 9.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ToolBar(selected: String, onSelect: (String) -> Unit) {
    val items = listOf("Edit" to Icons.Default.ContentCut, "Audio" to Icons.Default.MusicNote,
        "Text" to Icons.Default.TextFields, "Captions" to Icons.Default.Subtitles,
        "Overlay" to Icons.Default.Layers, "Effects" to Icons.Default.AutoAwesome,
        "Filters" to Icons.Default.Tune, "Import" to Icons.Default.Add)
    Surface(color = Color(0xFF15171D), shadowElevation = 10.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().horizontalScroll(rememberScrollState()).padding(horizontal = 7.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            items.forEach { (label, icon) ->
                val active = label == selected
                Column(Modifier.width(66.dp).clip(RoundedCornerShape(9.dp)).background(if (active) Color(0xFF293B3A) else Color.Transparent)
                    .clickable { onSelect(label) }.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, null, tint = if (active) Accent else Color.LightGray, modifier = Modifier.size(21.dp))
                    Spacer(Modifier.height(5.dp))
                    Text(label, color = if (active) Accent else Color.LightGray, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun ToolActions(tool: String, onAction: (String) -> Unit) {
    val actions = when (tool) {
        "Edit" -> listOf("Split", "Trim start", "Trim end", "Move left", "Move right", "Go to clip start", "Delete")
        "Audio" -> listOf("Import audio", "Extract audio", "Record", "Fade")
        "Text" -> listOf("Add text", "Text template", "Stickers")
        "Captions" -> listOf("Auto captions", "Manual captions", "Caption style")
        "Overlay" -> listOf("Add overlay", "Picture in picture", "Mask")
        "Effects" -> listOf("Video effects", "Transitions", "Keyframes")
        "Filters" -> listOf("Adjust", "Filters", "Color")
        else -> listOf("Import")
    }
    Row(Modifier.fillMaxWidth().height(74.dp).background(Bg).horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        actions.forEach { action ->
            Column(Modifier.width(70.dp).clip(RoundedCornerShape(8.dp)).background(Panel).clickable { onAction(action) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(action.take(1), color = Accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(action, color = Color.LightGray, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

private fun readVideoDurationMs(context: android.content.Context, uri: Uri): Long {
    val retriever = MediaMetadataRetriever()
    try {
        val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Cannot open selected video")
        descriptor.use { asset ->
            if (asset.length >= 0L) retriever.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
            else retriever.setDataSource(asset.fileDescriptor)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (duration > 0L) return duration
        }
    } catch (_: Exception) {
        // Fall back for document providers that do not expose a seekable descriptor.
    } finally {
        retriever.release()
    }
    val fallback = MediaMetadataRetriever()
    return try {
        fallback.setDataSource(context, uri)
        fallback.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } finally {
        fallback.release()
    }
}

private fun timeLabel(ms: Long): String {
    val sec = ms.coerceAtLeast(0L) / 1000L
    return String.format(Locale.US, "%02d:%02d", sec / 60L, sec % 60L)
}
