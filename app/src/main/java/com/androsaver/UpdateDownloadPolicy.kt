package com.androsaver

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object UpdateDownloadPolicy {
    fun select(rolling: String, immutable: String?, channel: String): String? {
        val rollingUrl = rolling.toHttpUrlOrNull() ?: return null
        val base = "/Whichcraft/androsaver/releases/download/"
        fun safe(url: okhttp3.HttpUrl): Boolean = url.scheme == "https" &&
            url.host == "github.com" && url.port == 443 && url.username.isEmpty() &&
            url.password.isEmpty() && url.query == null && url.fragment == null
        if (!safe(rollingUrl) || rollingUrl.encodedPath != "${base}$channel/androsaver.apk") return null
        // Older manifests remain usable. A supplied but invalid immutable URL fails closed.
        if (immutable == null) return rolling
        val pinned = immutable.toHttpUrlOrNull() ?: return null
        val tag = "build-${Regex.escape(channel)}-[0-9a-f]{40}"
        if (!safe(pinned) || !Regex("${Regex.escape(base)}$tag/androsaver\\.apk").matches(pinned.encodedPath)) return null
        return immutable
    }
}
