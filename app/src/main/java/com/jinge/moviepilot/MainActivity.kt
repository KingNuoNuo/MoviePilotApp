package com.jinge.moviepilot

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import com.jinge.moviepilot.api.MpApi
import com.jinge.moviepilot.api.TokenStore
import com.jinge.moviepilot.ui.BgGray
import com.jinge.moviepilot.ui.BottomNavBar
import com.jinge.moviepilot.ui.MpTopBar
import com.jinge.moviepilot.ui.DownloadsScreen
import com.jinge.moviepilot.ui.LoginScreen
import com.jinge.moviepilot.ui.MoviePilotTheme
import com.jinge.moviepilot.ui.OrganizeBar
import com.jinge.moviepilot.ui.SearchFlow
import com.jinge.moviepilot.ui.SettingsScreen
import com.jinge.moviepilot.ui.SubscribesScreen
import com.jinge.moviepilot.ui.rememberOrganizeProgress
import kotlinx.serialization.json.JsonObject

class MainActivity : ComponentActivity() {
    private lateinit var store: TokenStore
    private lateinit var api: MpApi

    /**
     * 应用级协程作用域：绑定进程生命周期、用 SupervisorJob，某个子任务失败不会连带取消别的。
     * 下载页的批量提交（重新整理 N / 待人工复核）跑在这里 —— 这些任务一旦提交就应跑到底，
     * 不能因为用户切到别的 tab（下载页离开组合、`rememberCoroutineScope` 被取消）就静默中断，
     * 否则几十条整理会「提交一半就消失」。
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 默认网络监听回调（在 [registerNetworkMonitor] 里注册，[onDestroy] 里注销） */
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    /** 当前默认网络；null = 此刻没有默认网络（断网中） */
    private var currentNetwork: Network? = null

    /** 最近一次「已验证可出网」的网络：只有它的变化才算一次真正的网络切换 */
    private var validatedNetwork: Network? = null

    /** 网络事件的合并窗口（ms）：切网瞬间会连来多个回调，合并成一次重载 */
    private val networkHandler = Handler(Looper.getMainLooper())
    private val reloadRunnable = Runnable { onNetworkSwitched() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TokenStore(getSharedPreferences("mp", MODE_PRIVATE))
        api = MpApi(store)
        registerNetworkMonitor()

        setContent {
            MoviePilotTheme {
                AppRoot()
            }
        }
    }

    /**
     * 监听默认网络的变化，切换网络后自动恢复连接。
     *
     * 不注册这段的话：切网瞬间旧网络上的那条 TCP 连接还留在 OkHttp 连接池里，下一个请求会先写进
     * 这条死连接、等满 60 秒 readTimeout 才报失败；而失败之后各页面只是把错误写进自己的界面状态，
     * 没有任何人再触发一次加载 —— 结果就是「切网后连不上，必须重启 App 或去设置里点一次重新连接」。
     *
     * 这里只处理**默认网络**（`registerDefaultNetworkCallback`）：同一 WiFi 下漫游、BSSID 变化时
     * 系统不会换 Network 对象，所以不会误触发无谓的重刷。
     *
     * ⚠️ 触发条件是「网络**已验证可出网**（NET_CAPABILITY_VALIDATED）」而不是 onAvailable：
     * onAvailable 只代表链路连上，DHCP / DNS / 路由多半还没就绪，那一刻重载必然失败，而
     * netEpoch 不会再变第二遍 —— 界面就把错误写死了（用户看到「切网后报错、切回来也不刷」）。
     * 另外 onAvailable 的 Network 对象会按 netId 复用，断开再连回同一 WiFi 时对象相等，
     * 靠 `network != currentNetwork` 判断会漏掉恢复事件，所以改用「验证态」作为唯一判据。
     */
    private fun registerNetworkMonitor() {
        if (netCallback != null) return
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            /** 注册时系统会立刻回调一次「当前网络 + 它的能力」，那不是变化，跳过（避免一启动就白刷） */
            private var sawFirstValidated = false

            override fun onAvailable(network: Network) {
                // 只记住它；真正触发重刷等 onCapabilitiesChanged 里的 VALIDATED
                currentNetwork = network
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (network != currentNetwork) return
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    // 原来能出网、现在不能了（同一网络掉公网）→ 清掉标记，
                    // 等它重新验证通过时才算一次恢复
                    if (network == validatedNetwork) validatedNetwork = null
                    return
                }
                val first = !sawFirstValidated
                sawFirstValidated = true
                if (first || network == validatedNetwork) return
                validatedNetwork = network
                // 晚一点点再刷：验证通过瞬间 DNS / 路由偶尔还在收尾，且切网会连来多个回调，
                // 合并成一次（见 [scheduleNetworkReload]）
                scheduleNetworkReload()
            }

