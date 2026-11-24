package io.kunalmno.zefer.ar

import android.content.Context
import android.graphics.Bitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Downloads (or reads from Coil's cache) the 10 nail images of a set and trims each one to its
 * visible pixels, so every nail has its own tight size. Index = nail number - 1
 * (0..4 left pinky -> left thumb, 5..9 right thumb -> right pinky). Failed ones are null.
 */
suspend fun loadNailBitmaps(context: Context, urls: List<String>): List<Bitmap?> = coroutineScope {
    val loader = SingletonImageLoader.get(context)
    urls.map { url ->
        async(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
                val result = loader.execute(request) as? SuccessResult ?: return@runCatching null
                trimToOpaque(result.image.toBitmap())
            }.getOrNull()
        }
    }.awaitAll()
}

private fun trimToOpaque(src: Bitmap): Bitmap {
    val w = src.width
    val h = src.height
    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)
    var minX = w; var minY = h; var maxX = -1; var maxY = -1
    for (y in 0 until h) {
        for (x in 0 until w) {
            if ((pixels[y * w + x] ushr 24) > 20) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
    }
    if (maxX < 0) return src
    return Bitmap.createBitmap(src, minX, minY, maxX - minX + 1, maxY - minY + 1)
}
