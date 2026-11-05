package com.example.aivideoeditor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Non-destructive project model. Source media is never modified; edits are
 * represented as references and time ranges in this project document.
 */
enum class TrackType { VIDEO, AUDIO, TEXT, OVERLAY }

data class EditorClip(
    val id: String,
    val sourceUri: String,
    val sourceInMs: Long,
    val sourceOutMs: Long,
    val timelineStartMs: Long,
    val trackId: String,
    val type: TrackType = TrackType.VIDEO,
    val title: String = "",
    val volume: Float = 1f
) {
    val durationMs: Long get() = (sourceOutMs - sourceInMs).coerceAtLeast(0L)
    val timelineEndMs: Long get() = timelineStartMs + durationMs
}

data class EditorTrack(
    val id: String,
    val name: String,
    val type: TrackType,
    val muted: Boolean = false,
    val locked: Boolean = false,
    val clips: List<EditorClip> = emptyList()
)

data class EditorProject(
    val id: String,
    val name: String,
    val width: Int = 1080,
    val height: Int = 1920,
    val frameRate: Int = 30,
    val tracks: List<EditorTrack> = emptyList()
) {
    val durationMs: Long
        get() = tracks.flatMap { it.clips }.maxOfOrNull { it.timelineEndMs } ?: 0L

    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (width <= 0 || height <= 0) errors += "Project dimensions must be positive"
        if (frameRate !in 1..120) errors += "Frame rate must be between 1 and 120"
        val trackIds = tracks.map { it.id }
        if (trackIds.size != trackIds.toSet().size) errors += "Track IDs must be unique"
        val clipIds = tracks.flatMap { it.clips }.map { it.id }
        if (clipIds.size != clipIds.toSet().size) errors += "Clip IDs must be unique"
        tracks.forEach { track ->
            track.clips.forEach { clip ->
                if (clip.trackId != track.id) errors += "Clip ${clip.id} belongs to the wrong track"
                if (clip.sourceOutMs <= clip.sourceInMs) errors += "Clip ${clip.id} has an invalid source range"
                if (clip.timelineStartMs < 0) errors += "Clip ${clip.id} has a negative timeline position"
            }
        }
        return errors
    }
}

/** Pure editing operations. These modify project metadata, never source files. */
object TimelineEngine {
    fun split(project: EditorProject, clipId: String, timelinePositionMs: Long): EditorProject {
        val track = project.tracks.firstOrNull { t -> t.clips.any { it.id == clipId } } ?: return project
        val clip = track.clips.first { it.id == clipId }
        val offset = timelinePositionMs - clip.timelineStartMs
        if (offset <= 0L || offset >= clip.durationMs) return project
        val sourceCut = clip.sourceInMs + offset
        val left = clip.copy(sourceOutMs = sourceCut)
        val right = clip.copy(
            id = "${clip.id}_split_${sourceCut}",
            sourceInMs = sourceCut,
            timelineStartMs = timelinePositionMs
        )
        return project.replaceTrack(track.copy(clips = track.clips.flatMap {
            if (it.id == clipId) listOf(left, right) else listOf(it)
        }))
    }

    fun trimStart(project: EditorProject, clipId: String, timelinePositionMs: Long): EditorProject {
        val track = project.tracks.firstOrNull { t -> t.clips.any { it.id == clipId } } ?: return project
        val clip = track.clips.first { it.id == clipId }
        val delta = timelinePositionMs - clip.timelineStartMs
        if (delta <= 0L || delta >= clip.durationMs) return project
        return project.replaceTrack(track.copy(clips = track.clips.map {
            if (it.id == clipId) it.copy(
                sourceInMs = it.sourceInMs + delta,
                timelineStartMs = timelinePositionMs
            ) else it
        }))
    }

    fun trimEnd(project: EditorProject, clipId: String, timelinePositionMs: Long): EditorProject {
        val track = project.tracks.firstOrNull { t -> t.clips.any { it.id == clipId } } ?: return project
        val clip = track.clips.first { it.id == clipId }
        val newDuration = timelinePositionMs - clip.timelineStartMs
        if (newDuration <= 0L || newDuration >= clip.durationMs) return project
        return project.replaceTrack(track.copy(clips = track.clips.map {
            if (it.id == clipId) it.copy(sourceOutMs = it.sourceInMs + newDuration) else it
        }))
    }

    fun delete(project: EditorProject, clipId: String): EditorProject =
        project.copy(tracks = project.tracks.map { track ->
            track.copy(clips = track.clips.filterNot { it.id == clipId })
        })

