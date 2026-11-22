package com.ohsung.marketbrief.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Plain GET with sane timeouts; returns the status code and body (error body included). */
internal object Http {
    fun get(url: String, timeoutMs: Int = 15_000): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs + 5_000
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return code to body
        } finally {
            conn.disconnect()
        }
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    /** data.go.kr keys come either already URL-encoded (with %) or raw; only encode the raw ones. */
    fun key(k: String): String = if ('%' in k) k else enc(k)
}
