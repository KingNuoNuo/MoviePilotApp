package com.jinge.moviepilot.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
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
            color = TextTertiary
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
                unfocusedContainerColor = FieldFill,
                focusedContainerColor = FieldFill,
                unfocusedTextColor = TextPrimary,
                focusedTextColor = TextPrimary,
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

/** 首页功能里的榜单开关行（标题 + 说明 + 右侧开关），多个开关共用同一版式；[badge] 是挂在标题右侧的徽标位（由调用处决定形态，如可点的「依赖插件」） */
@Composable
private fun RankingSwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    primary: Color,
    badge: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                // 徽标位：由调用处决定是纯提示还是可点链接
                if (badge != null) {
                    Spacer(Modifier.width(6.dp))
                    badge()
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(desc, fontSize = 11.sp, color = TextTertiary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = primary),
        )
    }
}

/** 用系统浏览器打开 [url]：没有能处理 http 的应用时静默忽略，别让设置页崩掉 */
private fun openInBrowser(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * 可点的依赖徽标：浅靛蓝底 + 主色字 + ↗，整颗点击 → 系统浏览器打开 [url]。
 * 徽标本体只 21dp 高，外面套一圈透明内边距把点击区撑到 ~30dp（与右侧开关齐平的高度），
 * 看着还是标题旁一枚小标签，点起来不至于戳不准。
 */
@Composable
private fun BadgeLink(label: String, url: String) {
    val context = LocalContext.current
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { openInBrowser(context, url) }
            .padding(horizontal = 6.dp, vertical = 5.dp)
    ) {
        Row(
            Modifier
                .background(SelectedPill, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = SelectedPillText, maxLines = 1
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Rounded.OpenInNew,
                contentDescription = null,
                tint = SelectedPillText,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/**
 * 仓库快捷方式（按行排的链接文字）：只显示 [label] 那几个字，不把整串 URL 铺在界面上；右侧 ↗ 说明会跳出 App。
 * 点击 → 系统浏览器打开 [url]，没有可处理的应用时静默忽略。
 * 目前用在「关于」卡的项目源码仓库；卡片里的依赖插件入口是贴在标题旁的 [BadgeLink]。
 * 点击区收在文字本身（不撑满整行）：文案短，撑满后「哪里能点」反而看不出来。
 */
@Composable
private fun RepoLink(label: String, url: String) {
    val context = LocalContext.current
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { openInBrowser(context, url) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SelectedPillText,
            maxLines = 1
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Rounded.OpenInNew,
            contentDescription = null,
            tint = SelectedPillText,
            modifier = Modifier.size(13.dp)
        )
    }
}

/** 设置页：查看/修改服务器地址与凭据（账号密码 + 二步验证动态码）、退出登录 */
@Composable
fun SettingsScreen(
    api: MpApi,
    store: TokenStore,
    onLogout: () -> Unit,
) {
    var server by remember { mutableStateOf(store.baseUrl ?: "") }
    var username by remember { mutableStateOf(store.savedUsername ?: "") }
    var password by remember { mutableStateOf(store.savedPassword ?: "") }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // 二步验证：只有服务端明确说「需要二次验证」才把验证码行显出来（见 MpApi.MfaRequiredException）
    var otpRequired by remember { mutableStateOf(false) }
    var otp by remember { mutableStateOf("") }
    var otpError by remember { mutableStateOf(false) }
    val otpFocus = remember { FocusRequester() }
    var rankingOn by remember { mutableStateOf(store.homeTvRanking) }
    var maoyanOn by remember { mutableStateOf(store.homeMaoyanRanking) }
    val scope = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary
    // 连接设置卡片折叠：默认展开；连接成功后自动折叠
    var expanded by remember {
        mutableStateOf(store.baseUrl.isNullOrBlank())
    }

    // 服务端要求验证码 → 把焦点和键盘直接送到验证码框，省掉一次点击
    LaunchedEffect(otpRequired) {
        if (otpRequired) runCatching { otpFocus.requestFocus() }
    }

    // 用户名/密码一改，上一轮的验证码就作废：收起验证码行并清空，避免拿旧码去撞限流
    fun invalidateOtp() {
        if (otpRequired) {
            otpRequired = false
            otp = ""
            otpError = false
        }
    }

    // 必填齐了才能提交；已经在验证码态时还要求 6 位填满（避免拿半截码去撞宿主限流）
    val canSubmit = !saving && server.isNotBlank() && username.isNotBlank() && password.isNotBlank() &&
            (!otpRequired || otp.length == 6)

    /**
     * 保存并连接：先用账号密码试一次；服务端要求二步验证时回到验证码态，
     * 用户填满 6 位再提交一次。两次走的是同一个接口，只是多带一个 otp_password。
     */
    fun submit() {
        if (!canSubmit) return
        scope.launch {
            saving = true; message = null; error = null
            try {
                api.login(
                    serverBase = server,
                    username = username.trim(),
                    password = password,
                    otpCode = otp.takeIf { otpRequired },
                )
                message = "已保存并连接成功"
                server = store.baseUrl ?: server
                otpRequired = false
                otp = ""
                otpError = false
                expanded = false   // 连接成功自动折叠
            } catch (_: MpApi.MfaRequiredException) {
                // 密码已通过、只差一步：把验证码行显出来（LaunchedEffect 会把焦点送进去）
                otpRequired = true
                otp = ""
                otpError = false
                expanded = true
            } catch (e: Exception) {
                val m = e.message ?: "连接失败"
                // 在验证码态收到「用户名或密码错误」其实就是动态码不对（宿主对错码也回这句），
                // 只标红验证码框，别让用户以为密码也错了；其它失败（限流/连不上）照常报错
                if (otpRequired && m.contains("用户名或密码错误")) {
                    otpError = true
                    otp = ""
                } else {
                    error = m
                }
                expanded = true
            } finally {
                saving = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 连接设置卡片（可折叠：点头部切换，连接成功自动折叠）
        // 折叠头部只展示当前连接信息与在线状态：未连接则显示「未连接」；
        // 卡外的小标题「连接设置」已说明用途，行内不再重复「展开修改/填写连接信息」字样
        Text("连接设置", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextTertiary)
        Spacer(Modifier.height(8.dp))
        Surface(color = Color.White, shape = RoundedCornerShape(14.dp), shadowElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                // 折叠头部：状态圆点 + 已连接/未连接 + 服务器地址 + 右侧展开/收起提示
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 当前连接信息：已连接显示绿点 + 地址；待验证显示靛蓝点；未连接显示红点
                    val online = api.serverOnline
                    val dotColor = when {
                        otpRequired -> Color(0xFF5A63D8)
                        online == true -> SuccessGreen
                        online == false -> DangerRed
                        else -> Color(0xFFC3C6D4)
                    }
                    val statusText = when {
                        otpRequired -> "待验证"
                        online == true -> "已连接"
                        online == false -> "未连接"
                        else -> "连接中"
                    }
                    val statusColor = when {
                        otpRequired -> Color(0xFF414BB2)
                        online == true -> SuccessGreen
                        else -> TextTertiary
                    }
                    Box(Modifier.size(8.dp).background(dotColor, CircleShape))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        statusText,
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                    Spacer(Modifier.width(10.dp))
                    // 折叠态顺手带出登录账号，省得展开才知道连的是谁
                    val account = store.savedUsername?.takeIf { it.isNotBlank() }
                    Text(
                        listOfNotNull(
                            store.baseUrl?.takeIf { it.isNotBlank() } ?: "未设置服务器",
                            account,
                        ).joinToString(" · "),
                        fontSize = 12.sp, color = TextTertiary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (expanded) "收起 ▲" else "展开 ▼",
                        fontSize = 12.sp, color = primary, fontWeight = FontWeight.SemiBold
                    )
                }

                message?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = SuccessGreen, fontSize = 13.sp)
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
                            onValueChange = { server = it; invalidateOtp() },
                            placeholder = "http://10.0.0.4:3000"
                        )
                        Spacer(Modifier.height(14.dp))
                        SettingField(
                            label = "用户名",
                            value = username,
                            onValueChange = { username = it; invalidateOtp() },
                            placeholder = "MoviePilot 登录用户名"
                        )
                        Spacer(Modifier.height(14.dp))
                        SettingField(
                            label = "密码",
                            value = password,
                            onValueChange = { password = it; invalidateOtp() },
                            isPassword = true
                        )

                        // 二步验证：服务端说「需要二次验证」才出现，避免平时多出一个没用的框
                        androidx.compose.animation.AnimatedVisibility(visible = otpRequired) {
                            Column {
                                Spacer(Modifier.height(14.dp))
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(SelectedPill, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .size(14.dp)
                                            .background(primary, RoundedCornerShape(3.dp))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "该账号已开启二步验证，请输入验证器 App 里的 6 位动态码",
                                        fontSize = 12.sp, color = Color(0xFF414BB2), lineHeight = 17.sp
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "动态验证码",
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    color = TextTertiary
                                )
                                Spacer(Modifier.height(6.dp))
                                OtpCodeField(
                                    value = otp,
                                    onValueChange = { otp = it; otpError = false },
                                    hasError = otpError,
                                    focusRequester = otpFocus,
                                    onDone = { if (canSubmit) submit() },
                                )
                                if (otpError) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "验证码不正确，请重新输入",
                                        fontSize = 11.sp, color = Color(0xFFD84343)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { submit() },
                            enabled = canSubmit,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (saving) CircularProgressIndicator(
                                modifier = Modifier.height(18.dp).width(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            ) else Text(
                                if (otpRequired) "验证并连接" else "保存并连接",
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "凭据仅保存在本机；服务端开启二步验证时会自动要求动态码",
                            fontSize = 11.sp, color = Color(0xFFA6AABE)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 首页功能卡片：榜单开关（设置页改完回首页即生效）
        Text("首页功能", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextTertiary)
        Spacer(Modifier.height(8.dp))
        Surface(color = Color.White, shape = RoundedCornerShape(14.dp), shadowElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                RankingSwitchRow(
                    title = "豆瓣榜单",
                    desc = "首页展示豆瓣国内/国外剧集周榜，点击可直接搜索",
                    checked = rankingOn,
                    onCheckedChange = { rankingOn = it; store.homeTvRanking = it },
                    primary = primary,
                )
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                Spacer(Modifier.height(12.dp))
                RankingSwitchRow(
                    title = "猫眼榜单",
                    desc = "首页展示猫眼电视剧/综艺热度榜",
                    // 徽标即入口：点这颗跳插件仓库，不再单起一行重复说「插件」
                    badge = {
                        BadgeLink("依赖插件", "https://github.com/KingNuoNuo/MoviePilot-Plugins")
                    },
                    checked = maoyanOn,
                    onCheckedChange = { maoyanOn = it; store.homeMaoyanRanking = it },
                    primary = primary,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 应用更新卡片：检查更新 → 新版显示折叠的更新内容 + 更新按钮
        Text("应用更新", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextTertiary)
        Spacer(Modifier.height(8.dp))
        UpdateCard()

        Spacer(Modifier.height(16.dp))

        // 关于卡片：与上方卡片统一「卡外灰小标题 + 白卡」风格
        Text("关于", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextTertiary)
        Spacer(Modifier.height(8.dp))
        Surface(color = Color.White, shape = RoundedCornerShape(14.dp), shadowElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("MoviePilotApp", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "基于 MoviePilot v3 API\n搜索资源时默认使用服务端勾选的站点",
                    fontSize = 12.sp, color = TextTertiary
                )
                Spacer(Modifier.height(2.dp))
                // 作者 + 项目源码仓库同一行：作者是灰字说明，仓库是主色可点的链接
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("作者：KingNuoNuo", fontSize = 11.sp, color = TextTertiary)
                    Spacer(Modifier.width(12.dp))
                    RepoLink("项目仓库", "https://github.com/KingNuoNuo/MoviePilotApp")
                }
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
                        color = TextPrimary
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
                Text("已是最新版本", color = SuccessGreen, fontSize = 13.sp)
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
                        Text("更新内容", fontSize = 12.sp, color = TextTertiary)
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
                                fontSize = 12.sp, color = TextSecondary,
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
