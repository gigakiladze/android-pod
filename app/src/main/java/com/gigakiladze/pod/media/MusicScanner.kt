package com.gigakiladze.pod.media

import android.media.MediaMetadataRetriever
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Walks shared storage looking for .mp3 files, rather than querying MediaStore.
 * Slower than the media index, but it finds files the system has not scanned yet —
 * which is the normal case for music sideloaded over USB or adb push.
 */
object MusicScanner {

    private const val TAG = "MusicScanner"

    /** Directories that never contain user music and are expensive or forbidden to walk. */
    private val SKIPPED_DIR_NAMES = setOf(
        "Android", ".thumbnails", "cache", ".cache", "LOST.DIR", ".trashed",
    )

    private const val MAX_DEPTH = 12

    /**
     * @param onProgress invoked on the caller's dispatcher with the running file count,
     *                   so the UI can show "Scanning… 132" instead of freezing.
     */
    suspend fun scan(onProgress: (Int) -> Unit = {}): List<Track> = withContext(Dispatchers.IO) {
        val mp3Files = mutableListOf<File>()
        for (root in storageRoots()) {
            coroutineContext.ensureActive()
            collectMp3s(root, mp3Files, depth = 0, onProgress = onProgress)
        }

        val retriever = MediaMetadataRetriever()
        val tracks = mutableListOf<Track>()
        try {
            for ((index, file) in mp3Files.withIndex()) {
                coroutineContext.ensureActive()
                tracks += readTags(retriever, file, id = index.toLong())
            }
        } finally {
            runCatching { retriever.release() }
        }

        // Artist, then album, then title — the order the iPod's "All Songs" implies.
        tracks.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER, Track::artist)
                .thenBy(String.CASE_INSENSITIVE_ORDER, Track::album)
                .thenBy(String.CASE_INSENSITIVE_ORDER, Track::title)
        )
    }

    /**
     * Primary shared storage plus any SD card. Secondary volumes are derived from
     * getExternalFilesDirs by trimming the app-private "/Android/data/..." suffix.
     */
    private fun storageRoots(): List<File> {
        val roots = linkedSetOf<File>()
        Environment.getExternalStorageDirectory()?.let { roots += it }
        return roots.filter { it.isDirectory && it.canRead() }
    }

    private suspend fun collectMp3s(
        dir: File,
        into: MutableList<File>,
        depth: Int,
        onProgress: (Int) -> Unit,
    ) {
        if (depth > MAX_DEPTH) return
        coroutineContext.ensureActive()

        val children = try {
            dir.listFiles()
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot read ${dir.path}", e)
            null
        } ?: return

        for (child in children) {
            coroutineContext.ensureActive()
            when {
                child.isDirectory -> {
                    val name = child.name
                    if (name in SKIPPED_DIR_NAMES || name.startsWith(".")) continue
                    collectMp3s(child, into, depth + 1, onProgress)
                }

                child.isFile && child.name.endsWith(".mp3", ignoreCase = true) && child.length() > 0 -> {
                    into += child
                    onProgress(into.size)
                }
            }
        }
    }

    /** Leading track numbers: "01 ", "01. ", "01 - ", "1) ". */
    private val TRACK_NUMBER_PREFIX = Regex("""^\s*\d{1,3}\s*[-._)]?\s+""")

    /** The near-universal "Artist - Title" filename convention. */
    private val ARTIST_TITLE = Regex("""^(.{1,120}?)\s+-\s+(.+)$""")

    /**
     * Most sideloaded music carries no ID3 tags at all, which would otherwise leave
     * the whole library reading "Unknown Artist". The filename almost always
     * encodes the same information, so fall back to parsing it.
     */
    private fun guessFromFileName(file: File): Pair<String?, String> {
        val base = file.nameWithoutExtension
            .replace('_', ' ')
            .replace(TRACK_NUMBER_PREFIX, "")
            .trim()

        val match = ARTIST_TITLE.find(base) ?: return null to base
        val artist = match.groupValues[1].trim()
        val title = match.groupValues[2].trim()
        return if (artist.isEmpty() || title.isEmpty()) null to base else artist to title
    }

    private fun readTags(retriever: MediaMetadataRetriever, file: File, id: Long): Track {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var duration = 0L

        try {
            retriever.setDataSource(file.absolutePath)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            // A malformed or DRM'd file should cost us one track, not the whole scan.
            Log.w(TAG, "Unreadable tags for ${file.name}", e)
        }

        val (guessedArtist, guessedTitle) = guessFromFileName(file)

        return Track(
            id = id,
            file = file,
            title = title?.trim()?.takeIf { it.isNotEmpty() } ?: guessedTitle,
            artist = artist?.trim()?.takeIf { it.isNotEmpty() }
                ?: guessedArtist
                ?: "Unknown Artist",
            // Left blank rather than "Unknown Album": the filename cannot tell us,
            // and a screenful of "Unknown Album" is just noise.
            album = album?.trim()?.takeIf { it.isNotEmpty() } ?: "",
            durationMs = duration,
        )
    }
}
