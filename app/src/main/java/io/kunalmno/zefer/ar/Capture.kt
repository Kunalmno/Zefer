package io.kunalmno.zefer.ar

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.view.PreviewView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The camera picture exactly as shown on screen (mirrored for the front camera) with the nails drawn on top. */
fun captureComposite(previewView: PreviewView, smoother: NailSmoother, nails: List<Bitmap?>): Bitmap? {
    val background = previewView.bitmap ?: return null
    val out = background.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(out)
    if (previewView.width > 0 && previewView.height > 0) {
        canvas.scale(out.width / previewView.width.toFloat(), out.height / previewView.height.toFloat())
    }
    NailPainter().draw(canvas, smoother, nails)
    return out
}

/** Saves to Pictures/Zefer (no storage permission needed on Android 10+). Returns the picture's Uri, or null. */
suspend fun saveToGallery(context: Context, bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Zefer_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Zefer")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching null
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        uri
    }.getOrNull()
}

fun shareImage(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "My nails with Zefer")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share your nails"))
}
