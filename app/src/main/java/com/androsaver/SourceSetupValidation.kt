package com.androsaver

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object SourceSetupValidation {
    fun host(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty() || value.contains("/") || value.contains("://") || value.any { it.isWhitespace() }) return null
        return "https://$value".toHttpUrlOrNull()?.host?.let { value }
    }

    fun required(vararg values: String): Boolean = values.all { it.trim().isNotEmpty() }
}
