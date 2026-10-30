package com.omniplayer

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

/** Convert a user-picked shared-storage directory to the filesystem path required by libtorrent. */
internal object DirectoryPicker {
    fun hasFilesystemAccess(): Boolean = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

    fun resolve(context: Context, uri: Uri): File? = runCatching {
        require(uri.authority == "com.android.externalstorage.documents")
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        val volume = documentId.substringBefore(':')
        val relative = documentId.substringAfter(':', "")
        require(volume.isNotBlank() && !relative.split('/').any { it == ".." })
        val base = if (volume.equals("primary", true)) File("/storage/emulated/0") else File("/storage", volume)
        File(base, relative).canonicalFile
    }.getOrNull()
}
