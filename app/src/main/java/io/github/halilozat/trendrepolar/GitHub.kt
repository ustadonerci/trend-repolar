package io.github.halilozat.trendrepolar

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

data class Repo(
    val fullName: String,
    val description: String,
    val language: String?,
    val stars: Int,
    val forks: Int,
    val openIssues: Int,
    val topics: List<String>,
    val license: String?,
    val createdAt: String,
    val url: String,
)

class RateLimitException : IOException()

/** "Trend" = seçilen dönemde oluşturulmuş, en çok yıldız almış repolar. */
fun buildQuery(days: Int, language: String?, text: String, today: LocalDate = LocalDate.now()): String {
    val parts = mutableListOf<String>()
    if (text.isNotBlank()) parts += text.trim()
    parts += "created:>=${today.minusDays(days.toLong())}"
    if (language != null) parts += "language:\"$language\""
    return parts.joinToString(" ")
}

fun fetchTrending(days: Int, language: String?, text: String): List<Repo> {
    val q = URLEncoder.encode(buildQuery(days, language, text), "UTF-8")
    val conn = URL("https://api.github.com/search/repositories?q=$q&sort=stars&order=desc&per_page=50")
        .openConnection() as HttpURLConnection
    conn.setRequestProperty("Accept", "application/vnd.github+json")
    conn.connectTimeout = 15_000
    conn.readTimeout = 15_000
    try {
        when (conn.responseCode) {
            200 -> {}
            403, 429 -> throw RateLimitException()
            else -> throw IOException("HTTP ${conn.responseCode}")
        }
        val items = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("items")
        return (0 until items.length()).map { i ->
            val o = items.getJSONObject(i)
            val topics = o.optJSONArray("topics")
            Repo(
                fullName = o.getString("full_name"),
                description = o.optString("description").takeUnless { it == "null" } ?: "",
                language = o.optString("language").takeUnless { it.isEmpty() || it == "null" },
                stars = o.getInt("stargazers_count"),
                forks = o.getInt("forks_count"),
                openIssues = o.getInt("open_issues_count"),
                topics = if (topics == null) emptyList() else (0 until topics.length()).map { topics.getString(it) },
                license = o.optJSONObject("license")?.optString("spdx_id")?.takeUnless { it == "NOASSERTION" },
                createdAt = o.getString("created_at").take(10),
                url = o.getString("html_url"),
            )
        }
    } finally {
        conn.disconnect()
    }
}
