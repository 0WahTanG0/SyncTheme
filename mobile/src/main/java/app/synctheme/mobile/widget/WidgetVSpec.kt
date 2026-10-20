package app.synctheme.mobile.widget

// Сгенерировано scripts/gen_widgets.py — не редактировать вручную.

/**
 * Размеры элементов вертикальных разметок (столбики и высокие виджеты): отступы, промежутки, значки
 * и полоса заряда — в dp, высоты подписей и строк (…Box, …Sp) — в sp. По ним [VerticalFit] подбирает
 * кегль главного значения и решает, какие строки поместятся целиком.
 */
internal class VSpec(val padH: Float, val padV: Float, val gap: Float, val div: Float, val labelBox: Float, val smallBox: Float, val icon: Float, val hourSp: Float, val hourDp: Float, val eventSp: Float, val eventDp: Float, val hintSp: Float, val alarmSp: Float, val alarmDp: Float, val bar: Float, val appIcon: Float, val appGap: Float, val titleBox: Float, val cityBox: Float, val descBox: Float, val stateBox: Float, val extraBox: Float)

internal object VSpecs {
    val column = VSpec(5f, 12f, 6f, 10f, 14f, 12f, 26f, 25f, 26f, 12f, 14f, 0f, 12f, 0f, 8f, 30f, 10f, 0f, 0f, 0f, 24f, 0f)
    val tall = VSpec(12f, 16f, 6f, 10f, 17f, 15f, 40f, 26f, 0f, 30f, 8f, 44f, 16f, 0f, 12f, 38f, 14f, 17f, 18f, 28f, 17f, 14f)

    fun of(size: WidgetSize): VSpec = if (size == WidgetSize.TALL) tall else column
}
