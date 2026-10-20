package app.synctheme.mobile.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
 * Выбор шрифта цифр: «Как в стиле» и все шрифты, каждый показан своими цифрами.
 * selected == null — шрифт по умолчанию для стиля (styleDefault).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DigitFontPicker(selected: DigitFont?, styleDefault: DigitFont, onPick: (DigitFont?) -> Unit) {
    val context = LocalContext.current
    val families = remember { DigitFont.entries.associateWith { it.family(context) } }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected == null, { onPick(null) },
            label = {
                Text("10:08", fontFamily = families.getValue(styleDefault), fontSize = 18.sp)
                Spacer(Modifier.width(6.dp))
                Text("Как в стиле", style = MaterialTheme.typography.labelMedium)
            },
        )
        DigitFont.entries.forEach { font ->
            FilterChip(
                selected == font, { onPick(font) },
                label = {
                    Text("10:08", fontFamily = families.getValue(font), fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(font.title, style = MaterialTheme.typography.labelMedium)
                },
            )
        }
    }
}
