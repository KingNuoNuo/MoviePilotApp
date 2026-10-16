package com.jinge.moviepilot.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** GitHub Release 信息（用于应用更新检测） */
data class ReleaseInfo(
    val tag: String,
    val name: String?,
    val body: String?,
    val apkUrl: String?,
    val htmlUrl: String,
)

object UpdateChecker {

    const val REPO = "KingNuoNuo/MoviePilotApp"

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** 当前 App 版本名（如 0.2） */
    fun currentVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
    } catch (_: Exception) {
        "0.0.0"
    }

    /** 版本号比较：纯数字点分，忽略前导 v。返回 true 表示 remote 更新 */
    private fun isNewer(remote: String, current: String): Boolean {
        fun norm(v: String) = v.trimStart('v', 'V').split('.')
            .map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val r = norm(remote); val c = norm(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }; val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    /** 查最新 Release；有新版返回 ReleaseInfo，已是最新返回 null */
    suspend fun check(currentVersion: String): ReleaseInfo? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("GitHub 请求失败 (HTTP ${resp.code})")
            val obj = json.parseToJsonElement(resp.body?.string() ?: "{}").jsonObject
            val tag = obj["tag_name"]?.jsonPrimitive?.content ?: return@withContext null
            if (!isNewer(tag, currentVersion)) return@withContext null
            val apkUrl = (obj["assets"] as? kotlinx.serialization.json.JsonArray)
                ?.map { it.jsonObject }
                ?.firstOrNull { (it["name"]?.jsonPrimitive?.content ?: "").endsWith(".apk") }
                ?.get("browser_download_url")?.jsonPrimitive?.content
            ReleaseInfo(
                tag = tag,
                name = obj["name"]?.jsonPrimitive?.content,
                body = obj["body"]?.jsonPrimitive?.content,
                apkUrl = apkUrl,
                htmlUrl = obj["html_url"]?.jsonPrimitive?.content ?: "",
            )
        }
    }

    /** 下载 APK 到 cacheDir，返回文件；progress 回调 0~100 */
    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("下载失败 (HTTP ${resp.code})")
            val body = resp.body ?: throw IOException("下载内容为空")
            val total = body.contentLength()
            val file = File(context.cacheDir, "update.apk")
            file.outputStream().use { out ->
                var done = 0L
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = body.byteStream().read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) onProgress(((done * 100) / total).toInt().coerceIn(0, 100))
                }
                out.flush()
            }
            file
        }
    }
}
