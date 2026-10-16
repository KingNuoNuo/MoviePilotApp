package com.jinge.moviepilot.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jinge.moviepilot.R
import com.jinge.moviepilot.api.MpApi
import com.jinge.moviepilot.api.TokenStore
import kotlinx.coroutines.launch

/** 主题色阶梯（与 Theme.kt 保持一致） */
private val IndigoBright = Color(0xFF6E76EA)
private val IndigoMain = Color(0xFF5A63D8)
private val IndigoDeep = Color(0xFF464FB8)
private val ErrorRed = Color(0xFFD84343)
// TextPrimary（1A1C2B）/ TextTertiary（8A8FA3 灰字）/ FieldFill（F3F4F8）已收敛到 ui/Colors.kt

/**
 * 登录页：账号密码登录；账号开了二步验证时，服务端会先要求一次动态验证码，
 * 页面就地展开验证码输入（与设置页共用 [OtpCodeField]，两处手感一致）。
 */
@Composable
fun LoginScreen(api: MpApi, store: TokenStore, onSuccess: () -> Unit) {
    var server by remember { mutableStateOf(store.baseUrl ?: "") }
    var username by remember { mutableStateOf(store.savedUsername ?: "") }
    var password by remember { mutableStateOf(store.savedPassword ?: "") }
    // 二步验证：只有服务端明确要求时才出现验证码行（见 MpApi.MfaRequiredException）
    var otpRequired by remember { mutableStateOf(false) }
    var otp by remember { mutableStateOf("") }
    var otpError by remember { mutableStateOf(false) }
    val otpFocus = remember { FocusRequester() }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // 服务端要求验证码 → 焦点与键盘直接送到验证码框
    LaunchedEffect(otpRequired) {
        if (otpRequired) runCatching { otpFocus.requestFocus() }
    }

    // 凭据改了就把上一轮的验证码作废，避免拿旧码去撞宿主限流
    fun invalidateOtp() {
        if (otpRequired) {
            otpRequired = false
            otp = ""
            otpError = false
        }
    }

    val canSubmit = server.isNotBlank() && username.isNotBlank() && password.isNotBlank() &&
            (!otpRequired || otp.length == 6)

    fun submit() {
        if (!canSubmit || loading) return
        scope.launch {
            loading = true; error = null
            try {
                api.login(
                    serverBase = server,
                    username = username.trim(),
                    password = password,
                    otpCode = otp.takeIf { otpRequired },
                )
                onSuccess()
            } catch (_: MpApi.MfaRequiredException) {
                otpRequired = true
                otp = ""
                otpError = false
            } catch (e: Exception) {
                val m = e.message ?: "登录失败"
                // 验证码态下的「用户名或密码错误」其实指动态码不对，只标红验证码框
                if (otpRequired && m.contains("用户名或密码错误")) {
                    otpError = true
                    otp = ""
                } else {
                    error = m
                }
            } finally {
                loading = false
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(IndigoBright, IndigoMain, IndigoDeep))
            )
    ) {
        // 背景装饰光斑，增加层次感
        Box(
            Modifier
                .offset(x = (-130).dp, y = (-90).dp)
                .size(300.dp)
                .background(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)),
                    CircleShape
                )
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 110.dp, y = 130.dp)
                .size(340.dp)
                .background(
                    Brush.radialGradient(listOf(Color(0xFFC3C8FF).copy(alpha = 0.30f), Color.Transparent)),
                    CircleShape
                )
        )

        Column(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 应用标识（MoviePilot 官方 Logo）
                    Image(
                        painter = painterResource(R.drawable.logo_moviepilot),
                        contentDescription = "MoviePilot",
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "MoviePilot",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        "搜索 · 订阅 · 下载",
                        fontSize = 13.sp,
                        color = TextTertiary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(22.dp))

                    LoginField(
                        value = server,
                        onValueChange = { server = it; invalidateOtp() },
                        label = "服务器地址",
                        placeholder = "http://10.0.0.4:3000",
                        leadingIcon = Icons.Rounded.Dns,
                        keyboardType = KeyboardType.Uri,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Next) }
                    )
                    Spacer(Modifier.height(12.dp))
                    LoginField(
                        value = username,
                        onValueChange = { username = it; invalidateOtp() },
                        label = "用户名",
                        placeholder = "登录 MoviePilot 的用户名",
                        leadingIcon = Icons.Rounded.Person,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Next) }
                    )
                    Spacer(Modifier.height(12.dp))
                    LoginField(
                        value = password,
                        onValueChange = { password = it; invalidateOtp() },
                        label = "密码",
                        placeholder = "登录密码",
                        leadingIcon = Icons.Rounded.Lock,
                        isPassword = true,
                        keyboardType = KeyboardType.Password,
                        imeAction = if (otpRequired) ImeAction.Next else ImeAction.Done,
                        onImeAction = {
                            if (otpRequired) otpFocus.requestFocus()
                            else if (canSubmit && !loading) submit()
                        }
                    )

                    // 二步验证：服务端要求时才出现（浅靛蓝说明条 + 居中大字验证码框）
                    androidx.compose.animation.AnimatedVisibility(visible = otpRequired) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFE8EAFB))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.VpnKey,
                                    contentDescription = null,
                                    tint = IndigoDeep,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "该账号已开启二步验证，请输入验证器 App 里的 6 位动态码",
                                    fontSize = 12.sp, color = IndigoDeep, lineHeight = 17.sp
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            OtpCodeField(
                                value = otp,
                                onValueChange = { otp = it; otpError = false },
                                hasError = otpError,
                                focusRequester = otpFocus,
                                onDone = { submit() },
                            )
                            if (otpError) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "验证码不正确，请重新输入",
                                    fontSize = 11.sp, color = ErrorRed
                                )
                            }
                        }
                    }

                    error?.let {
                        Spacer(Modifier.height(14.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFDECEC))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(it, color = ErrorRed, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(20.dp))

                    val active = canSubmit && !loading
                    Button(
                        onClick = { submit() },
                        enabled = active,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .shadow(if (active) 8.dp else 0.dp, RoundedCornerShape(14.dp))
                            .background(
                                if (active) Brush.horizontalGradient(listOf(IndigoBright, IndigoDeep))
                                else Brush.horizontalGradient(listOf(Color(0xFFC6C9DE), Color(0xFFB6BAD3))),
                                RoundedCornerShape(14.dp)
                            )
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                if (otpRequired) "验 证" else "登 录",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 2.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "服务器地址与凭据仅保存在本机",
                        fontSize = 11.sp,
                        color = Color(0xFFB4B7C9)
                    )
                }
            }
        }
    }
}

/** 登录页输入框：灰底圆角、无边框，聚焦时描主题色，带前置图标 */
@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: ImageVector,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
) {
    var showPwd by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = Color(0xFFB4B7C9)) },
        leadingIcon = {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = Color(0xFFA6AABE),
                modifier = Modifier.size(20.dp)
            )
        },
        singleLine = true,
        visualTransformation = if (isPassword && !showPwd) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = if (imeAction == ImeAction.Done) {
            KeyboardActions(onDone = { onImeAction() })
        } else {
            KeyboardActions(onNext = { onImeAction() })
        },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = FieldFill,
            focusedContainerColor = FieldFill,
            unfocusedTextColor = TextPrimary,
            focusedTextColor = TextPrimary,
            cursorColor = IndigoMain,
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = IndigoMain,
        ),
        trailingIcon = {
            if (isPassword) {
                IconButton(
                    onClick = { showPwd = !showPwd },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (showPwd) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
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
