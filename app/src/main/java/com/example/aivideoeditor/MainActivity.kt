package com.example.aivideoeditor

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

private val Accent = Color(0xFF8B72FF)
private val Panel = Color(0xFF171B24)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EditorHome() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorHome() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedVideo by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            selectedVideo = uri
        }
    }
    val player = remember(selectedVideo) {
        ExoPlayer.Builder(context).build().apply {
            selectedVideo?.let { setMediaItem(MediaItem.fromUri(it)) }
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent,
        background = Color(0xFF0B0D12),
        surface = Panel
    )) {
        Scaffold(containerColor = Color(0xFF0B0D12)) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Accent)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("AI VIDEO EDITOR", color = Color.LightGray, fontSize = 11.sp)
                        Text("Your creative studio", style = MaterialTheme.typography.headlineSmall)
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Start a new edit", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Import footage and preview it. Multi-track editing and AI tools are planned for upcoming releases.",
                            color = Color.LightGray
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { picker.launch(arrayOf("video/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Import video")
                        }
                    }
                }
                if (selectedVideo != null) {
                    Text("VIDEO PREVIEW", color = Color.LightGray, fontSize = 11.sp)
                    AndroidView(
                        factory = { PlayerView(it).apply { this.player = player } },
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                } else {
                    Box(
                        Modifier.fillMaxWidth().weight(1f)
                            .background(Panel, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Your projects will appear here", color = Color.LightGray)
                    }
                }
            }
        }
    }
}
