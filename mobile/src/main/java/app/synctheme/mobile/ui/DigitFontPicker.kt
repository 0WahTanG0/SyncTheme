package app.synctheme.mobile.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.synctheme.core.DigitFont

/** Шрифт цифр как FontFamily для Compose: файл из res/font или системный тонкий. */
@SuppressLint("DiscouragedApi")
fun DigitFont.family(context: Context): FontFamily {
    val id = res?.let { context.resources.getIdentifier(it, "font", context.packageName) } ?: 0
    return if (id != 0) FontFamily(Font(id)) else FontFamily(Typeface.create("sans-serif-thin", Typeface.NORMAL))
}

/**
 * Выбор шрифта цифр — одной прокручиваемой строкой: «Как в стиле» и все шрифты, каждый показан
 * своими цифрами. selected == null — шрифт по умолчанию для стиля (styleDefault).
 */
@Composable
fun DigitFontPicker(selected: DigitFont?, styleDefault: DigitFont, onPick: (DigitFont?) -> Unit) {
    val context = LocalContext.current
    val families = remember { DigitFont.entries.associateWith { it.family(context) } }
    val options: List<DigitFont?> = listOf(null) + DigitFont.entries
    // Строка сразу прокручена к выбранному шрифту.
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (options.indexOf(selected) - 1).coerceAtLeast(0))
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options, key = { it?.name ?: "style" }) { font ->
            FontCard(
                family = families.getValue(font ?: styleDefault),
                title = font?.title ?: "Как в стиле",
                selected = selected == font,
            ) { onPick(font) }
        }
    }
}

@Composable
private fun FontCard(family: FontFamily, title: String, selected: Boolean, onClick: () -> Unit) {
    val look = LocalLook.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .width(84.dp)
            .clip(shape)
            .background(if (selected) look.primary.copy(alpha = 0.22f) else look.control)
            .border(if (selected) 2.dp else 1.dp, if (selected) look.primary else look.cardBorder, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("10:08", fontFamily = family, fontSize = 22.sp, maxLines = 1, color = look.text)
        Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = if (selected) look.text else look.muted)
    }
}
