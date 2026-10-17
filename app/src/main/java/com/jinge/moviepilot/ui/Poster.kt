package com.jinge.moviepilot.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jinge.moviepilot.api.MpApi
import com.jinge.moviepilot.api.posterUrlOf
import kotlinx.coroutines.delay

/** 整轮取图（代理 → 重登 → 直连）都失败后，再整轮重来几次 */
private const val MAX_POSTER_RETRY_ROUNDS = 2

/** 整轮重来的间隔：网络刚切换/刚恢复时，前几秒的失败多半是暂时的 */
private const val POSTER_RETRY_DELAY_MS = 3_000L

/**
 * 海报图：优先走服务器代理（服务器端抓图并缓存，豆瓣图由服务器补 Referer）。
 * 代理失败（常见是 resource cookie 30 分钟过期）→ 自动重登刷新 cookie 再试一次 → 仍失败才回退直连。
 * 接口令牌模式（无 resource cookie）直接走直连；豆瓣图直连时补 Referer 绕过防盗链。
 *
 * 缓存：请求带**固定的** `memoryCacheKey` / `diskCacheKey`（只看海报本身，不含取图方式），
 * 因此在下载 / 搜索 / 订阅任何一处加载成功后，别处再遇到同一张海报直接命中本地缓存，
 * 不再重新下载（磁盘缓存在 `MpApp` 里配，容量 160MB 且不理会服务端 no-cache）。
 *
 * 自愈：三轮都失败时，隔 [POSTER_RETRY_DELAY_MS] 再整轮重来（最多 [MAX_POSTER_RETRY_ROUNDS] 轮），
 * 救「进页面时网络刚好在切换」的那几张 —— 以前它们会一直空着，只能退出页面再进。
 */
@Composable
fun MpPosterImage(
    api: MpApi,
    raw: String?,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val direct = posterUrlOf(raw)
    // 缓存键只跟「海报本身」有关：代理 URL 与直连 URL 长得完全不同（代理还带 ?url=），
    // 直接拿 URL 当键会让同一张图各存一份，三处页面之间也共不到一起
    val cacheKey = remember(raw) { posterCacheKey(raw) }
    // attempt: 0=代理  1=重登后重试代理  3=用新 cookie 试代理  2=直连
    var attempt by remember(raw) { mutableIntStateOf(if (api.imgProxyUrl(raw) != null) 0 else 2) }
    // 整轮重来的次数（每轮改一次 request 参数，保证 Coil 真的重新发起请求）
    var round by remember(raw) { mutableIntStateOf(0) }
    // 到点后把整轮重来「点火」，放在 LaunchedEffect 里才能先 delay
    var roundTick by remember(raw) { mutableIntStateOf(0) }
    var cookie by remember(raw) { mutableStateOf(api.resourceCookie()) }
    val proxy = remember(raw) { api.imgProxyUrl(raw) }

    // 重登刷新 cookie 后用新 cookie 重试代理
    if (attempt == 1) {
        LaunchedEffect(raw) {
            api.refreshSession()
            cookie = api.resourceCookie()
            attempt = if (cookie != null) 3 else 2 // 3=用新 cookie 再试代理
        }
    }

    // 整轮重来：等一下再从头走代理（没有代理就还是直连）
    LaunchedEffect(roundTick) {
        if (roundTick == 0) return@LaunchedEffect
        delay(POSTER_RETRY_DELAY_MS)
        attempt = if (proxy != null) 0 else 2
    }

    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(if (attempt >= 2) direct else proxy)
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            // 换一轮时改个参数，确保是真正的新请求（否则 Coil 可能认为「同一个请求」不重发）
            .setParameter("mpPosterRound", round)
            .apply {
                if (attempt < 2) {
                    cookie?.let { addHeader("Cookie", it) }
                } else if (direct?.contains("doubanio.com") == true) {
                    // 豆瓣图有防盗链：无 Referer 返回 418，直连时必须补上
                    addHeader("Referer", "https://movie.douban.com/")
                }
            }
            .build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        onError = {
            when (attempt) {
                0 -> attempt = 1       // cookie 可能过期，重登再试
                3 -> attempt = 2       // 新 cookie 也失败，回退直连
                2 -> if (round < MAX_POSTER_RETRY_ROUNDS) {
                    // 直连也失败：可能是网络刚切换/刚恢复，等一下整轮重来
                    round += 1
                    roundTick += 1
                }
            }
        }
    )
}

/**
 * 海报缓存键：取「主机 + 路径」并小写，去掉协议与查询串。
 *
 * 例：`https://image.tmdb.org/t/p/w500/abc.jpg` → `poster:image.tmdb.org/t/p/w500/abc.jpg`
 * 代理取法是 `…/system/cache/image?url=<encoded>`，直连取法就是原 URL —— 归一化后两者同键，
 * 下载 / 搜索 / 订阅三处也同键，一张海报全局只存一份。
 */
private fun posterCacheKey(raw: String?): String? {
    val url = posterUrlOf(raw) ?: return null
    return "poster:" + url.substringBefore('?').substringAfter("://").lowercase()
}
