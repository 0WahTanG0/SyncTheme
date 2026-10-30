package app.synctheme.mobile.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.synctheme.R
import app.synctheme.faces.R as FaceR
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Оформление самого приложения (не путать с темой обоев). */
enum class AppStyle(val title: String, val note: String) {
    BENTO("Плитки", "Цветные плитки разного размера — каждая открывает свой раздел"),
    GLASS("Стекло", "Полупрозрачные карточки и мягкое свечение"),
    EXPRESSIVE("Экспрессив", "Тональные цвета и живые формы"),
    NEON("Неон", "Сетка и светящиеся рамки"),
}

enum class AppMode(val title: String) { SYSTEM("Как в системе"), LIGHT("Светлая"), DARK("Тёмная") }

data class Appearance(val style: AppStyle = AppStyle.BENTO, val mode: AppMode = AppMode.SYSTEM)

object AppearanceStore {
    private var flow: MutableStateFlow<Appearance>? = null

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    @Synchronized
    private fun state(context: Context): MutableStateFlow<Appearance> = flow ?: run {
        val p = prefs(context)
        // С выходом «Плиток» приложение один раз переходит на них; прежние оформления остаются в «Настройках».
        if (!p.getBoolean(KEY_BENTO, false)) p.edit().putBoolean(KEY_BENTO, true).remove("app_style").apply()
        val loaded = Appearance(
            style = runCatching { AppStyle.valueOf(p.getString("app_style", null)!!) }.getOrDefault(AppStyle.BENTO),
            mode = runCatching { AppMode.valueOf(p.getString("app_mode", null)!!) }.getOrDefault(AppMode.SYSTEM),
        )
        MutableStateFlow(loaded).also { flow = it }
    }

    fun get(context: Context): StateFlow<Appearance> = state(context.applicationContext)

    fun set(context: Context, value: Appearance) {
        prefs(context).edit().putBoolean(KEY_BENTO, true).putString("app_style", value.style.name).putString("app_mode", value.mode.name).apply()
        state(context.applicationContext).value = value
    }

    private const val KEY_BENTO = "app_style_bento"
}

