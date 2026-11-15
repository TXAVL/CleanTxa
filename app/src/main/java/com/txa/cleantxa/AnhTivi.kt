package com.txa.cleantxa

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AnhTivi {
    val luuDuoc: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private fun ten() = "CleanTxa-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".png"

    fun luu(ctx: Context, anh: Bitmap): Boolean {
        if (!luuDuoc) return false
        val v = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, ten())
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CleanTxa")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val cr = ctx.contentResolver
        val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return false
        return try {
            cr.openOutputStream(uri)?.use { anh.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: error("cannot open")
            cr.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            true
        } catch (_: Exception) {
            cr.delete(uri, null, null)
            false
        }
    }

    fun chiaSe(ctx: Context, anh: Bitmap) {
        val dir = File(ctx.cacheDir, "anh").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, ten())
        f.outputStream().use { anh.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val gui = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(
            Intent.createChooser(gui, t("Chia sẻ màn hình tivi", "Share TV screen"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

