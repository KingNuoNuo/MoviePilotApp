package com.jinge.moviepilot

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * 全局 Coil 图片加载器：给**海报**一套独立的磁盘缓存，供下载 / 搜索 / 订阅三处共用。
 *
 * 为什么需要它：以前完全依赖 Coil 默认的磁盘缓存，而默认实现会尊重服务端返回的
 * `Cache-Control`（服务器代理的图片常带 no-cache/no-store），于是**同一张海报每次进页面
 * 都要重新走一遍服务器代理**；加上代理 cookie 30 分钟过期、豆瓣图直连 418，就表现为
 * 「有些海报总是刷新不出来」。
 *
 * 这里做的三件事：
 * 1. 固定目录 `cacheDir/poster_cache`，容量 160MB（海报是固定资源，缓存命中后进页面立刻出图）；
 * 2. [ImageLoader.Builder.respectCacheHeaders] 关掉 —— 海报 URL 内容不会变，命中就该直接用本地；
 * 3. 与 [com.jinge.moviepilot.ui.MpPosterImage] 里的固定 `memoryCacheKey`/`diskCacheKey` 配合：
 *    同一张海报不管是走代理还走直连、出现在哪个页面，都算同一份缓存。
 *
 * 放 `cacheDir` 而不是 `filesDir`：这属于「临时缓存」，系统空间吃紧时清掉无所谓（会重新拉）。
 */
class MpApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.20)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("poster_cache"))
                .maxSizeBytes(160L * 1024 * 1024)
                .build()
        }
        // 海报是内容固定的静态资源：不理会服务端的 no-cache/no-store，命中本地就不再回源
        .respectCacheHeaders(false)
        .build()
}