/** Все цвета, шрифты и формы выбранного оформления; акцент берётся из текущих обоев. */
@Immutable
class Look(
    val style: AppStyle,
    val dark: Boolean,
    /** Акцент как есть — для свечения, рамок и декора. */
    val accent: Color,
    /** Акцент для кнопок: на светлом фоне слишком светлый акцент затемняется. */
    val primary: Color,
    val onPrimary: Color,
    /** Акцент для текста поверх фона. */
    val accentText: Color,
    val background: Color,
    val text: Color,
    val muted: Color,
    val card: Color,
    /** Выделенная карточка (например, «Обои из галереи»). */
    val highlight: Color,
    val onHighlight: Color,
    /** Рамка карточек; прозрачная — без рамки. */
    val cardBorder: Color,
    /** Невыбранные вкладки и подложки элементов. */
    val control: Color,
    val bar: Color,
    /** Непрозрачная поверхность для диалогов. */
    val dialog: Color,
    /** Пятна «Стекла», формы «Экспрессива», сетка «Неона». */
    val decor: Color,
    val radius: Dp,
    val headings: FontFamily,
    val body: FontFamily,
) {
    val glows get() = style == AppStyle.NEON

    val sectionTitle: TextStyle
        get() = when (style) {
            AppStyle.BENTO -> TextStyle(fontFamily = headings, fontWeight = FontWeight.Black, fontSize = 18.sp)
            AppStyle.GLASS -> TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            AppStyle.EXPRESSIVE -> TextStyle(fontFamily = body, fontWeight = FontWeight.Black, fontSize = 18.sp)
            AppStyle.NEON -> TextStyle(fontFamily = headings, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
        }

    fun title(text: String) = if (style == AppStyle.NEON) text.uppercase() else text
}

// Шрифты лежат готовыми начертаниями: вариативные оси применяются не везде одинаково.
val Manrope = FontFamily(
    Font(R.font.manrope_400, FontWeight.Normal), Font(R.font.manrope_600, FontWeight.SemiBold),
    Font(R.font.manrope_700, FontWeight.Bold), Font(FaceR.font.manrope_800, FontWeight.ExtraBold),
)
val Nunito = FontFamily(
    Font(R.font.nunito_400, FontWeight.Normal), Font(R.font.nunito_600, FontWeight.SemiBold),
    Font(R.font.nunito_700, FontWeight.Bold), Font(FaceR.font.nunito_900, FontWeight.Black),
)
val Unbounded = FontFamily(
    Font(R.font.unbounded_500, FontWeight.Medium), Font(FaceR.font.unbounded_700, FontWeight.Bold),
    Font(R.font.unbounded_800, FontWeight.ExtraBold),
)
val Onest = FontFamily(
    Font(R.font.onest_400, FontWeight.Normal), Font(R.font.onest_500, FontWeight.Medium),
    Font(FaceR.font.onest_600, FontWeight.SemiBold), Font(R.font.onest_700, FontWeight.Bold),
)

/** Смешивание в sRGB, как в макетах: t = 0 → a, t = 1 → b. */
fun mix(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

fun lookFor(style: AppStyle, dark: Boolean, accentColor: Int): Look {
    val accent = Color(accentColor).copy(alpha = 1f)
    val white = Color.White
    val black = Color.Black
    val primary = if (!dark && accent.luminance() > 0.45f) mix(accent, black, 0.35f) else accent
    val onPrimary = if (primary.luminance() > 0.35f) Color(0xFF111111) else white
    val accentText = if (dark) mix(accent, white, 0.45f) else mix(accent, black, 0.35f)
    return when (style) {
        AppStyle.BENTO -> if (dark) {
            val ink = Color(0xFFF3EFFA)
            Look(
                style, dark, accent, primary, onPrimary, accentText,
                background = Color(0xFF14111C), text = ink, muted = Color(0xFFB7AFC9),
                card = Color(0xFF221D2E), highlight = Color(0xFF2C2540), onHighlight = ink,
                cardBorder = Color.Transparent, control = Color(0xFF2C2540),
                bar = Color(0xFF14111C), dialog = Color(0xFF221D2E),
                decor = Color.Transparent, radius = 26.dp, headings = Nunito, body = Nunito,
            )
        } else {
            val ink = Color(0xFF1F1A2E)
            Look(
                style, dark, accent, primary, onPrimary, accentText,
                background = Color(0xFFF4EFFA), text = ink, muted = Color(0xFF625B75),
                card = Color.White, highlight = Color(0xFFE9E1F7), onHighlight = ink,
                cardBorder = Color.Transparent, control = Color(0xFFEAE3F5),
                bar = Color(0xFFF4EFFA), dialog = Color.White,
                decor = Color.Transparent, radius = 26.dp, headings = Nunito, body = Nunito,
            )
        }

        AppStyle.GLASS -> if (dark) {
            val bg = mix(accent, Color(0xFF09090B), 0.86f)
            Look(
                style, dark, accent, primary, onPrimary, accentText,
                background = bg, text = white, muted = white.copy(alpha = 0.72f),
                card = white.copy(alpha = 0.07f), highlight = white.copy(alpha = 0.11f), onHighlight = white,
                cardBorder = white.copy(alpha = 0.14f), control = white.copy(alpha = 0.08f),
                bar = mix(bg, black, 0.35f).copy(alpha = 0.94f), dialog = mix(bg, white, 0.07f),
                decor = accent.copy(alpha = 0.40f), radius = 24.dp, headings = Manrope, body = Manrope,
            )
        } else {
            val ink = Color(0xFF15151A)
            val bg = mix(accent, white, 0.86f)
            Look(
                style, dark, accent, primary, onPrimary, accentText,
                background = bg, text = ink, muted = ink.copy(alpha = 0.64f),
                card = white.copy(alpha = 0.55f), highlight = white.copy(alpha = 0.75f), onHighlight = ink,
                cardBorder = white.copy(alpha = 0.85f), control = white.copy(alpha = 0.5f),
                bar = mix(bg, white, 0.6f).copy(alpha = 0.95f), dialog = mix(bg, white, 0.7f),
                decor = accent.copy(alpha = 0.32f), radius = 24.dp, headings = Manrope, body = Manrope,
            )
        }

        AppStyle.EXPRESSIVE -> if (dark) {
            val base = Color(0xFF111114)
            Look(
                style, dark, accent, primary, onPrimary, accentText,
                background = mix(accent, base, 0.88f), text = mix(accent, white, 0.92f), muted = mix(accent, white, 0.7f),
                card = mix(accent, base, 0.78f), highlight = mix(accent, base, 0.55f), onHighlight = mix(accent, white, 0.9f),
                cardBorder = Color.Transparent, control = mix(accent, base, 0.72f),
                bar = mix(accent, base, 0.82f), dialog = mix(accent, base, 0.8f),
                decor = mix(accent, base, 0.62f), radius = 32.dp, headings = Nunito, body = Nunito,
            )
        } else {
            val ink = mix(accent, black, 0.86f)
            Look(
                style, dark, accent, primary, onPrimary, accentText,
                background = mix(accent, white, 0.93f), text = ink, muted = ink.copy(alpha = 0.7f),
                card = mix(accent, white, 0.82f), highlight = mix(accent, white, 0.6f), onHighlight = mix(accent, black, 0.7f),
                cardBorder = Color.Transparent, control = mix(accent, white, 0.82f),
                bar = mix(accent, white, 0.86f), dialog = mix(accent, white, 0.9f),
                decor = mix(accent, white, 0.66f), radius = 32.dp, headings = Nunito, body = Nunito,
            )
        }

        AppStyle.NEON -> if (dark) {
            Look(
                style, dark, accent, primary, onPrimary, accentText,
                background = Color(0xFF06060A), text = Color(0xFFEDEDF5), muted = Color(0xFF9A9AB0),
                card = Color(0xFF0C0C12).copy(alpha = 0.88f), highlight = Color(0xFF0C0C12).copy(alpha = 0.88f), onHighlight = Color(0xFFEDEDF5),
                cardBorder = Color(0xFF1E1E2A), control = Color(0xFF0C0C12),
                bar = Color(0xFF06060A).copy(alpha = 0.95f), dialog = Color(0xFF111119),
                decor = accent.copy(alpha = 0.08f), radius = 18.dp, headings = Unbounded, body = Onest,
            )
        } else {
            Look(
                style, dark, accent, primary, onPrimary, accentText,
                background = Color(0xFFF3F3F8), text = Color(0xFF12121A), muted = Color(0xFF5A5A70),
                card = white.copy(alpha = 0.92f), highlight = white.copy(alpha = 0.92f), onHighlight = Color(0xFF12121A),
                cardBorder = Color(0xFFDCDCE6), control = white,
                bar = Color(0xFFF3F3F8).copy(alpha = 0.96f), dialog = white,
                decor = accent.copy(alpha = 0.10f), radius = 18.dp, headings = Unbounded, body = Onest,
            )
        }
    }
}

val LocalLook = staticCompositionLocalOf { lookFor(AppStyle.BENTO, false, 0xFF8B6CF6.toInt()) }

private fun typography(look: Look): Typography {
    val base = Typography()
    fun TextStyle.h() = copy(fontFamily = look.headings)
    fun TextStyle.b() = copy(fontFamily = look.body)
    val neon = look.style == AppStyle.NEON
    val heavy = if (look.style == AppStyle.EXPRESSIVE || look.style == AppStyle.BENTO) FontWeight.Black else FontWeight.ExtraBold
    return base.copy(
        displayLarge = base.displayLarge.h(), displayMedium = base.displayMedium.h(), displaySmall = base.displaySmall.h(),
        headlineLarge = base.headlineLarge.h(), headlineMedium = base.headlineMedium.h(),
        headlineSmall = base.headlineSmall.h().copy(fontWeight = heavy, fontSize = if (neon) 20.sp else 24.sp),
        titleLarge = base.titleLarge.h().copy(fontWeight = FontWeight.Bold, fontSize = if (neon) 18.sp else 22.sp),
        titleMedium = base.titleMedium.b().copy(fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.b().copy(fontWeight = FontWeight.Bold),
        bodyLarge = base.bodyLarge.b(), bodyMedium = base.bodyMedium.b(), bodySmall = base.bodySmall.b(),
        labelLarge = base.labelLarge.b().copy(fontWeight = FontWeight.Bold),
        labelMedium = base.labelMedium.b().copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.b(),
    )
}

private fun shapes(look: Look) = when (look.style) {
    AppStyle.BENTO -> Shapes(RoundedCornerShape(10.dp), RoundedCornerShape(16.dp), RoundedCornerShape(20.dp), RoundedCornerShape(26.dp), RoundedCornerShape(30.dp))
    AppStyle.GLASS -> Shapes(RoundedCornerShape(8.dp), RoundedCornerShape(12.dp), RoundedCornerShape(16.dp), RoundedCornerShape(24.dp), RoundedCornerShape(28.dp))
    AppStyle.EXPRESSIVE -> Shapes(RoundedCornerShape(10.dp), RoundedCornerShape(16.dp), RoundedCornerShape(22.dp), RoundedCornerShape(32.dp), RoundedCornerShape(36.dp))
    AppStyle.NEON -> Shapes(RoundedCornerShape(6.dp), RoundedCornerShape(8.dp), RoundedCornerShape(12.dp), RoundedCornerShape(18.dp), RoundedCornerShape(20.dp))
}

/** Тема приложения: выбранное оформление + светлая/тёмная + акцент текущих обоев. */
@Composable
fun AppTheme(accentColor: Int, content: @Composable () -> Unit) {
    val appearance by AppearanceStore.get(LocalContext.current).collectAsState()
    val dark = when (appearance.mode) {
        AppMode.SYSTEM -> isSystemInDarkTheme()
        AppMode.LIGHT -> false
        AppMode.DARK -> true
    }
    val look = remember(appearance.style, dark, accentColor) { lookFor(appearance.style, dark, accentColor) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }

    val solidCard = mix(look.background, look.card.copy(alpha = 1f), look.card.alpha)
    val tonal = mix(look.background, look.primary, if (dark) 0.3f else 0.22f)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = look.primary, onPrimary = look.onPrimary,
        primaryContainer = tonal, onPrimaryContainer = look.text,
        secondary = look.primary, onSecondary = look.onPrimary,
        secondaryContainer = tonal, onSecondaryContainer = look.text,
        background = look.background, onBackground = look.text,
        surface = look.background, onSurface = look.text,
        surfaceVariant = solidCard, onSurfaceVariant = look.muted,
        surfaceContainerLowest = look.background, surfaceContainerLow = solidCard,
        surfaceContainer = solidCard, surfaceContainerHigh = look.dialog, surfaceContainerHighest = mix(look.dialog, look.text, 0.08f),
        inverseSurface = look.text, inverseOnSurface = look.background, inversePrimary = look.accentText,
        outline = look.muted.copy(alpha = 0.6f), outlineVariant = look.muted.copy(alpha = 0.25f),
        surfaceTint = look.primary,
    )
    MaterialTheme(colorScheme = scheme, typography = typography(look), shapes = shapes(look)) {
        CompositionLocalProvider(LocalLook provides look, content = content)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
