package com.jinge.moviepilot

import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TokenStore(getSharedPreferences("mp", MODE_PRIVATE))
        api = MpApi(store)

        setContent {
            MoviePilotTheme {
                AppRoot()
            }
        }
    }

    @Composable
    private fun AppRoot() {
        var loggedIn by remember { mutableStateOf(store.token != null || store.apiToken != null) }
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
                            onBack = { backToList() },
                            key = logoutTick
                        )
                        1 -> DownloadsScreen(api, key = logoutTick)
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
