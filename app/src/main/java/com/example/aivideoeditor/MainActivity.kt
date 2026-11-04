package com.example.aivideoeditor

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

private val AppBg = Color(0xFF08090D)
private val SurfaceDark = Color(0xFF17191F)
private val SurfaceRaised = Color(0xFF22252D)
private val Accent = Color(0xFF42D6C5)
private val Muted = Color(0xFF9297A3)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CapCutStyleEditor() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CapCutStyleEditor() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var selectedTool by remember { mutableStateOf("Edit") }
    var showInfo by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            videoUri = uri
            isPlaying = false
        }
    }
    val player = remember { ExoPlayer.Builder(context).build() }
    LaunchedEffect(videoUri) {
        player.stop()
        player.clearMediaItems()
        videoUri?.let {
            player.setMediaItem(MediaItem.fromUri(it))
            player.prepare()
            player.playWhenReady = false
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent, secondary = Accent, background = AppBg, surface = SurfaceDark
    )) {
        Scaffold(
            containerColor = AppBg,
            topBar = {
                Row(
                    Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                    Text("New project", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Button(
                        onClick = { showInfo = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        contentPadding = PaddingValues(horizontal = 17.dp, vertical = 7.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Export", color = Color(0xFF07110F), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.FileUpload, null, tint = Color(0xFF07110F), modifier = Modifier.size(17.dp))
                    }
                }
            },
            bottomBar = { EditorToolBar(selectedTool) { selectedTool = it } }
        ) { insets ->
            Column(
                Modifier.fillMaxSize().padding(insets),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Preview canvas
                Box(
                    Modifier.fillMaxWidth().weight(1f).padding(horizontal = 18.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp)).background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (videoUri != null) {
                        AndroidView(
                            factory = { viewContext ->
                                PlayerView(viewContext).apply {
                                    this.player = player
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                                }
                            },
                            update = { it.player = player },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Movie, null, tint = Color(0xFF555B66), modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Your video preview", color = Color(0xFFB9BDC6), fontSize = 15.sp)
                            Spacer(Modifier.height(5.dp))
                            Text("Add a clip to start editing", color = Muted, fontSize = 12.sp)
                            Spacer(Modifier.height(16.dp))
                            FilledTonalButton(
                                onClick = { picker.launch(arrayOf("video/*")) },
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Accent)
                            ) {
                                Icon(Icons.Default.Add, null, tint = Color(0xFF07110F))
                                Spacer(Modifier.width(6.dp))
                                Text("Add video", color = Color(0xFF07110F), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (videoUri != null) {
                        Surface(
                            Modifier.align(Alignment.BottomEnd).padding(10.dp),
                            color = Color(0x99000000), shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("PREVIEW", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, fontSize = 9.sp)
                        }
                    }
                }

                // Playback row
                Row(
                    Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("00:00", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("  /  00:00", color = Muted, fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { player.seekTo(0); isPlaying = false; player.pause() }) {
                            Icon(Icons.Default.SkipPrevious, "Beginning", tint = Color.White)
                        }
                        FilledIconButton(
                            onClick = {
                                if (player.isPlaying) { player.pause(); isPlaying = false }
                                else if (videoUri != null) { player.play(); isPlaying = true }
                                else picker.launch(arrayOf("video/*"))
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play", tint = Color.Black)
                        }
                        IconButton(onClick = { showInfo = true }) {
                            Icon(Icons.Default.Undo, "Undo", tint = Color.White)
                        }
                        IconButton(onClick = { showInfo = true }) {
                            Icon(Icons.Default.Redo, "Redo", tint = Muted)
                        }
                    }
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Default.AspectRatio, "Canvas ratio", tint = Color.White)
                    }
                }

                // Timeline workspace
                Column(
                    Modifier.fillMaxWidth().height(174.dp).background(Color(0xFF101217)).padding(top = 8.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Timeline", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("00:00", color = Muted, fontSize = 10.sp)
                            Spacer(Modifier.width(10.dp))
                            Icon(Icons.Default.ZoomOut, null, tint = Muted, modifier = Modifier.size(17.dp))
                            Icon(Icons.Default.ZoomIn, null, tint = Muted, modifier = Modifier.size(17.dp))
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, end = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("00:00", "00:05", "00:10", "00:15", "00:20").forEach {
                            Text(it, color = Muted, fontSize = 9.sp)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().weight(1f).padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Video", color = Muted, fontSize = 10.sp, modifier = Modifier.width(42.dp))
                        if (videoUri == null) {
                            Box(
                                Modifier.weight(1f).height(48.dp).border(1.dp, Color(0xFF363A44), RoundedCornerShape(6.dp))
                                    .clip(RoundedCornerShape(6.dp)).background(SurfaceDark),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("＋  Add a video clip", color = Muted, fontSize = 11.sp)
                            }
                        } else {
                            Row(
                                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF254A49)).border(1.dp, Accent, RoundedCornerShape(6.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(8) { index ->
                                    Box(Modifier.weight(1f).fillMaxHeight().padding(2.dp).background(
                                        if (index % 2 == 0) Color(0xFF477B76) else Color(0xFF35645F),
                                        RoundedCornerShape(3.dp)
                                    ))
                                }
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(start = 56.dp, end = 14.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("＋", color = Accent, fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("Add audio", color = Muted, fontSize = 10.sp)
                        Spacer(Modifier.width(22.dp))
                        Text("＋", color = Accent, fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("Add text", color = Muted, fontSize = 10.sp)
                    }
                }

                // Contextual tool options
                ToolOptions(selectedTool) {
                    when (it) {
                        "Import" -> picker.launch(arrayOf("video/*"))
                        else -> showInfo = true
                    }
                }
            }
        }
        if (showInfo) {
            AlertDialog(
                onDismissRequest = { showInfo = false },
                title = { Text("Coming in the next build") },
                text = { Text("The CapCut-style editor layout is in place. Editing actions, export, effects and AI tools are being added in the next development steps.") },
                confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Got it") } },
                containerColor = SurfaceRaised,
                titleContentColor = Color.White,
                textContentColor = Color.LightGray
            )
        }
    }
}

@Composable
private fun EditorToolBar(selected: String, onSelect: (String) -> Unit) {
    val tools = listOf(
        "Edit" to Icons.Default.ContentCut,
        "Audio" to Icons.Default.MusicNote,
        "Text" to Icons.Default.TextFields,
        "Captions" to Icons.Default.Subtitles,
        "Overlay" to Icons.Default.Layers,
        "Effects" to Icons.Default.AutoAwesome,
        "Filters" to Icons.Default.Tune,
        "Import" to Icons.Default.Add
    )
    Surface(color = Color(0xFF15171D), shadowElevation = 10.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().horizontalScroll(rememberScrollState())
                .padding(horizontal = 7.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            tools.forEach { (label, icon) ->
                val active = selected == label
                Column(
                    Modifier.width(66.dp).clip(RoundedCornerShape(9.dp))
                        .background(if (active) Color(0xFF293B3A) else Color.Transparent)
                        .padding(vertical = 7.dp).clickableNoRipple { onSelect(label) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(icon, null, tint = if (active) Accent else Color(0xFFD1D4DA), modifier = Modifier.size(21.dp))
                    Spacer(Modifier.height(5.dp))
                    Text(label, color = if (active) Accent else Color(0xFFD1D4DA), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun ToolOptions(tool: String, onAction: (String) -> Unit) {
    val options = when (tool) {
        "Edit" -> listOf("Split", "Trim", "Speed", "Volume", "Delete")
        "Audio" -> listOf("Sounds", "Extract", "Record", "Fade")
        "Text" -> listOf("Add text", "Text template", "Stickers")
        "Captions" -> listOf("Auto captions", "Manual captions", "Caption style")
        "Overlay" -> listOf("Add overlay", "PIP", "Mask")
        "Effects" -> listOf("Video effects", "Body effects", "Transitions")
        "Filters" -> listOf("Adjust", "Filters", "Color")
        else -> listOf("From device", "Recent videos")
    }
    Row(
        Modifier.fillMaxWidth().height(74.dp).background(AppBg).horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            Column(
                Modifier.width(66.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceDark)
                    .clickableNoRipple { onAction(option) }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(option.take(1), color = Accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(option, color = Color.LightGray, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickableCompat(onClick))

private fun Modifier.clickableCompat(onClick: () -> Unit): Modifier =
    androidx.compose.foundation.clickable(onClick = onClick)
