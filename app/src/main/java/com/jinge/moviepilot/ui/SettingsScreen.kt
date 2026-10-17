package com.jinge.moviepilot.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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

/**
 * 设置项行版式：左列「标题 + 说明」，右列一个控件（[trailing]），垂直居中、右边缘对齐。
 * 开关行与下拉行都走这一套 —— 搜索卡里四行的节奏才一致（曾经下拉甩到下一行、行高是别人两倍）。
 *
 * [badge] 是挂在标题右侧的徽标位（由调用处决定形态，如可点的「依赖插件」）。
 */
@Composable
private fun SettingRow(
    title: String,
    desc: String,
    badge: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit,
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
        trailing()
    }
}

/** 设置项开关行 = [SettingRow] + 右侧开关（说明文字尽量压到一行，行高才不会被撑开）。 */
@Composable
private fun SettingSwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    primary: Color,
    badge: (@Composable () -> Unit)? = null,
) {
    SettingRow(title, desc, badge) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = primary),
        )
    }
}

/** 排序条 chip 固定尺寸：86dp 宽 + 9dp 间距 → 节距 95dp，3 枚合计 276dp ≤ 卡内可用 296dp */
private val RankChipWidth = 86.dp
private val RankChipHeight = 36.dp
private val RankChipGap = 9.dp

/**
 * 榜单排序条：三枚「logo + 名称」chip，长按拖动换位、实时预览，位置变化通过
 * [onOrderChange] 回传（调用方持久化）。[dimIds] 里的是已关闭的榜，chip 内容变淡提示。
 *
 * 拖动实现：被拖 chip 用 graphicsLayer.translationX 跟手；拖过相邻 chip 的中线
 * （节距一半）就交换位置并回退一个节距的位移，chip 始终钉在指尖下。交换是即时的
 * （位置动画略），三枚单行的场景里足够利落。快速甩动可连续换位——每次跨过中线
 * 各结算一次。pointerInput 只 key [id]，order 变化时手势不中断。
 */
