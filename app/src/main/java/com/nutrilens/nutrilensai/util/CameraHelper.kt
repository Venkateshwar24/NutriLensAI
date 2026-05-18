package com.nutrilens.nutrilensai.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.nutrilens.nutrilensai.Constants
import java.io.File

internal object CameraHelper {
    fun createPhotoUri(context: Context): Pair<Uri, File> {
        val imageFile = File(
            context.externalCacheDir ?: context.cacheDir,
            "scan_${System.currentTimeMillis()}.jpg"
        )
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}${Constants.FILE_PROVIDER_AUTHORITY_SUFFIX}",
            imageFile
        )
        return Pair(uri, imageFile)
    }
}
