package com.example.aivideoeditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TimelineEngineTest {
    private fun sampleProject(): EditorProject {
        val track = EditorTrack(
            id = "video-main",
            name = "Main video",
            type = TrackType.VIDEO,
            clips = listOf(
                EditorClip(
                    id = "clip-1",
                    sourceUri = "content://media/video/1",
                    sourceInMs = 1000L,
                    sourceOutMs = 11000L,
                    timelineStartMs = 0L,
                    trackId = "video-main"
                )
            )
        )
        return EditorProject(id = "project-1", name = "Test project", tracks = listOf(track))
    }

    @Test fun splitCreatesTwoSourceRangesWithoutChangingTotalDuration() {
        val result = TimelineEngine.split(sampleProject(), "clip-1", 4000L)
        val clips = result.tracks.first().clips
        assertEquals(2, clips.size)
        assertEquals(1000L, clips[0].sourceInMs)
        assertEquals(5000L, clips[0].sourceOutMs)
        assertEquals(5000L, clips[1].sourceInMs)
        assertEquals(11000L, clips[1].sourceOutMs)
        assertEquals(10000L, result.durationMs)
        assertNotEquals(clips[0].id, clips[1].id)
    }

    @Test fun trimStartMovesTimelineStartAndSourceInTogether() {
        val result = TimelineEngine.trimStart(sampleProject(), "clip-1", 2500L)
        val clip = result.tracks.first().clips.single()
        assertEquals(3500L, clip.sourceInMs)
        assertEquals(2500L, clip.timelineStartMs)
        assertEquals(8500L, clip.durationMs)
    }

    @Test fun trimEndShortensSourceOut() {
        val result = TimelineEngine.trimEnd(sampleProject(), "clip-1", 6000L)
        val clip = result.tracks.first().clips.single()
        assertEquals(7000L, clip.sourceOutMs)
        assertEquals(6000L, clip.durationMs)
    }

    @Test fun deleteRemovesOnlySelectedClip() {
        val project = sampleProject()
        val withSecond = project.copy(tracks = project.tracks.map { track ->
            track.copy(clips = track.clips + track.clips.first().copy(id = "clip-2", timelineStartMs = 10000L))
        })
        val result = TimelineEngine.delete(withSecond, "clip-1")
        assertEquals(listOf("clip-2"), result.tracks.first().clips.map { it.id })
    }

    @Test fun invalidSplitDoesNotChangeProject() {
        val project = sampleProject()
        assertEquals(project, TimelineEngine.split(project, "clip-1", 0L))
        assertEquals(project, TimelineEngine.split(project, "clip-1", 10000L))
    }

    @Test fun projectValidationAcceptsConsistentTrack() {
        assertEquals(emptyList<String>(), sampleProject().validate())
    }
}