@Composable
private fun RankingSortRow(
    order: List<String>,
    dimIds: Set<String>,
    onOrderChange: (List<String>) -> Unit,
) {
    val pitchPx = with(LocalDensity.current) { (RankChipWidth + RankChipGap).toPx() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragX by remember { mutableFloatStateOf(0f) }
    // 手势回调里读的是快照：order 变化触发重组时，pointerInput(Unit) 不重启、闭包不失效
    val currentOrder by rememberUpdatedState(order)
    val currentOnChange by rememberUpdatedState(onOrderChange)

    Row(horizontalArrangement = Arrangement.spacedBy(RankChipGap)) {
        order.forEach { id ->
            val dragging = draggingId == id
            Box(
                Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationX = if (dragging) dragX else 0f }
                    .shadow(if (dragging) 6.dp else 0.dp, RoundedCornerShape(12.dp))
                    .size(RankChipWidth, RankChipHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (dragging) Color.White else FieldFill)
                    .then(
                        if (dragging) Modifier.border(
                            1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)
                        ) else Modifier
                    )
                    .pointerInput(id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { draggingId = id; dragX = 0f },
                            onDragEnd = { draggingId = null; dragX = 0f },
                            onDragCancel = { draggingId = null; dragX = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                dragX += amount.x
                                val dir = when {
                                    dragX > pitchPx / 2 -> 1
                                    dragX < -pitchPx / 2 -> -1
                                    else -> 0
                                }
                                if (dir != 0) {
                                    val list = currentOrder
                                    val from = list.indexOf(id)
                                    val to = from + dir
                                    if (from >= 0 && to in list.indices) {
                                        val swapped = list.toMutableList()
                                        swapped[from] = swapped[to].also { swapped[to] = id }
                                        currentOnChange(swapped)
                                        dragX -= dir * pitchPx
                                    }
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.alpha(if (id in dimIds && !dragging) 0.45f else 1f)
                ) {
                    when (id) {
                        "maoyan" -> Icon(
                            BrandIcons.Maoyan, contentDescription = null,
                            tint = MaoyanRed, modifier = Modifier.size(18.dp)
                        )
                        "tv" -> Box(
                            Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(16.dp * 5f / 24f))
                        ) {
                            Icon(
                                BrandIcons.Douban, contentDescription = null,
                                tint = DoubanGreen, modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> Icon(
                            BrandVideoIcons.Movie, contentDescription = null,
                            tint = Color.Unspecified, modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(
                        when (id) {
                            "maoyan" -> "猫眼"
                            "tv" -> "豆瓣"
                            else -> "电影"
                        },
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
                    )
                }
            }
        }
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
            // 4dp 而非 6dp：行盒压到 24dp（16sp 行高 + 上下 4dp），跟「关于」卡的块间距节奏对得上；
            // 点击区仍有 24dp 高（文字本身 16dp），日常点得到
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 11.sp, lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold, color = SelectedPillText,
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

/** 分组内两行之间的 1dp 分隔线（两侧 12dp 留白），与旧版「首页功能」卡里的一致 */
@Composable
private fun RowDivider() {
    Spacer(Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
    Spacer(Modifier.height(12.dp))
}

/**
 * 设置页的折叠卡：卡片头一行「标题 + 状态摘要 + 收起/展开」，点整行折叠、内容缩进在头下面。
 * 与「连接设置」卡同一套交互（整行 clickable + AnimatedVisibility），榜单 / 搜索两张卡共用。
 *
 * [summary] 是折叠状态下唯一能看到的当前值，必须简短（右对齐前的剩余宽度，超长会省略）。
 */
@Composable
private fun CollapsibleSettingCard(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    summary,
                    fontSize = 11.sp, color = TextTertiary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (expanded) "收起 ▲" else "展开 ▼",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = primary
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) { content() }
            }
        }
    }
}

/**
 * 下拉触发器的最小宽度：搜索卡右列两个下拉（推荐策略 / 画质偏好）等宽，右边缘才齐。
 *
 * 116dp = 12(左内边距) + 最长标签「做种速度优先」12sp 约 72dp + 4 + 16(▾) + 8(右内边距)。
 * 用 `widthIn(min)` 而不是定宽：标签更长或系统字号放大时触发器能自己长开，不会把字截掉。
 */
private val DropdownTriggerWidth = 116.dp

/**
 * 弹出菜单的最小宽度（实际取 `max(触发器宽度, 这个值)`，即菜单不会比触发器窄）。
 * 128dp 装得下最长标签 13sp 约 78dp + 左右各 14dp 内边距 + ✓。
 */
private val DropdownMenuWidth = 128.dp

/**
 * 下拉选择器：浅灰底胶囊显示当前值 + ▾，点开在下方弹出白色圆角菜单。
 *
 * 用 [Popup] 手写而不是 Material3 的 DropdownMenu：后者的容器色来自 `surfaceContainer`
 * （本项目的配色方案里没定义，会落到默认的淡紫灰），圆角也只有 4dp，与卡片 12–14dp 的语言不搭。
 * 自己画能直接复用项目的白底 + 12dp + 阴影，且不必依赖 Material3 的小版本 API。
 *
 * ⚠️ 菜单宽度必须在**布局里给定**（[DropdownMenuWidth]）：菜单在 [Popup] 里测量时约束是窗口宽，
 * 若让 wrap-content 的列去装 `fillMaxWidth` 的行，行会拿到窗口宽度 → 菜单左右撑满整个屏幕
 * （踩过的实际 bug）。定宽后行内 `fillMaxWidth` 解析成菜单宽度，「标签 weight(1f) + ✓ 靠右」才成立。
 */
@Composable
private fun SettingsDropdown(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var triggerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val menuWidth = with(density) { maxOf(triggerSize.width.toDp(), DropdownMenuWidth) }
    Box {
        Row(
            Modifier
                .widthIn(min = DropdownTriggerWidth)
                .onGloballyPositioned { triggerSize = it.size }
                .clip(RoundedCornerShape(10.dp))
                .background(FieldFill)
                .clickable { open = true }
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            // 文字靠左、▾ 钉在右边缘：两个下拉的控件位置完全一致
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                options.getOrElse(selected) { options.firstOrNull().orEmpty() },
                fontSize = 12.sp, color = TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
        if (open) {
            Popup(
                // 右对齐触发器，向下让出一个触发器的高度 → 菜单紧贴触发器下沿弹出
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, triggerSize.height + with(density) { 6.dp.roundToPx() }),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 8.dp
                ) {
                    // 宽度跟着触发器走（不窄于 [DropdownMenuWidth]）：右边缘与触发器对齐，两个下拉版式一致
                    Column(Modifier.width(menuWidth).padding(vertical = 6.dp)) {
                        options.forEachIndexed { i, label ->
                            val isSel = i == selected
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(i); open = false }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    label,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSel) MaterialTheme.colorScheme.primary else TextPrimary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                if (isSel) {
                                    Text(
                                        "✓",
                                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
    var movieOn by remember { mutableStateOf(store.homeMovieRanking) }
    // 榜单顺序 = 首页页签序；「榜单」卡第一行的「榜单排序」条长按拖动调整并持久化
    var rankingOrder by remember { mutableStateOf(store.homeRankingOrder) }
    // 首页功能 › 搜索：推荐策略 / 全局画质偏好 / 两个开关（都写回 TokenStore，退出登录保留）
    var sortStrategy by remember { mutableIntStateOf(store.searchSortStrategy) }
    var quality by remember { mutableIntStateOf(store.qualityPref) }
    var directSearchOn by remember { mutableStateOf(store.showDirectSearch) }
    var mediaMetaOn by remember { mutableStateOf(store.showMediaMeta) }
    // 两张折叠卡默认都展开：设置页就是来改这些开关的，默认收起反而要先多点一下
    // 折叠卡默认收起（表头摘要收起时也能看到当前设置），点表头展开
    var rankingExpanded by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }
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

        // 首页功能：榜单 / 搜索两张可折叠卡（表头右侧的摘要在收起时也能看到当前设置）
        Text("首页功能", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextTertiary)
        Spacer(Modifier.height(8.dp))

        // ---- 榜单 ----（卡内第一行是「榜单排序」拖动条，下方三行开关固定顺序）
        CollapsibleSettingCard(
            title = "榜单",
            summary = rankingOrder.mapNotNull { id ->
                when (id) {
                    "tv" -> "豆瓣".takeIf { rankingOn }
                    "maoyan" -> "猫眼".takeIf { maoyanOn }
                    else -> "电影".takeIf { movieOn }
                }
            }.joinToString(" · ").ifEmpty { "均未开启" },
            expanded = rankingExpanded,
            onToggle = { rankingExpanded = !rankingExpanded },
        ) {
            // 「榜单排序」栏：三枚品牌 chip 长按拖动换位，实时预览、松手即保存（置于卡内第一行）
            Column(Modifier.padding(top = 10.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("榜单排序", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("长按拖动调整首页页签顺序", fontSize = 11.sp, color = TextTertiary)
                }
                Spacer(Modifier.height(8.dp))
                RankingSortRow(
                    order = rankingOrder,
                    // 关掉的榜 chip 变淡（仍可拖，顺序全局保留）
                    dimIds = setOfNotNull(
                        "tv".takeUnless { rankingOn },
                        "maoyan".takeUnless { maoyanOn },
                        "movie".takeUnless { movieOn },
                    ),
                    onOrderChange = {
                        rankingOrder = it
                        store.homeRankingOrder = it
                    },
                )
            }
            RowDivider()
            // 三行开关固定顺序展示；顺序本身由上方「榜单排序」条调整（行序不随拖动变，避免视觉跳动）
            SettingSwitchRow(
                title = "豆瓣电视榜单",
                desc = "首页展示豆瓣国内/国外剧集周榜，点击可直接搜索",
                checked = rankingOn,
                onCheckedChange = { rankingOn = it; store.homeTvRanking = it },
                primary = primary,
            )
            RowDivider()
            SettingSwitchRow(
                title = "猫眼电视榜单",
                desc = "首页展示猫眼电视剧/综艺热度榜",
                // 徽标即入口：点这颗跳插件仓库，不再单起一行重复说「插件」
                badge = {
                    BadgeLink("依赖插件", "https://github.com/KingNuoNuo/MoviePilot-Plugins")
                },
                checked = maoyanOn,
                onCheckedChange = { maoyanOn = it; store.homeMaoyanRanking = it },
                primary = primary,
            )
            RowDivider()
            SettingSwitchRow(
                title = "电影票房榜",
                desc = "首页展示国内/全球年度票房榜",
                badge = {
                    BadgeLink("依赖插件", "https://github.com/KingNuoNuo/MoviePilot-Plugins")
                },
                checked = movieOn,
                onCheckedChange = { movieOn = it; store.homeMovieRanking = it },
                primary = primary,
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---- 搜索 ----
        val qualitySummary = when (quality) {
            QualityPref.UHD -> "4K 优先"
            QualityPref.FHD -> "1080P 优先"
            else -> "全部画质"
        }
        CollapsibleSettingCard(
            title = "搜索",
            summary = "${SortStrategy.labelOf(sortStrategy)} · $qualitySummary",
            expanded = searchExpanded,
            onToggle = { searchExpanded = !searchExpanded },
        ) {
            // ① 搜索框右侧的直达按钮：最常用的一项，放最前
            SettingSwitchRow(
                title = "直接搜种子入口",
                desc = "搜索框右侧的直达按钮，可直接搜站点",
                checked = directSearchOn,
                onCheckedChange = { directSearchOn = it; store.showDirectSearch = it },
                primary = primary,
            )
            RowDivider()

            // ② 搜索结果卡异步补详情
            SettingSwitchRow(
                title = "结果补主演·首播日",
                desc = "按需查详情补全主演与首播日",
                checked = mediaMetaOn,
                onCheckedChange = { mediaMetaOn = it; store.showMediaMeta = it },
                primary = primary,
            )
            RowDivider()

            // ③ 结果推荐策略：下拉（选项见 SortStrategy.labels）
            SettingRow(
                title = "结果推荐策略",
                desc = "资源列表怎么排、★推荐哪条",
            ) {
                SettingsDropdown(
                    options = SortStrategy.labels,
                    selected = sortStrategy,
                    onSelect = { sortStrategy = it; store.searchSortStrategy = it },
                )
            }
            RowDivider()

            // ④ 画质偏好：与排序策略正交（策略答「怎么排」，它答「要哪种」），控件与 ③ 同款下拉
            SettingRow(
                title = "画质偏好",
                desc = "优先展示所选画质，不过滤其它",
            ) {
                SettingsDropdown(
                    options = QualityPref.labels,
                    selected = quality,
                    onSelect = { quality = it; store.qualityPref = it },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 应用更新卡片：检查更新 → 新版显示折叠的更新内容 + 更新按钮
        Text("应用更新", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextTertiary)
        Spacer(Modifier.height(8.dp))
        UpdateCard(store)

        Spacer(Modifier.height(16.dp))

        // 关于卡片：与上方卡片统一「卡外灰小标题 + 白卡」风格
        Text("关于", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextTertiary)
        Spacer(Modifier.height(8.dp))
        Surface(color = Color.White, shape = RoundedCornerShape(14.dp), shadowElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                // ⚠️ 这几行**必须显式写 lineHeight**：只给 fontSize 的话行高会继承 Material3 的
                // 默认 bodyLarge（24sp），于是 12sp / 11sp 的字也按 24sp 行距排 —— 关于卡下部几行
                // 「行距越往下越松」就是这么来的（实测行心距 24 / 32.5 / 35.5dp）。
                // 统一后：12sp 行盒 18dp、11sp 行盒 16dp，块与块之间用 Spacer 控到视觉空隙 ≈15dp（均匀）。
                Text(
                    "MoviePilotApp",
                    fontSize = 14.sp, lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "基于 MoviePilot v3 API",
                    fontSize = 12.sp, lineHeight = 18.sp, color = TextTertiary
                )
                Spacer(Modifier.height(6.dp))
                // 作者 + 项目源码仓库同一行：作者是灰字说明，仓库是主色可点的链接
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("作者：KingNuoNuo", fontSize = 11.sp, lineHeight = 16.sp, color = TextTertiary)
                    Spacer(Modifier.width(12.dp))
                    RepoLink("项目仓库", "https://github.com/KingNuoNuo/MoviePilotApp")
                }
                // 许可单独占一行：AGPL-3.0 要求分发时告知许可，链接指向仓库里的 LICENSE 全文。
                // 两行行盒本身已 24dp 高（16sp 行高 + RepoLink 的 4dp 上下留白），补 2dp 让块间距与上方一致
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("开源许可", fontSize = 11.sp, lineHeight = 16.sp, color = TextTertiary)
                    Spacer(Modifier.width(12.dp))
                    RepoLink("AGPL-3.0", "https://github.com/KingNuoNuo/MoviePilotApp/blob/main/LICENSE")
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
 * 应用更新卡「操作行」里控件的高度 = M3 `Switch` 的**可见轨道高度**（32dp）。
 *
 * 为什么统一到 32dp：操作行左边是描边按钮、右边是「自动检查更新 + Switch」。M3 的 Switch 可见部分
 * （轨道）恒为 52×32dp，而 `OutlinedButton` 默认 40dp —— 并排看两个「外框」一高一矮（真机实测：
 * 按钮 40dp / 开关 32dp，差 8dp）。把按钮压到 32dp 与开关轨道等高，左右两个外框才真正齐平。
 *
 * ⚠️ 按钮同时要把 `contentPadding` 的**纵向归零**：压到 32dp 后，默认 8dp 上下内边距
 * （8+18+8≈34dp）会超过可用高度，13sp 文字会被圆角裁掉。
 */
private val ActionRowControlHeight = 32.dp

/**
 * 应用更新卡片：检查 GitHub 最新 Release。
 * 有新版 → 显示版本号 + 可折叠的更新内容（默认收起）+ 更新按钮（下载完成后拉起安装器）。
 *
 * 卡内收成两行：**信息行**（当前版本 + 更新源，11sp 与 14sp 按基线对齐）、
 * **操作行**（左「检查更新」按钮 / 行尾「自动检查更新」开关）。
 * 左右两个「外框」**等宽且等高**：宽度取右侧那组的实测宽度，高度统一 [ActionRowControlHeight]。
 * 信息行右列**与操作行右列对齐**（同一栏宽 = 那组实测宽度），所以「更新源」的左边缘正好在「自动检查更新」正上方。
 *
 * **进入设置页会自动检测一次**（受操作行那颗开关控制，默认开）：
 * 自动那次是**静默**的 —— 按钮不转圈、失败不弹错，只把信息行的「更新源」换成「正在自动检测更新…」，
 * 检测完该显示新版就显示新版、没新版就显示「已是最新版本」。手动点按钮才转圈并报错。
 */
@Composable
private fun UpdateCard(store: TokenStore) {
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
    // 自动检查：卡内开关（写回 TokenStore，退出登录保留）
    var autoCheckOn by remember { mutableStateOf(store.autoCheckUpdate) }
    var silentAuto by remember { mutableStateOf(false) }
    // 操作行右侧「自动检查更新 + 开关」的实测宽度。回写给两处，让它们都按这一栏的宽度对齐：
    // ① 左边「检查更新」按钮的宽度（两栏等宽）；② 信息行「更新源」那列的栏宽（左边缘与「自动检查更新」齐）。
    var trailingW by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    fun doCheck(silent: Boolean = false) {
        if (checking) return
        scope.launch {
            checking = true; silentAuto = silent
            updateError = null; release = null; checked = false
            try {
                release = UpdateChecker.check(currentVersion)
                checked = true
            } catch (e: Exception) {
                // 自动检测静默失败：进个设置页就弹一条网络错误太吵，用户想看错误就手点一次
                if (!silent) updateError = e.message ?: "检查失败"
            } finally {
                checking = false; silentAuto = false
            }
        }
    }

    // 进入设置页（本卡首次进入组合）自动检测一次；开关关掉则不跑，重新打开会再跑一次
    LaunchedEffect(autoCheckOn) {
        if (autoCheckOn) doCheck(silent = true)
    }

    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            // 信息行：版本 + 更新源。11sp 与 14sp 按**基线**对齐，不靠垂直居中（否则小字会浮高）。
            // 「更新源」不是紧跟在版本号后面，而是**左边缘与下行「自动检查更新」的左边缘齐平** ——
            // 它占的就是操作行右列那一栏，栏宽 = 那组的实测宽度 [trailingW]，所以两行右列成一条竖线。
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "当前版本 v$currentVersion",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.alignByBaseline()
                )
                // 撑开剩余空间，把右列顶到最右；右列定宽后，栏内文字自然从栏起点（= 开关那组的左缘）开始
                Spacer(Modifier.weight(1f))
                Text(
                    // 自动检测期间把「更新源」临时换成进度文案：文字等长替换，版式不跳
                    if (silentAuto) "正在自动检测更新…" else "更新源：GitHub",
                    fontSize = 11.sp,
                    color = if (silentAuto) TextTertiary else Color(0xFFA6AABE),
                    maxLines = 1,
                    modifier = Modifier
                        .then(
                            if (trailingW > 0) Modifier.width(with(density) { trailingW.toDp() })
                            else Modifier
                        )
                        .alignByBaseline()
                )
            }
            Spacer(Modifier.height(12.dp))
            // 操作行：左「检查更新」按钮（主操作）、行尾「自动检查更新」开关（次要设置）。
            // 两个「外框」对齐成同样的尺寸：**宽**取右侧那组的实测宽度（内容决定：标签 + 间距 + 开关，
            // 用 onGloballyPositioned 回写给按钮，不写死 dp，字体放大也没事）；**高**统一 32dp
            // （见 [ActionRowControlHeight]，与 Switch 可见轨道等高）。
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { doCheck() },
                    // 只有手动检查时才置灰；静默自动检测期间按钮看着是正常的，别让用户以为按钮坏了
                    enabled = !downloading && !(checking && !silentAuto),
                    shape = RoundedCornerShape(10.dp),
                    // 纵向内边距归零：按钮被压到 32dp 高（与开关轨道等高），默认 8dp 上下留白会把 13sp 文字裁掉
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                    // 宽度：首帧还没量到就先按内容排，量到右侧那组的宽度后立刻对齐（不依赖硬编码数值）。
                    // 高度：固定 32dp，与右侧 Switch 的可见轨道等高 —— 左右两个「外框」等高。
                    modifier = Modifier
                        .then(
                            if (trailingW > 0) Modifier.width(with(density) { trailingW.toDp() })
                            else Modifier
                        )
                        .height(ActionRowControlHeight)
                ) {
                    // 静默自动检测不转圈，按钮文字保持「检查更新」
                    if (checking && !silentAuto) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = primary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (checking && !silentAuto) "检查中" else "检查更新", fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                // 右侧这组整块参与测量：它的宽度就是左边按钮最终要对齐的宽度
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.onGloballyPositioned { trailingW = it.size.width }
                ) {
                    Text("自动检查更新", fontSize = 11.sp, color = TextTertiary, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = autoCheckOn,
                        onCheckedChange = { autoCheckOn = it; store.autoCheckUpdate = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = primary),
                    )
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
