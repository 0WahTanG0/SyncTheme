package app.synctheme.mobile.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale

data class MonthCell(val day: Int, val inMonth: Boolean, val today: Boolean)

data class MonthGrid(val title: String, val weekdays: List<String>, val cells: List<MonthCell>)

data class CalendarEvent(val title: String, val time: String, val color: Int)

object CalendarData {
    /** Сетка текущего месяца: 6 недель с первого дня недели по локали. */
    fun month(today: LocalDate = LocalDate.now(), locale: Locale = Locale.getDefault()): MonthGrid {
        val firstDow: DayOfWeek = WeekFields.of(locale).firstDayOfWeek
        val first = today.withDayOfMonth(1)
        val shift = (first.dayOfWeek.value - firstDow.value + 7) % 7
        val start = first.minusDays(shift.toLong())
        val cells = (0 until 42).map { i ->
            val d = start.plusDays(i.toLong())
            MonthCell(d.dayOfMonth, d.month == today.month, d == today)
        }
        val weekdays = (0 until 7).map { firstDow.plus(it.toLong()).getDisplayName(TextStyle.SHORT_STANDALONE, locale) }
        val title = today.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
            .replaceFirstChar { it.titlecase(locale) } + " " + today.year
        return MonthGrid(title, weekdays, cells)
    }

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Ближайшие события на две недели (нужно разрешение на чтение календаря). */
    fun upcoming(context: Context, limit: Int): List<CalendarEvent> {
        if (!hasPermission(context)) return emptyList()
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - 12 * 3_600_000L)
            ContentUris.appendId(it, now + 14 * 86_400_000L)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR,
        )
        val events = mutableListOf<CalendarEvent>()
        val day = SimpleDateFormat("d MMM", Locale.getDefault())
        val time = SimpleDateFormat("HH:mm", Locale.getDefault())
        runCatching {
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext() && events.size < limit) {
                    val begin = c.getLong(1)
                    val end = c.getLong(2)
                    val allDay = c.getInt(3) == 1
                    if (end < now) continue
                    val isToday = LocalDate.now() == Date(begin).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    val label = when {
                        allDay && isToday -> "Весь день"
                        allDay -> day.format(Date(begin))
                        isToday -> time.format(Date(begin))
                        else -> day.format(Date(begin))
                    }
                    events += CalendarEvent(c.getString(0).orEmpty().ifBlank { "Без названия" }, label, c.getInt(4))
                }
            }
        }
        return events
    }
}
