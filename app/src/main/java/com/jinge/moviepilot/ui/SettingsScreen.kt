package com.jinge.moviepilot.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.jinge.moviepilot.api.MpApi
import com.jinge.moviepilot.api.ReleaseInfo
import com.jinge.moviepilot.api.TokenStore
import com.jinge.moviepilot.api.UpdateChecker
import kotlinx.coroutines.launch

/** 设置页输入框：灰底圆角、无边框，聚焦时描主题色 */@Composable
private fun SettingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailing: (@Composable () -> Unit)? = null,
) {
    var showPwd by remember { mutableStateOf(false) }
    Column {
        Text(
            label,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = Color(0xFF8A8FA3)
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                if (placeholder.isNotEmpty()) Text(placeholder, color = Color(0xFFB4B7C9))
            },
            singleLine = true,
            visualTransformation = if (isPassword && !showPwd) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color(0xFFF3F4F8),
                focusedContainerColor = Color(0xFFF3F4F8),
                unfocusedTextColor = Color(0xFF1A1C2B),
                focusedTextColor = Color(0xFF1A1C2B),
                cursorColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
            ),
            trailingIcon = {
                when {
                    trailing != null -> trailing()
                    isPassword -> IconButton(
                        onClick = { showPwd = !showPwd },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (showPwd) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = Color(0xFFA6AABE),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 设置页：查看/修改服务器地址与凭据、退出登录 */
@Composable
fun SettingsScreen(
    api: MpApi,
    store: TokenStore,
    onLogout: () -> Unit,
) {
    val tokenMode = store.apiToken != null
    var server by remember { mutableStateOf(store.baseUrl ?: "") }
    var apiToken by remember { mutableStateOf(store.apiToken ?: "") }
    var username by remember { mutableStateOf(store.savedUsername ?: "") }
    var password by remember { mutableStateOf(store.savedPassword ?: "") }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary
    // 连接设置卡片折叠：默认展开；连接成功后自动折叠
    var expanded by remember {
        mutableStateOf(store.baseUrl.isNullOrBlank())
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 连接状态卡片
        Surface(color = Color.White, shape = RoundedCornerShape(14.dp), shadowElevation = 1.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("当前连接", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1C2B))
                Spacer(Modifier.weight(1f))
                Text(
                    "${store.baseUrl ?: "未设置"}",
                    fontSize = 12.sp, color = primary, fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // 连接设置卡片（可折叠：点头部切换，连接成功自动折叠）
        Text("连接设置", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF8A8FA3))
        Spacer(Modifier.height(8.dp))
        Surface(color = Color.White, shape = RoundedCornerShape(14.dp), shadowElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                // 折叠头部
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (expanded) "填写连接信息" else "展开修改连接信息",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1A1C2B)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (expanded) "收起 ▲" else "展开 ▼",
                        fontSize = 12.sp, color = primary, fontWeight = FontWeight.SemiBold
                    )
                }

                message?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = Color(0xFF2E9E5B), fontSize = 13.sp)
                }
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = Color(0xFFD84343), fontSize = 13.sp)
                }

                androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        SettingField(
                            label = "服务器地址",
                            value = server,
                            onValueChange = { server = it },
                            placeholder = "http://10.0.0.4:3000"
                        )
                        Spacer(Modifier.height(14.dp))
                        SettingField(
                            label = "API 令牌（优先使用）",
                            value = apiToken,
                            onValueChange = { apiToken = it },
                            placeholder = "留空则使用账号密码",
                            isPassword = true
                        )
                        if (apiToken.isBlank()) {
                            Spacer(Modifier.height(14.dp))
                            SettingField(
                                label = "用户名",
                                value = username,
                                onValueChange = { username = it },
                                placeholder = "MoviePilot 登录用户名"
                            )
                            Spacer(Modifier.height(14.dp))
                            SettingField(
                                label = "密码",
                                value = password,
                                onValueChange = { password = it },
                                isPassword = true
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    saving = true; message = null; error = null
                                    try {
                                        if (apiToken.isNotBlank()) {
                                            api.loginWithToken(server, apiToken.trim())
                                        } else {
                                            api.login(server, username.trim(), password)
                                        }
                                        message = "已保存并连接成功"
                                        server = store.baseUrl ?: server
                                        expanded = false   // 连接成功自动折叠
                                    } catch (e: Exception) {
                                        error = e.message ?: "连接失败"
                                        expanded = true
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                            enabled = !saving && server.isNotBlank() &&
                                    (apiToken.isNotBlank() || username.isNotBlank()),
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (saving) CircularProgressIndicator(
                                modifier = Modifier.height(18.dp).width(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            ) else Text(
                                if (tokenMode) "使用令牌连接" else "保存并测试连接",
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (tokenMode) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "当前为令牌模式，填写账号密码可切换",
                                fontSize = 11.sp, color = Color(0xFFA6AABE)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 应用更新卡片：检查更新 → 新版显示折叠的更新内容 + 更新按钮
        Text("应用更新", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF8A8FA3))
        Spacer(Modifier.height(8.dp))
        UpdateCard()

        Spacer(Modifier.height(16.dp))
        Surface(color = Color.White, shape = RoundedCornerShape(14.dp), shadowElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("关于", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1C2B))
                Spacer(Modifier.height(6.dp))
                Text(
                    "MoviePilotApp · 基于 MoviePilot v3 API\n搜索资源时默认使用服务端勾选的站点",
                    fontSize = 12.sp, color = Color(0xFF8A8FA3)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD84343))
        ) {
            Text("退出登录")
        }
    }
}

