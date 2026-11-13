package app.synctheme.mobile.util

/**
 * Как coerceIn, но без падения, если границы сошлись наоборот (часы шире экрана на долю пикселя
 * из-за округления): тогда — середина между ними.
 */
fun Float.within(lo: Float, hi: Float): Float = if (lo <= hi) coerceIn(lo, hi) else (lo + hi) / 2
