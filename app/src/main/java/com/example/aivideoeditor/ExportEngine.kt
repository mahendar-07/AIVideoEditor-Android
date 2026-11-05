package com.example.aivideoeditor

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File

/**
 * Exports the current non-destructive timeline to an MP4 file.
 *
 * Video clips are concatenated in timeline order. Audio clips are exported as
 * separate audio sequences and mixed by Media3 when their sequences overlap.
 * The result is written to the app's Movies folder; no source media is changed.
 */
@UnstableApi
class ExportEngine(context: Context) {
    private val appContext = context.applicationContext
    private var transformer: Transformer? = null

    fun export(
        project: EditorProject,
        onCompleted: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        cancel()
        val videoClips = project.tracks
            .filter { it.type == TrackType.VIDEO && !it.muted }
            .flatMap { it.clips }
            .sortedBy { it.timelineStartMs }

        require(videoClips.isNotEmpty()) { "Add at least one video clip before exporting." }
        require(project.validate().isEmpty()) { project.validate().joinToString("; ") }

        val videoItems = videoClips.map { clip ->
            EditedMediaItem.Builder(clippedMediaItem(clip)).build()
        }
        val sequences = mutableListOf(
            EditedMediaItemSequence.withAudioAndVideoFrom(videoItems)
        )

        project.tracks
            .filter { it.type == TrackType.AUDIO && !it.muted }
            .forEach { track ->
                val audioItems = track.clips.sortedBy { it.timelineStartMs }.map { clip ->
                    EditedMediaItem.Builder(clippedMediaItem(clip)).build()
                }
                if (audioItems.isNotEmpty()) {
                    sequences += EditedMediaItemSequence.withAudioFrom(audioItems)
                }
            }

        val composition = Composition.Builder(sequences).build()
        val outputDirectory = appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: appContext.filesDir
        if (!outputDirectory.exists()) outputDirectory.mkdirs()
        val outputFile = File(outputDirectory, "AIVideoEditor_${System.currentTimeMillis()}.mp4")

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                transformer = null
                onCompleted(outputFile)
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                transformer = null
                outputFile.delete()
                onError(exportException.message ?: "MP4 export failed.")
            }
        }

        try {
            transformer = Transformer.Builder(appContext)
                .addListener(listener)
                .build()
            transformer?.start(composition, outputFile.absolutePath)
        } catch (error: Exception) {
            transformer = null
            outputFile.delete()
            onError(error.message ?: "Unable to start MP4 export.")
        }
    }

    fun cancel() {
        transformer?.cancel()
        transformer = null
    }

    private fun clippedMediaItem(clip: EditorClip): MediaItem =
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