            override fun onLost(network: Network) {
                // 只记「默认网络没了」，不在这里重刷：等新网络的验证回调到了才算一次真正的切换
                if (network == currentNetwork) currentNetwork = null
                if (network == validatedNetwork) validatedNetwork = null
            }
        }
        netCallback = callback
        runCatching {
            // 指定主线程 Handler：下面要改 Compose 状态（netEpoch）并启动协程，落在主线更稳妥
            cm.registerDefaultNetworkCallback(callback, networkHandler)
        }
    }

    /**
     * 合并短时间内的多次网络事件，只刷一次。
     *
     * 切网瞬间会连来 onAvailable / onCapabilitiesChanged（甚至先 Lost 再 Available），
     * 每个回调都 evictAll + netEpoch++ 会让每个页面连刷 2~3 轮（首页榜单能看到「加载中」闪）。
     */
    private fun scheduleNetworkReload() {
        networkHandler.removeCallbacks(reloadRunnable)
        networkHandler.postDelayed(reloadRunnable, NET_SETTLE_MS)
    }

    /** 网络真的换了：清连接池 + 让各页面自动重载，并顺手刷新一次连接状态（顶栏 / 设置页圆点） */
    private fun onNetworkSwitched() {
        api.onNetworkChanged()
        appScope.launch { api.probe() }
    }

    override fun onDestroy() {
        networkHandler.removeCallbacks(reloadRunnable)
        netCallback?.let { cb ->
            runCatching {
                getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb)
            }
        }
        netCallback = null
        super.onDestroy()
    }

    @Composable
    private fun AppRoot() {
        var loggedIn by remember { mutableStateOf(store.token != null) }
        if (!loggedIn) {
            LoginScreen(api, store) { loggedIn = true }
            return
        }

        var tab by remember { mutableIntStateOf(0) }
        var logoutTick by remember { mutableIntStateOf(0) }
        // 全局整理进度：跨板块常驻的顶部进度条（轮询宿主整理队列，见 ui/OrganizeBar.kt）
        val organize = rememberOrganizeProgress(api)

        // 启动/升级后自动探测连接：顶栏状态立即生效，JWT 失效时自动重登
        val probeScope = rememberCoroutineScope()
        LaunchedEffect(loggedIn, logoutTick) {
            if (loggedIn) probeScope.launch { api.probe() }
        }

        // 简单页面栈：selectedMedia / directKeyword 非空时显示资源列表页
        var selectedMedia by remember { mutableStateOf<JsonObject?>(null) }
        var directKeyword by remember { mutableStateOf<String?>(null) }

        fun backToList() {
            selectedMedia = null
            directKeyword = null
        }

        Surface(color = BgGray) {
            Scaffold(
                containerColor = BgGray,
                topBar = {
                    MpTopBar(
                        online = api.serverOnline,
                        selected = tab,
                        onSelect = { tab = it }
                    )
                },
                bottomBar = {
                    BottomNavBar(selected = tab, onSelect = { tab = it })
                }
            ) { padding ->
                // 外层 Box 承担 Scaffold 内边距：顶栏/底导航/系统栏的 padding 必须套住
                // 全部板块 —— 曾把 when(tab) 挪到外面，下载/订阅页签、设置连接信息被顶栏盖住。
                Box(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                ) {
                    Column(Modifier.fillMaxSize()) {
                        // 全局整理进度条：顶栏正下方、四个板块都能看到；队列清空自动收起。
                        // 点它跳「下载」板块看明细。放在 Column 顶部而不是各页面里，才能跨页面常驻。
                        OrganizeBar(organize, onClick = { tab = 1 })
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            // 搜索流常驻组合：`when(tab)` 切板块会把 SearchFlow 移出组合、内部
                            // remember 状态清空，搜索结果随之消失。非搜索板块时折叠为 0 尺寸隐藏，
                            // 切回时搜索词 / 结果 / 榜单页签原样恢复；登出仍靠 logoutTick 重置。
                            // ⚠️ 两点：
                            // 1. 隐藏态下 BackHandler 必须失效（visible=false），否则在其他板块按返回键会被它拦截；
                            // 2. size(0.dp) 只压测量不裁绘制，必须加 clipToBounds() 真正裁掉；
                            //    且 size(0) 必须在链首 —— 外层若先 fillMaxSize，内层约束被强制成
                            //    min=max=全屏，0 会被 coerce 回全屏 → 永远压不小（踩过两次）。
                            Box(
                                Modifier.then(
                                    if (tab == 0) Modifier.fillMaxSize()
                                    else Modifier.size(0.dp).clipToBounds()
                                )
                            ) {
                                SearchFlow(
                                    api = api,
                                    selectedMedia = selectedMedia,
                                    directKeyword = directKeyword,
                                    onSelectMedia = { selectedMedia = it; directKeyword = null },
                                    onDirectSearch = { directKeyword = it; selectedMedia = null },
                                    onSelectTab = { tab = it },
                                    visible = tab == 0,
                                    showRanking = store.homeTvRanking,
                                    showMaoyanRanking = store.homeMaoyanRanking,
                                    showMovieRanking = store.homeMovieRanking,
                                    // 设置页「榜单」卡里调好的页签顺序（排序条拖动）
                                    rankingOrder = store.homeRankingOrder,
                                    // 设置页「搜索」分组的四项偏好（改了回搜索页即生效）
                                    strategy = store.searchSortStrategy,
                                    qualityPref = store.qualityPref,
                                    showDirectSearch = store.showDirectSearch,
                                    showMediaMeta = store.showMediaMeta,
                                    onBack = { backToList() },
                                    key = logoutTick
                                )
                            }
                            when (tab) {
                                // 整理提交后让顶部进度条立刻出现，不用等下一次轮询
                                1 -> DownloadsScreen(
                                    api, appScope, key = logoutTick,
                                    onOrganizeSubmitted = { organize.refreshNow() }
                                )
                                2 -> SubscribesScreen(api, key = logoutTick)
                                3 -> SettingsScreen(api, store, onLogout = {
                                    store.clear()
                                    loggedIn = false
                                    logoutTick++
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 网络事件的合并窗口（ms）：切网瞬间会连来多个回调，合并成一次重载，避免各页面连刷几轮 */
private const val NET_SETTLE_MS = 1_200L
