package de.worktime.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val GITHUB_OWNER = "SaschaLucius"
private const val GITHUB_REPO = "work-time"

data class ReleaseInfo(val tagName: String, val downloadUrl: String)

suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest")
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.connect()
        if (connection.responseCode != 200) return@withContext null
        val json = JSONObject(connection.inputStream.bufferedReader().readText())
        val tagName = json.getString("tag_name")
        val assets = json.getJSONArray("assets")
        val downloadUrl = if (assets.length() > 0) {
            assets.getJSONObject(0).getString("browser_download_url")
        } else {
            json.getString("html_url")
        }
        ReleaseInfo(tagName, downloadUrl)
    } catch (_: Exception) {
        null
    }
}

fun isNewerVersion(latestTag: String, currentVersion: String): Boolean {
    val latest = latestTag.trimStart('v')
    if (latest == currentVersion) return false
    return try {
        val latestParts = latest.split(".").map { it.toInt() }
        val currentParts = currentVersion.split(".").map { it.toInt() }
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        false
    } catch (_: Exception) {
        false
    }
}
