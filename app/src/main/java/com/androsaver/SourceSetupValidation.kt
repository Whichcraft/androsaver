package com.androsaver

import okhttp3.HttpUrl

object SourceSetupValidation {
    fun host(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty() || value.any { it.isWhitespace() || it in "/\\@?#" }) return null
        if (value.count { it == ':' } == 1) return null // port belongs in its own field
        val candidate = if (value.startsWith("[") && value.endsWith("]")) value.substring(1, value.length - 1) else value
        return try {
            val host = HttpUrl.Builder().scheme("https").host(candidate).build().host
            if (':' in host) "[$host]" else host
        } catch (_: IllegalArgumentException) { null }
    }
    fun required(vararg values: String): Boolean = values.all { it.trim().isNotEmpty() }
}