/**
 * 应用更新卡片：检查 GitHub 最新 Release。
 * 有新版 → 显示版本号 + 可折叠的更新内容（默认收起）+ 更新按钮（下载完成后拉起安装器）。
 */
@Composable
private fun UpdateCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary
    var currentVersion by remember { mutableStateOf(UpdateChecker.currentVersion(context)) }
    var checking by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var notesExpanded by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }

    fun doCheck() {
        scope.launch {
            checking = true; updateError = null; release = null; checked = false
            try {
                release = UpdateChecker.check(currentVersion)
                checked = true
            } catch (e: Exception) {
                updateError = e.message ?: "检查失败"
            } finally {
                checking = false
            }
        }
    }

    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        "当前版本 v$currentVersion",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1A1C2B)
                    )
                    Text(
                        "更新源：GitHub Release",
                        fontSize = 11.sp, color = Color(0xFFA6AABE)
                    )
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = { doCheck() },
                    enabled = !checking && !downloading,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = primary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (checking) "检查中" else "检查更新", fontSize = 13.sp)
                }
            }

            // 检查结果
            updateError?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Color(0xFFD84343), fontSize = 12.sp)
            }
            if (checked && release == null && updateError == null) {
                Spacer(Modifier.height(10.dp))
                Text("已是最新版本", color = Color(0xFF2E9E5B), fontSize = 13.sp)
            }
            release?.let { rel ->
                Spacer(Modifier.height(12.dp))
                Text(
                    "发现新版本 ${rel.tag}" +
                            if (!rel.name.isNullOrBlank() && rel.name != rel.tag) " · ${rel.name}" else "",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = primary
                )
                // 更新内容（可折叠，默认收起）
                val notes = rel.body?.trim().orEmpty()
                if (notes.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { notesExpanded = !notesExpanded },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("更新内容", fontSize = 12.sp, color = Color(0xFF8A8FA3))
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (notesExpanded) "收起 ▲" else "展开 ▼",
                            fontSize = 11.sp, color = primary, fontWeight = FontWeight.SemiBold
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(visible = notesExpanded) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                notes,
                                fontSize = 12.sp, color = Color(0xFF5C5F70),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // 下载 / 更新按钮
                Button(
                    onClick = {
                        val apk = rel.apkUrl
                        if (apk == null) {
                            // 没有找到 APK 附件：浏览器打开 Release 页
                            val i = Intent(Intent.ACTION_VIEW, Uri.parse(rel.htmlUrl))
                            context.startActivity(i)
                            return@Button
                        }
                        scope.launch {
                            downloading = true; progress = 0
                            try {
                                val file = UpdateChecker.downloadApk(context, apk) { progress = it }
                                if (!installApk(context, file)) {
                                    updateError = "下载完成，但无法拉起安装器"
                                }
                            } catch (e: Exception) {
                                updateError = e.message ?: "下载失败"
                            } finally {
                                downloading = false
                            }
                        }
                    },
                    enabled = !downloading,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (downloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("$progress%", fontSize = 13.sp)
                    } else {
                        Text("立即更新", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
