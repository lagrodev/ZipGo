package com.example.carcatalogue.data.api

import android.content.Context
import android.util.Base64
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class PersistentCookieJar(context: Context) : CookieJar {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val cookieStore = mutableListOf<Cookie>()

    init {
        loadFromPrefs()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // Replace cookies with same name/domain/path
        cookies.forEach { newCookie ->
            cookieStore.removeAll { existing ->
                existing.name == newCookie.name &&
                    existing.domain == newCookie.domain &&
                    existing.path == newCookie.path
            }
            cookieStore.add(newCookie)
        }

        pruneExpired()
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        pruneExpired()
        return cookieStore.filter { it.matches(url) }
    }

    private fun pruneExpired() {
        val now = System.currentTimeMillis()
        val removed = cookieStore.removeAll { it.expiresAt < now }
        if (removed) {
            persist()
        }
    }

    private fun loadFromPrefs() {
        val encoded = prefs.getStringSet(KEY_COOKIES, emptySet()).orEmpty()
        encoded.forEach { encodedCookie ->
            decodeCookie(encodedCookie)?.let { cookie ->
                cookieStore.add(cookie)
            }
        }
        pruneExpired()
    }

    private fun persist() {
        val encoded = cookieStore
            .distinctBy { "${it.name}|${it.domain}|${it.path}" }
            .map { encodeCookie(it) }
            .toSet()

        prefs.edit().putStringSet(KEY_COOKIES, encoded).apply()
    }

    private fun encodeCookie(cookie: Cookie): String {
        // Base64-encode string fields to safely persist any characters.
        // Versioned format for forward compatibility.
        return listOf(
            FORMAT_V2,
            b64(cookie.name),
            b64(cookie.value),
            cookie.expiresAt.toString(),
            b64(cookie.domain),
            b64(cookie.path),
            if (cookie.secure) "1" else "0",
            if (cookie.httpOnly) "1" else "0",
            if (cookie.hostOnly) "1" else "0"
        ).joinToString("|")
    }

    private fun decodeCookie(encoded: String): Cookie? {
        return decodeCookieV2(encoded) ?: decodeCookieV1(encoded)
    }

    private fun decodeCookieV2(encoded: String): Cookie? {
        val parts = encoded.split("|")
        if (parts.size < 9) return null
        if (parts[0] != FORMAT_V2) return null

        return try {
            val name = unb64(parts[1])
            val value = unb64(parts[2])
            val expiresAt = parts[3].toLong()
            var domain = unb64(parts[4])
            val path = unb64(parts[5])
            val secure = parts[6] == "1"
            val httpOnly = parts[7] == "1"
            val hostOnly = parts[8] == "1"

            // OkHttp Cookie.Builder domain() rejects leading dots.
            if (domain.startsWith('.')) {
                domain = domain.drop(1)
            }

            val builder = Cookie.Builder()
                .name(name)
                .value(value)
                .expiresAt(expiresAt)
                .path(if (path.startsWith('/')) path else "/")

            if (hostOnly) builder.hostOnlyDomain(domain) else builder.domain(domain)
            if (secure) builder.secure()
            if (httpOnly) builder.httpOnly()

            builder.build()
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeCookieV1(encoded: String): Cookie? {
        // Legacy tab-separated format (migration support)
        val parts = encoded.split("\t")
        if (parts.size < 8) return null

        return try {
            val name = parts[0]
            val value = parts[1]
            val expiresAt = parts[2].toLong()
            var domain = parts[3]
            val path = parts[4]
            val secure = parts[5].toBoolean()
            val httpOnly = parts[6].toBoolean()
            val hostOnly = parts[7].toBoolean()

            if (domain.startsWith('.')) {
                domain = domain.drop(1)
            }

            val builder = Cookie.Builder()
                .name(name)
                .value(value)
                .expiresAt(expiresAt)
                .path(if (path.startsWith('/')) path else "/")

            if (hostOnly) builder.hostOnlyDomain(domain) else builder.domain(domain)
            if (secure) builder.secure()
            if (httpOnly) builder.httpOnly()

            builder.build()
        } catch (_: Exception) {
            null
        }
    }

    private fun b64(value: String): String {
        return Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun unb64(value: String): String {
        return String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8)
    }

    private companion object {
        const val PREFS_NAME = "cookie_jar"
        const val KEY_COOKIES = "cookies"
        const val FORMAT_V2 = "v2"
    }
}
