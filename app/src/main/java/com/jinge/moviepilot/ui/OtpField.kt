package com.jinge.moviepilot.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 二步验证动态码输入框（登录页与设置页共用，保证两处手感完全一致）：
 * 灰底圆角 + 6 位数字居中大字、字距拉开，聚焦时描主色，验证失败时描危险红。
 *
 * 只收数字、最长 6 位；用户直接粘贴带空格的验证码也能用（空格会被滤掉）。
 *
 * @param hasError 上一次验证码不正确 → 描红，配合调用方清空重填
 * @param focusRequester 服务端要求二步验证时，调用方用它把焦点自动送进来（键盘同时弹出）
 */
@Composable
fun OtpCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hasError: Boolean = false,
    focusRequester: FocusRequester? = null,
    onDone: () -> Unit = {},
) {
    val primary = Color(0xFF5A63D8)
    val border = when {
        hasError -> Color(0xFFD24444)
        else -> Color.Transparent
    }
    OutlinedTextField(
        value = value,
        // 输入法可能带回分隔空格，统一滤掉并截到 6 位
        onValueChange = { raw -> onValueChange(raw.filter(Char::isDigit).take(6)) },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(
            textAlign = TextAlign.Center,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            // 字距 6sp（不是 8）：Compose 的 letterSpacing 也会加在末位之后，居中文本会因末尾
            // 空隙整体偏左；收到 6sp 后这个偏移缩到约 3sp，视觉上基本居中，又不失字距拉开的效果。
            letterSpacing = 6.sp,
            color = Color(0xFF1A1C2B),
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Color(0xFFF3F4F8),
            focusedContainerColor = Color(0xFFF3F4F8),
            errorContainerColor = Color(0xFFF3F4F8),
            cursorColor = primary,
            unfocusedBorderColor = border,
            focusedBorderColor = if (hasError) Color(0xFFD24444) else primary,
            errorBorderColor = Color(0xFFD24444),
        ),
        isError = hasError,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            ),
    )
}
