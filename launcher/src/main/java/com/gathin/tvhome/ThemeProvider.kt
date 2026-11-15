package com.gathin.tvhome

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

class ThemeProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException("No context")
        val path = uri.path?.trimStart('/') ?: throw FileNotFoundException("Empty path")
        if (path.contains("..")) throw SecurityException("Invalid path")

        val target = when {
            path == "config.json" -> ConfigStore.configFile(ctx)
            path == "wallpaper.jpg" -> ConfigStore.wallpaperFile(ctx)
            path.startsWith("wallpapers/") -> File(ConfigStore.wallpapersDir(ctx), path.removePrefix("wallpapers/"))
            path.startsWith("link_tiles/") -> File(ConfigStore.linkTilesDir(ctx), path.removePrefix("link_tiles/"))
            else -> File(ctx.filesDir, path)
        }
        target.parentFile?.mkdirs()

        val writing = mode.contains("w")
        val pfdMode = if (writing) {
            ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_WRITE_ONLY
        } else {
            if (!target.exists()) throw FileNotFoundException(path)
            ParcelFileDescriptor.MODE_READ_ONLY
        }
        val pfd = ParcelFileDescriptor.open(target, pfdMode)
        if (writing && path == "config.json") {
            ctx.sendBroadcast(Intent(ConfigStore.ACTION_RELOAD_CONFIG).setPackage(ctx.packageName))
        }
        return pfd
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, sa: Array<out String>?, so: String?): Cursor? = null
    override fun getType(uri: Uri): String = "application/octet-stream"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val ctx = context ?: return 0
        val path = uri.path?.trimStart('/') ?: return 0
        if (path.contains("..")) return 0
        val target = File(ctx.filesDir, path)
        return if (target.exists() && target.delete()) 1 else 0
    }
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

