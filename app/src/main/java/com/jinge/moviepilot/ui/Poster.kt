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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.jinge.moviepilot.api.MpApi
import com.jinge.moviepilot.api.posterUrlOf

/**
 * 海报图：优先走服务器代理（外网连不上 TMDB 时仍能出图）。
 * 代理失败（常见是 resource cookie 30 分钟过期）→ 自动重登刷新 cookie 再试一次 → 仍失败才回退直连。
 * 令牌模式（无 resource cookie）直接用原始地址。
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
    // attempt: 0=代理  1=重登后重试代理  2=直连
    var attempt by remember(raw) { mutableIntStateOf(if (api.imgProxyUrl(raw) != null) 0 else 2) }
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

    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(if (attempt >= 2) direct else proxy)
            .apply { if (attempt < 2) cookie?.let { addHeader("Cookie", it) } }
            .build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        onError = {
            when (attempt) {
                0 -> attempt = 1       // cookie 可能过期，重登再试
                3 -> attempt = 2       // 新 cookie 也失败，回退直连
            }
        }
    )
}
