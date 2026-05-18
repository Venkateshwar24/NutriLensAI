package com.nutrilens.nutrilensai

internal object Constants {
    const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"
    const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"
    const val HEALTH_REPORT_ASSET_NAME = "sample_health_report.txt"
    const val HEALTH_REPORT_RAW_TEXT_LIMIT = 6000
    const val HEALTH_REPORT_SUMMARY_LIMIT = 500
    val HEALTH_REPORT_MIME_TYPES = arrayOf("application/pdf", "text/plain")
}
