package com.jinge.moviepilot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jinge.moviepilot.api.MpApi
import com.jinge.moviepilot.api.TokenStore
import kotlinx.coroutines.launch

/** 登录页：支持「账号密码」和「API 令牌」两种方式 */
@Composable
fun LoginScreen(api: MpApi, store: TokenStore, onSuccess: () -> Unit) {
    var mode by remember { mutableStateOf("token") } // token | password
    var server by remember { mutableStateOf(store.baseUrl ?: "") }
    var username by remember { mutableStateOf(store.savedUsername ?: "") }
    var password by remember { mutableStateOf(store.savedPassword ?: "") }
    var apiToken by remember { mutableStateOf(store.apiToken ?: "") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Surface(color = Color(0xFF5A63D8), modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(32.dp)
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "MoviePilot",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5A63D8)
                    )
                    Text(
                        "搜索 · 订阅 · 下载",
                        fontSize = 13.sp,
                        color = Color(0xFF8A8FA3),
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )
                    // 登录方式切换
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = mode == "token",
                            onClick = { mode = "token"; error = null },
                            label = { Text("API 令牌") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = mode == "password",
                            onClick = { mode = "password"; error = null },
                            label = { Text("账号密码") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = server,
                        onValueChange = { server = it },
                        label = { Text("服务器地址") },
                        placeholder = { Text("http://10.0.0.4:3000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    if (mode == "token") {
                        OutlinedTextField(
                            value = apiToken,
                            onValueChange = { apiToken = it },
                            label = { Text("API 令牌") },
                            placeholder = { Text("MoviePilot 设置 → API 密钥 API_TOKEN") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("用户名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = Color(0xFFD84343), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(20.dp))
                    val canSubmit = server.isNotBlank() && when (mode) {
                        "token" -> apiToken.isNotBlank()
                        else -> username.isNotBlank()
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                loading = true; error = null
                                try {
                                    if (mode == "token") {
                                        api.loginWithToken(server, apiToken.trim())
                                    } else {
                                        api.login(server, username.trim(), password)
                                    }
                                    onSuccess()
                                } catch (e: Exception) {
                                    error = e.message ?: "登录失败"
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        enabled = !loading && canSubmit,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (loading) CircularProgressIndicator(
                            modifier = Modifier.height(20.dp).width(20.dp),
                            color = Color.White, strokeWidth = 2.dp
                        ) else Text("登 录")
                    }
                }
            }
        }
    }
}