    fun move(project: EditorProject, clipId: String, newTimelineStartMs: Long): EditorProject {
        if (newTimelineStartMs < 0L) return project
        return project.copy(tracks = project.tracks.map { track ->
            track.copy(clips = track.clips.map {
                if (it.id == clipId) it.copy(timelineStartMs = newTimelineStartMs) else it
            })
        })
    }

    /** Reorders primary-track clips and closes gaps to create a continuous sequence. */
    fun reorder(project: EditorProject, clipId: String, targetIndex: Int): EditorProject {
        val track = project.tracks.firstOrNull { it.clips.any { clip -> clip.id == clipId } } ?: return project
        val ordered = track.clips.sortedBy { it.timelineStartMs }.toMutableList()
        val fromIndex = ordered.indexOfFirst { it.id == clipId }
        if (fromIndex < 0 || targetIndex !in ordered.indices) return project
        val clip = ordered.removeAt(fromIndex)
        ordered.add(targetIndex.coerceIn(0, ordered.size), clip)
        var cursor = 0L
        val positioned = ordered.map { item ->
            item.copy(timelineStartMs = cursor).also { cursor += it.durationMs }
        }
        return project.replaceTrack(track.copy(clips = positioned))
    }

    private fun EditorProject.replaceTrack(updated: EditorTrack): EditorProject =
        copy(tracks = tracks.map { if (it.id == updated.id) updated else it })
}

/** JSON project persistence in app-private storage. */
class ProjectStore(private val context: Context) {
    fun save(project: EditorProject): String {
        val errors = project.validate()
        require(errors.isEmpty()) { errors.joinToString("; ") }
        val root = JSONObject()
            .put("schemaVersion", 1)
            .put("id", project.id)
            .put("name", project.name)
            .put("width", project.width)
            .put("height", project.height)
            .put("frameRate", project.frameRate)
        val tracksJson = JSONArray()
        project.tracks.forEach { track ->
            val trackJson = JSONObject()
                .put("id", track.id)
                .put("name", track.name)
                .put("type", track.type.name)
                .put("muted", track.muted)
                .put("locked", track.locked)
            val clipsJson = JSONArray()
            track.clips.forEach { clip ->
                clipsJson.put(JSONObject()
                    .put("id", clip.id)
                    .put("sourceUri", clip.sourceUri)
                    .put("sourceInMs", clip.sourceInMs)
                    .put("sourceOutMs", clip.sourceOutMs)
                    .put("timelineStartMs", clip.timelineStartMs)
                    .put("trackId", clip.trackId)
                    .put("type", clip.type.name)
                    .put("title", clip.title)
                    .put("volume", clip.volume.toDouble()))
            }
            trackJson.put("clips", clipsJson)
            tracksJson.put(trackJson)
        }
        root.put("tracks", tracksJson)
        val file = java.io.File(context.filesDir, "projects/${project.id}.json")
        file.parentFile?.mkdirs()
        file.writeText(root.toString(2))
        return file.absolutePath
    }

    fun load(projectId: String): EditorProject? {
        val file = java.io.File(context.filesDir, "projects/$projectId.json")
        if (!file.exists()) return null
        val root = JSONObject(file.readText())
        val tracks = mutableListOf<EditorTrack>()
        val trackArray = root.optJSONArray("tracks") ?: JSONArray()
        for (i in 0 until trackArray.length()) {
            val t = trackArray.getJSONObject(i)
            val type = TrackType.valueOf(t.getString("type"))
            val clips = mutableListOf<EditorClip>()
            val clipArray = t.optJSONArray("clips") ?: JSONArray()
            for (j in 0 until clipArray.length()) {
                val c = clipArray.getJSONObject(j)
                clips += EditorClip(
                    id = c.getString("id"),
                    sourceUri = c.getString("sourceUri"),
                    sourceInMs = c.getLong("sourceInMs"),
                    sourceOutMs = c.getLong("sourceOutMs"),
                    timelineStartMs = c.getLong("timelineStartMs"),
                    trackId = c.getString("trackId"),
                    type = TrackType.valueOf(c.optString("type", type.name)),
                    title = c.optString("title", ""),
                    volume = c.optDouble("volume", 1.0).toFloat()
                )
            }
            tracks += EditorTrack(
                id = t.getString("id"),
                name = t.getString("name"),
                type = type,
                muted = t.optBoolean("muted", false),
                locked = t.optBoolean("locked", false),
                clips = clips
            )
        }
        return EditorProject(
            id = root.getString("id"),
            name = root.getString("name"),
            width = root.optInt("width", 1080),
            height = root.optInt("height", 1920),
            frameRate = root.optInt("frameRate", 30),
            tracks = tracks
        )
    }
}
