package com.jinge.moviepilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jinge.moviepilot.api.MpApi
import com.jinge.moviepilot.api.long
import com.jinge.moviepilot.api.posterUrl
import com.jinge.moviepilot.api.str
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** 我的订阅：查看 + 删除（触发搜索下载在搜索页做） */
@Composable
fun SubscribesScreen(api: MpApi, key: Int) {
    var subs by remember(key) { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember(key) { mutableStateOf(true) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var message by remember(key) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(key) {
        try {
            subs = api.listSubscribes()
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        message?.let {
            Text(
                it,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary, fontSize = 13.sp
            )
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error!!, color = Color(0xFF8A8FA3))
            }
            subs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有订阅，去搜索页添加吧", color = Color(0xFF8A8FA3))
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(subs) { sub ->
                    var confirmDelete by remember { mutableStateOf(false) }
                    SubscribeCard(sub, onDelete = { confirmDelete = true })
                    if (confirmDelete) {
                        AlertDialog(
                            onDismissRequest = { confirmDelete = false },
                            title = { Text("删除订阅", color = Color(0xFFD84343)) },
                            text = {
                                Text(
                                    "确定要删除「${sub.str("name") ?: "该订阅"}」的订阅吗？\n删除后服务端将不再自动搜索和下载该片。",
                                    fontSize = 14.sp
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    confirmDelete = false
                                    scope.launch {
                                        message = null
                                        try {
                                            api.deleteSubscribe(sub.long("id") ?: 0L)
                                            subs = subs.filter { it.long("id") != sub.long("id") }
                                            message = "已删除订阅"
                                        } catch (e: Exception) {
                                            message = "删除失败：${e.message}"
                                        }
                                    }
                                }) { Text("删除", color = Color(0xFFD84343), fontWeight = FontWeight.SemiBold) }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
                            },
                            containerColor = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscribeCard(sub: JsonObject, onDelete: () -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = posterUrl(sub.str("poster") ?: sub.str("poster_path") ?: sub.str("backdrop")),
                contentDescription = null,
                modifier = Modifier
                    .width(44.dp)
                    .aspectRatio(0.7f)
                    .background(Color(0xFFE3E5F0), RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(
                        sub.str("name"),
                        sub.str("season")?.let { s -> if (s != "0" && s != "null") "第${s}季" else null }
                    ).joinToString(" "),
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(sub.str("year"), sub.str("type")).joinToString(" · "),
                    fontSize = 12.sp, color = Color(0xFF8A8FA3)
                )
            }
            TextButton(onClick = onDelete) {
                Text("删除", fontSize = 13.sp, color = Color(0xFFD84343))
            }
        }
    }
}
