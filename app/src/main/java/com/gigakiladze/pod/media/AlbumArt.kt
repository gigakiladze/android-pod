package com.gigakiladze.pod.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Embedded cover art, decoded on demand and kept in a small memory cache.
 * Decoding every cover up front would make a large library unusably slow to open.
 */
object AlbumArt {

    /** Covers are only ever drawn small, so decode them small. */
    private const val TARGET_PX = 512

    private val cache = object : LruCache<String, ImageBitmap>(24) {}

    /** Sentinel so we remember "this track has no art" and stop re-decoding it. */
    private val missing = mutableSetOf<String>()

    suspend fun of(track: Track): ImageBitmap? {
        val key = track.file.absolutePath
        cache.get(key)?.let { return it }
        synchronized(missing) { if (key in missing) return null }

        val bitmap = withContext(Dispatchers.IO) { decode(track) }
        if (bitmap == null) {
            synchronized(missing) { missing += key }
            return null
        }
        val image = bitmap.asImageBitmap()
        cache.put(key, image)
        return image
    }

    private fun decode(track: Track): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(track.file.absolutePath)
            val bytes = retriever.embeddedPicture ?: return null

            // Two-pass decode: measure first, then subsample to roughly TARGET_PX.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= TARGET_PX) {
            longest /= 2
            sample *= 2
        }
        return sample
    }
}
