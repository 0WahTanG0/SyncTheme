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
    CLAY("Мята", "Объёмные матовые плитки, мятные кнопки и поля в оправе"),
    SOFT("Мягкий", "Выпуклые кнопки и вдавленные поля, всё в одном цвете"),
    GLASS("Стекло", "Полупрозрачные карточки и мягкое свечение"),
}

enum class AppMode(val title: String) { SYSTEM("Как в системе"), LIGHT("Светлая"), DARK("Тёмная") }

data class Appearance(val style: AppStyle = AppStyle.CLAY, val mode: AppMode = AppMode.SYSTEM)

object AppearanceStore {
    private var flow: MutableStateFlow<Appearance>? = null

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    @Synchronized
    private fun state(context: Context): MutableStateFlow<Appearance> = flow ?: run {
        val p = prefs(context)
        // С выходом «Мяты» приложение один раз переходит на неё (светлой, как на образце);
        // «Мягкий», «Стекло» и тёмный режим остаются в «Настройках». «Экспрессив» и «Неон» убраны.
        if (!p.getBoolean(KEY_CLAY, false)) p.edit().putBoolean(KEY_CLAY, true).remove("app_style").putString("app_mode", AppMode.LIGHT.name).apply()
        val loaded = Appearance(
            style = runCatching { AppStyle.valueOf(p.getString("app_style", null)!!) }.getOrDefault(AppStyle.CLAY),
            mode = runCatching { AppMode.valueOf(p.getString("app_mode", null)!!) }.getOrDefault(AppMode.SYSTEM),
        )
        MutableStateFlow(loaded).also { flow = it }
    }

    fun get(context: Context): StateFlow<Appearance> = state(context.applicationContext)

    fun set(context: Context, value: Appearance) {
        prefs(context).edit().putBoolean(KEY_CLAY, true).putString("app_style", value.style.name).putString("app_mode", value.mode.name).apply()
        state(context.applicationContext).value = value
    }

    private const val KEY_CLAY = "app_style_clay"
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
    /** Пятна «Стекла». */
    val decor: Color,
    val radius: Dp,
    val headings: FontFamily,
    val body: FontFamily,
) {
    val sectionTitle: TextStyle
        get() = when (style) {
            AppStyle.CLAY -> ClayType.title.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp)
            AppStyle.SOFT, AppStyle.GLASS -> TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
}

// Шрифты лежат готовыми начертаниями: вариативные оси применяются не везде одинаково.
val Manrope = FontFamily(
    Font(R.font.manrope_400, FontWeight.Normal), Font(R.font.manrope_600, FontWeight.SemiBold),
    Font(R.font.manrope_700, FontWeight.Bold), Font(FaceR.font.manrope_800, FontWeight.ExtraBold),
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
        AppStyle.CLAY -> {
            // «Мята»: тёплый матовый фон, светлые объёмные плитки, мятный главный цвет (см. Clay.kt).
            val c = if (dark) ClayPalette.Dark else ClayPalette.Light
            Look(
                style, dark, accent, primary = c.mint.face, onPrimary = c.mint.content, accentText = c.link,
                background = c.canvas, text = c.ink, muted = c.muted,
                card = c.surface.face, highlight = c.field, onHighlight = c.ink,
                cardBorder = Color.Transparent, control = c.field,
                bar = c.canvas, dialog = c.surface.face,
                decor = Color.Transparent, radius = ClayRadius.card, headings = Onest, body = Onest,
            )
        }

        AppStyle.SOFT -> {
            // Одноцветный: карточки — того же цвета, что фон, их выделяют свет и тень (см. Soft.kt).
            val c = if (dark) SoftPalette.Dark else SoftPalette.Light
            if (dark) {
                val ink = Color(0xFFF0F0F2)
                Look(
                    style, dark, accent, primary = Color(0xFFE6E6E8), onPrimary = Color(0xFF2A2A2C), accentText = ink,
                    background = c.base, text = ink, muted = Color(0xFFA9A9AE),
                    card = c.base, highlight = c.field, onHighlight = ink,
                    cardBorder = Color.Transparent, control = c.field,
                    bar = c.base, dialog = Color(0xFF454548),
                    decor = Color.Transparent, radius = 28.dp, headings = Onest, body = Onest,
                )
            } else {
                val ink = Color(0xFF2B2C30)
                Look(
                    style, dark, accent, primary = Color(0xFF4A4C55), onPrimary = Color.White, accentText = ink,
                    background = c.base, text = ink, muted = Color(0xFF6B6D75),
                    card = c.base, highlight = c.field, onHighlight = ink,
                    cardBorder = Color.Transparent, control = c.field,
                    bar = c.base, dialog = Color(0xFFEEEFF2),
                    decor = Color.Transparent, radius = 28.dp, headings = Onest, body = Onest,
                )
            }
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
    }
}

val LocalLook = staticCompositionLocalOf { lookFor(AppStyle.CLAY, false, 0xFF8B6CF6.toInt()) }

private fun typography(look: Look): Typography {
    val base = Typography()
    fun TextStyle.h() = copy(fontFamily = look.headings)
    fun TextStyle.b() = copy(fontFamily = look.body)
    val clay = look.style == AppStyle.CLAY
    return base.copy(
        displayLarge = base.displayLarge.h(), displayMedium = base.displayMedium.h(), displaySmall = base.displaySmall.h(),
        headlineLarge = base.headlineLarge.h(), headlineMedium = base.headlineMedium.h(),
        headlineSmall = if (clay) ClayType.heading.copy(fontSize = 24.sp) else base.headlineSmall.h().copy(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp),
        titleLarge = if (clay) ClayType.heading else base.titleLarge.h().copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleMedium = base.titleMedium.b().copy(fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.b().copy(fontWeight = FontWeight.Bold),
        bodyLarge = base.bodyLarge.b(), bodyMedium = base.bodyMedium.b(), bodySmall = base.bodySmall.b(),
        labelLarge = base.labelLarge.b().copy(fontWeight = FontWeight.Bold),
        labelMedium = base.labelMedium.b().copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.b(),
    )
}

private fun shapes(look: Look) = when (look.style) {
    AppStyle.CLAY -> Shapes(RoundedCornerShape(ClayRadius.small), RoundedCornerShape(14.dp), RoundedCornerShape(ClayRadius.tile), RoundedCornerShape(ClayRadius.card), RoundedCornerShape(28.dp))
    AppStyle.SOFT -> Shapes(RoundedCornerShape(12.dp), RoundedCornerShape(16.dp), RoundedCornerShape(20.dp), RoundedCornerShape(28.dp), RoundedCornerShape(32.dp))
    AppStyle.GLASS -> Shapes(RoundedCornerShape(8.dp), RoundedCornerShape(12.dp), RoundedCornerShape(16.dp), RoundedCornerShape(24.dp), RoundedCornerShape(28.dp))
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
    // В «Мяте» мятная плитка слишком светла для текста: ссылки, переключатели и ползунки Material — тёмно-мятные.
    val clay = look.style == AppStyle.CLAY
    val scheme = base.copy(
        primary = if (clay) look.accentText else look.primary,
        onPrimary = if (clay) (if (dark) look.onPrimary else Color.White) else look.onPrimary,
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
