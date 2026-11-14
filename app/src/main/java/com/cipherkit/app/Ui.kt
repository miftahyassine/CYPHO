package com.cipherkit.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Text that always has an explicit colour (the app has no light surface to inherit from). */
@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CK.Text,
    size: Int = 15,
    weight: FontWeight = FontWeight.Normal,
    mono: Boolean = false,
    align: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        fontWeight = weight,
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        textAlign = align,
    )
}

@Composable
fun Caption(text: String) {
    Txt(text.uppercase(), color = CK.Muted, size = 11, weight = FontWeight.SemiBold)
}

@Composable
fun CkCard(
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CK.Surface)
            .border(1.dp, if (accent) CK.Accent else CK.Outline, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun CkButton(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val bg = if (filled) (if (enabled) CK.Accent else CK.AccentDim) else Color.Transparent
    val fg = if (filled) Color.Black else CK.Accent
    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, if (filled) bg else CK.AccentDim, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(text, color = fg, weight = FontWeight.SemiBold, size = 15, align = TextAlign.Center)
    }
}

@Composable
fun CkLink(text: String, onClick: () -> Unit) {
    Txt(
        text,
        color = CK.Accent,
        size = 14,
        weight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    )
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) CK.Accent else CK.Surface2)
            .border(1.dp, if (selected) CK.Accent else CK.Outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Txt(text, color = if (selected) Color.Black else CK.Text, size = 13, weight = FontWeight.Medium)
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    hint: String = "",
    minLines: Int = 1,
    mono: Boolean = false,
    singleLine: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Caption(label)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(
                color = CK.Text,
                fontSize = 16.sp,
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            ),
            cursorBrush = SolidColor(CK.Accent),
            minLines = minLines,
            singleLine = singleLine,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(CK.Field)
                        .border(1.dp, if (focused) CK.Accent else CK.Outline, shape)
                        .padding(12.dp),
                ) {
                    if (value.isEmpty() && hint.isNotEmpty()) {
                        Txt(hint, color = CK.Muted.copy(alpha = 0.6f), size = 16)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
fun <T> Dropdown(
    label: String,
    items: List<T>,
    selected: T,
    title: (T) -> String,
    subtitle: ((T) -> String)? = null,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Caption(label)
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(CK.Field)
                    .border(1.dp, CK.Outline, shape)
                    .clickable { open = true }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(title(selected), size = 16, modifier = Modifier.weight(1f))
                Txt("▾", color = CK.Accent, size = 16)
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                modifier = Modifier.background(CK.Surface2),
            ) {
                items.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Txt(title(item), size = 15)
                                if (subtitle != null) Txt(subtitle(item), color = CK.Muted, size = 12)
                            }
                        },
                        onClick = { onPick(item); open = false },
                    )
                }
            }
        }
    }
}

@Composable
fun LangPill(selected: Alphabet, onPick: (Alphabet) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Box {
        Row(
            Modifier
                .clip(shape)
                .background(CK.Surface2)
                .border(1.dp, CK.AccentDim, shape)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt("🌐 " + selected.name.substringBefore(" ("), size = 13, weight = FontWeight.Medium)
            Txt(" ▾", color = CK.Accent, size = 13)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(CK.Surface2),
        ) {
            ALPHABETS.forEach { a ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Txt(a.name, size = 15)
                            Txt("${a.size} letters", color = CK.Muted, size = 12)
                        }
                    },
                    onClick = { onPick(a); open = false },
                )
            }
        }
    }
}

@Composable
fun MatchBar(percent: Int) {
    val frac = (percent / 100f).coerceIn(0.03f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(CK.Outline),
    ) {
        Box(
            Modifier
                .fillMaxWidth(frac)
                .fillMaxHeight()
                .background(CK.Accent),
        )
    }
}

@Composable
fun BottomBar(tab: Int, onTab: (Int) -> Unit) {
    val items = listOf("🔐" to "Cipher", "⚡" to "Solve", "🔑" to "Key finder", "🔎" to "Detect")
    Row(
        Modifier
            .fillMaxWidth()
            .background(CK.Surface)
            .border(1.dp, CK.Outline),
    ) {
        items.forEachIndexed { i, item ->
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onTab(i) }
                    .padding(top = 8.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Txt(item.first, size = 20)
                Txt(
                    item.second,
                    size = 11,
                    weight = if (tab == i) FontWeight.Bold else FontWeight.Normal,
                    color = if (tab == i) CK.Accent else CK.Muted,
                )
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .height(3.dp)
                        .width(24.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (tab == i) CK.Accent else Color.Transparent),
                )
            }
        }
    }
}
