package com.example.aivideoeditor

import android.net.Uri
import android.media.MediaMetadataRetriever
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
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val Bg = Color(0xFF08090D)
private val Panel = Color(0xFF17191F)
private val Accent = Color(0xFF42D6C5)
private val Muted = Color(0xFF9297A3)

private data class Segment(val id: Int, val start: Long, val end: Long)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EditorScreen() }
    }
}

@Composable
private fun EditorScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var source by remember { mutableStateOf<Uri?>(null) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var tool by remember { mutableStateOf("Edit") }
    var segments by remember { mutableStateOf(listOf<Segment>()) }
    var selectedId by remember { mutableIntStateOf(1) }
    var nextId by remember { mutableIntStateOf(2) }
    var status by remember { mutableStateOf("Import a video to begin") }
    var dialog by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            source = uri
            duration = 0L
            position = 0L
            segments = emptyList()
            selectedId = 1
            nextId = 2
            status = "Loading video…"
        }
    }
    val player = remember { ExoPlayer.Builder(context).build() }

    LaunchedEffect(source) {
        player.stop()
        player.clearMediaItems()
        duration = 0L
        position = 0L
        source?.let { uri ->
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = false

            // Read duration independently of ExoPlayer. Some phone videos expose
            // their first frame before the player reports a usable duration.
            val metadataDuration = withContext(Dispatchers.IO) {
                runCatching {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    } finally {
                        retriever.release()
                    }
                }.getOrDefault(0L)
            }
            if (metadataDuration > 0L) {
                duration = metadataDuration
                if (segments.isEmpty()) {
                    segments = listOf(Segment(1, 0L, metadataDuration))
                    selectedId = 1
                    status = "Video loaded · " + timeLabel(metadataDuration)
                }
            } else {
                status = "Video imported · reading duration from player…"
            }
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    LaunchedEffect(player) {
        while (true) {
            val length = player.duration
            if (length > 0 && length != C.TIME_UNSET && length != duration) {
                duration = length
                if (segments.isEmpty()) {
                    segments = listOf(Segment(1, 0L, length))
                    status = "Video loaded · " + timeLabel(length)
                }
            }
            position = player.currentPosition.coerceAtLeast(0L)
            playing = player.isPlaying
            delay(150)
        }
    }

    val selected = segments.firstOrNull { it.id == selectedId }

    fun splitClip() {
        val clip = segments.firstOrNull { it.id == selectedId }
        val cut = player.currentPosition
        if (clip == null) status = "Import a video first"
        else if (cut <= clip.start + 100L || cut >= clip.end - 100L) {
            status = "Move playhead inside the selected clip to split"
        } else {
            val left = clip.copy(end = cut)
            val right = Segment(nextId, cut, clip.end)
            segments = segments.flatMap { if (it.id == clip.id) listOf(left, right) else listOf(it) }
            selectedId = nextId
            nextId++
            status = "Split at " + timeLabel(cut)
        }
    }

    fun trimStart() {
        val clip = segments.firstOrNull { it.id == selectedId }
        val at = player.currentPosition
        if (clip != null && at > clip.start && at < clip.end - 100L) {
            segments = segments.map { if (it.id == clip.id) it.copy(start = at) else it }
            status = "Start trimmed to " + timeLabel(at)
        } else status = "Place playhead inside the selected clip"
    }

    fun trimEnd() {
        val clip = segments.firstOrNull { it.id == selectedId }
        val at = player.currentPosition
        if (clip != null && at > clip.start + 100L && at < clip.end) {
            segments = segments.map { if (it.id == clip.id) it.copy(end = at) else it }
            status = "End trimmed to " + timeLabel(at)
        } else status = "Place playhead inside the selected clip"
    }

    fun deleteClip() {
        if (segments.isEmpty()) return
        if (segments.size == 1) {
            segments = emptyList()
            source = null
            duration = 0L
            player.stop()
        } else {
            val rest = segments.filterNot { it.id == selectedId }
            segments = rest
            selectedId = rest.first().id
            player.seekTo(rest.first().start)
        }
        status = "Selected clip segment deleted"
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel)) {
        Scaffold(
            containerColor = Bg,
            topBar = {
                Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { dialog = "Project settings are planned for a later update." }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("New project", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("AI VIDEO EDITOR", color = Muted, fontSize = 9.sp)
                    }
                    Button(onClick = { dialog = "Export is the next rendering milestone. Your current timeline edits are not yet rendered to a new video." },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)) {
                        Text("Export", color = Color(0xFF07110F), fontWeight = FontWeight.Bold)
                    }
                }
            },
            bottomBar = { ToolBar(tool) { tool = it } }
        ) { padding ->
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

                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(44.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(timeLabel(position), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("/ " + timeLabel(duration), color = Muted, fontSize = 9.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { player.seekTo(0); player.pause() }) {
                            Icon(Icons.Default.SkipPrevious, "Restart", tint = Color.White)
                        }
                        FilledIconButton(onClick = {
                            if (player.isPlaying) player.pause()
                            else if (source != null) {
                                val clip = segments.firstOrNull { it.id == selectedId }
                                if (clip != null && player.currentPosition >= clip.end) player.seekTo(clip.start)
                                player.play()
                            } else picker.launch(arrayOf("video/*"))
                        }, modifier = Modifier.size(38.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White)) {
                            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play", tint = Color.Black)
                        }
                        IconButton(onClick = { dialog = "Undo history will be added after the edit command model is in place." }) {
                            Icon(Icons.Default.Undo, "Undo", tint = Color.White)
                        }
                    }
                    Text("Original", color = Muted, fontSize = 10.sp)
                }

                if (duration > 0L) {
                    Slider(value = position.coerceIn(0L, duration).toFloat(),
                        onValueChange = { player.seekTo(it.toLong()) },
                        valueRange = 0f..duration.toFloat(),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(24.dp),
                        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent))
                }

                Column(Modifier.fillMaxWidth().height(194.dp).background(Color(0xFF101217)).padding(top = 8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Timeline", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("${segments.size} segment(s)", color = Muted, fontSize = 10.sp)
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (segments.isEmpty()) {
                            Box(Modifier.width(250.dp).height(55.dp).clip(RoundedCornerShape(6.dp))
                                .background(Panel).border(1.dp, Color.DarkGray, RoundedCornerShape(6.dp))
                                .clickable { picker.launch(arrayOf("video/*")) }, contentAlignment = Alignment.Center) {
                                Text("+ Import a video clip", color = Muted, fontSize = 11.sp)
                            }
                        } else segments.forEachIndexed { index, clip ->
                            val active = clip.id == selectedId
                            Column(Modifier.width(116.dp).height(55.dp).clip(RoundedCornerShape(6.dp))
                                .background(if (active) Color(0xFF254A49) else Panel)
                                .border(if (active) 2.dp else 1.dp, if (active) Accent else Color.DarkGray, RoundedCornerShape(6.dp))
                                .clickable { selectedId = clip.id; player.pause(); player.seekTo(clip.start) }
                                .padding(7.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Text("CLIP ${index + 1}", color = if (active) Accent else Color.LightGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text("${timeLabel(clip.start)} – ${timeLabel(clip.end)}", color = Color.White, fontSize = 9.sp)
                                Text(timeLabel(clip.end - clip.start), color = Muted, fontSize = 8.sp)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Audio track", color = Muted, fontSize = 10.sp)
                        Text("+ Add audio", color = Accent, fontSize = 10.sp, modifier = Modifier.clickable { dialog = "Audio import and mixing are coming in the audio editing phase." })
                    }
                    Spacer(Modifier.height(5.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Text track", color = Muted, fontSize = 10.sp)
                        Text("+ Add text", color = Accent, fontSize = 10.sp, modifier = Modifier.clickable { dialog = "Text overlays and title animation are coming in the text editing phase." })
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(status, color = Muted, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 14.dp))
                }

                ToolActions(tool) { action ->
                    when (action) {
                        "Import" -> picker.launch(arrayOf("video/*"))
                        "Split" -> splitClip()
                        "Trim start" -> trimStart()
                        "Trim end" -> trimEnd()
                        "Delete" -> deleteClip()
                        "Go to clip start" -> selected?.let { player.seekTo(it.start) }
                        else -> dialog = "$action is planned for the next feature phase."
                    }
                }
            }
        }
        if (dialog.isNotEmpty()) {
            AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("AI Video Editor") },
                text = { Text(dialog) }, confirmButton = {
                    TextButton(onClick = { dialog = "" }) { Text("OK") }
                }, containerColor = Color(0xFF22252D), titleContentColor = Color.White, textContentColor = Color.LightGray)
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
        Row(Modifier.fillMaxWidth().navigationBarsPadding().horizontalScroll(rememberScrollState())
            .padding(horizontal = 7.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            items.forEach { (label, icon) ->
                val active = label == selected
                Column(Modifier.width(66.dp).clip(RoundedCornerShape(9.dp))
                    .background(if (active) Color(0xFF293B3A) else Color.Transparent)
                    .clickable { onSelect(label) }.padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
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
        "Edit" -> listOf("Split", "Trim start", "Trim end", "Go to clip start", "Delete")
        "Audio" -> listOf("Import audio", "Extract audio", "Record", "Fade")
        "Text" -> listOf("Add text", "Text template", "Stickers")
        "Captions" -> listOf("Auto captions", "Manual captions", "Caption style")
        "Overlay" -> listOf("Add overlay", "Picture in picture", "Mask")
        "Effects" -> listOf("Video effects", "Transitions", "Keyframes")
        "Filters" -> listOf("Adjust", "Filters", "Color")
        else -> listOf("Import")
    }
    Row(Modifier.fillMaxWidth().height(74.dp).background(Bg).horizontalScroll(rememberScrollState())
        .padding(horizontal = 10.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        actions.forEach { action ->
            Column(Modifier.width(70.dp).clip(RoundedCornerShape(8.dp)).background(Panel)
                .clickable { onAction(action) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(action.take(1), color = Accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(action, color = Color.LightGray, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

private fun timeLabel(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000L
    return String.format(Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L)
}
