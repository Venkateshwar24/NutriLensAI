package com.nutrilens.nutrilensai.util

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DocumentReader {

    class UnsupportedFormatException(mimeType: String?) :
        Exception("Unsupported format: $mimeType. Please upload a PDF or TXT file.")

    suspend fun extractText(uri: Uri, context: Context): String = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri)
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: error("Cannot open URI: $uri")

        inputStream.use { stream ->
            when {
                mimeType == "application/pdf"
                    || uri.toString().endsWith(".pdf", ignoreCase = true) -> {
                    PDFBoxResourceLoader.init(context)
                    PDDocument.load(stream).use { doc -> PDFTextStripper().getText(doc) }
                }
                mimeType?.startsWith("text/") == true
                    || uri.toString().endsWith(".txt", ignoreCase = true) -> {
                    stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
                else -> throw UnsupportedFormatException(mimeType)
            }
        }
    }
}
