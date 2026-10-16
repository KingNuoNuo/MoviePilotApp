package com.jinge.moviepilot

import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import com.jinge.moviepilot.ui.SearchFlow
import com.jinge.moviepilot.ui.SettingsScreen
import com.jinge.moviepilot.ui.SubscribesScreen
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
     */
    private fun registerNetworkMonitor() {
        if (netCallback != null) return
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            /** 注册时系统会立刻回调一次「当前网络」，那不是变化，跳过（避免一启动就白刷一轮） */
            private var sawFirst = false

            override fun onAvailable(network: Network) {
                val changed = sawFirst && network != currentNetwork
                sawFirst = true
                currentNetwork = network
                if (changed) onNetworkSwitched()
            }

            override fun onLost(network: Network) {
                // 只记「默认网络没了」，不在这里重刷：等新网络的 onAvailable 到了才算一次真正的切换
                if (network == currentNetwork) currentNetwork = null
            }
        }
        netCallback = callback
        runCatching {
            // 指定主线程 Handler：下面要改 Compose 状态（netEpoch）并启动协程，落在主线更稳妥
            cm.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
        }
    }

    /** 网络真的换了：清连接池 + 让各页面自动重载，并顺手刷新一次连接状态（顶栏 / 设置页圆点） */
    private fun onNetworkSwitched() {
        api.onNetworkChanged()
        appScope.launch { api.probe() }
    }

    override fun onDestroy() {
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
                Box(Modifier.padding(padding)) {
                    when (tab) {
                        0 -> SearchFlow(
                            api = api,
                            selectedMedia = selectedMedia,
                            directKeyword = directKeyword,
                            onSelectMedia = { selectedMedia = it; directKeyword = null },
                            onDirectSearch = { directKeyword = it; selectedMedia = null },
                            onSelectTab = { tab = it },
                            showRanking = store.homeTvRanking,
                            showMaoyanRanking = store.homeMaoyanRanking,
                            onBack = { backToList() },
                            key = logoutTick
                        )
                        1 -> DownloadsScreen(api, appScope, key = logoutTick)
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
