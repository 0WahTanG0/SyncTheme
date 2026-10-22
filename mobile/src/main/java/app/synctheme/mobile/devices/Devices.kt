package app.synctheme.mobile.devices

import android.content.Context
import android.os.Build
import java.util.Locale

/**
 * Часы, для которых есть пошаговая инструкция установки: Wear OS 6+ (там есть Watch Face Push —
 * установка своего циферблата). Телефон подходит любой с Android 9 и новее.
 */
/** Марка часов: от неё зависят пункты меню в инструкции. */
enum class WatchBrand { SAMSUNG, PIXEL }

enum class Watch(val title: String, val brand: WatchBrand, val names: List<String>) {
    GALAXY8("Galaxy Watch8 / Watch8 Classic", WatchBrand.SAMSUNG, listOf("Watch8")),
    GALAXY_ULTRA("Galaxy Watch Ultra", WatchBrand.SAMSUNG, listOf("Ultra")),
    GALAXY7("Galaxy Watch7 / Watch FE", WatchBrand.SAMSUNG, listOf("Watch7", "Watch FE")),
    GALAXY6("Galaxy Watch6 / Watch6 Classic", WatchBrand.SAMSUNG, listOf("Watch6")),
    GALAXY5("Galaxy Watch5 / Watch5 Pro", WatchBrand.SAMSUNG, listOf("Watch5")),
    GALAXY4("Galaxy Watch4 / Watch4 Classic", WatchBrand.SAMSUNG, listOf("Watch4")),
    PIXEL4("Google Pixel Watch 4", WatchBrand.PIXEL, listOf("Pixel Watch 4")),
    PIXEL3("Google Pixel Watch 3", WatchBrand.PIXEL, listOf("Pixel Watch 3")),
    PIXEL2("Google Pixel Watch 2", WatchBrand.PIXEL, listOf("Pixel Watch 2"));

    companion object {
        /** Часы по имени устройства из Wear OS (например, «Galaxy Watch7 (4F2A)»). */
        fun of(name: String): Watch? {
            val n = name.replace(" ", "").lowercase(Locale.ROOT)
            return entries.firstOrNull { w -> w.names.any { n.contains(it.replace(" ", "").lowercase(Locale.ROOT)) } }
        }
    }
}

/** Что известно о телефоне: Samsung ли он и какая One UI (от неё зависит глубина на экране блокировки). */
data class PhoneInfo(val samsung: Boolean, val model: String, val oneUi: Double?) {
    companion object {
        fun detect(): PhoneInfo {
            val samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
            return PhoneInfo(samsung, Build.MODEL.orEmpty(), if (samsung) oneUiVersion() else null)
        }

        /** Версия One UI из ro.build.version.oneui (80000 → 8.0, 80500 → 8.5). */
        private fun oneUiVersion(): Double? = runCatching {
            val raw = Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
                .invoke(null, "ro.build.version.oneui") as String
            raw.toIntOrNull()?.takeIf { it > 0 }?.let { it / 10000 + (it % 10000) / 100 / 10.0 }
        }.getOrNull()

        fun format(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.ROOT, "%.1f", v)
    }
}

/** Выбор пользователя: его часы (по умолчанию — те, что на связи). */
object MyDevices {
    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun watch(context: Context, connectedName: String? = null): Watch? =
        prefs(context).getString("my_watch", null)?.let { runCatching { Watch.valueOf(it) }.getOrNull() }
            ?: connectedName?.let(Watch::of)

    fun setWatch(context: Context, watch: Watch) = prefs(context).edit().putString("my_watch", watch.name).apply()
}

/** Шаги инструкции под выбранные устройства. */
object InstallGuide {
    /** Как поставить SyncTheme на часы — пункты меню зависят от марки. */
    fun watch(watch: Watch): List<String> = when (watch.brand) {
        WatchBrand.SAMSUNG -> listOf(
            "Обновите часы до One UI 8 Watch (Wear OS 6): в Galaxy Wearable → «Настройки часов» → «Обновление ПО часов».",
            "На часах: «Настройки» → «О часах» → «Программное обеспечение» → 7 раз нажмите «Версия ПО».",
            "«Настройки» → «Параметры разработчика» → включите «Отладка ADB» и «Отладка по Wi‑Fi».",
            "Подключите часы к той же сети Wi‑Fi, что и телефон («Настройки» → «Подключения» → Wi‑Fi). Galaxy Watch выключают Wi‑Fi, пока связаны по Bluetooth, — на время установки выключите на часах Bluetooth.",
            "На часах: «Отладка по Wi‑Fi» → «Сопряжение нового устройства». Телефон найдёт часы (шаг 2 ниже) — введите код с экрана часов.",
            "После установки включите Bluetooth обратно, а «Отладку по Wi‑Fi» можно выключить.",
        )
        WatchBrand.PIXEL -> listOf(
            "Обновите часы до Wear OS 6: приложение Google Pixel Watch → «Настройки часов» → «Система» → «Обновления системы».",
            "На часах: «Настройки» → «Система» → «О часах» → «Версии» → 7 раз нажмите «Номер сборки».",
            "«Настройки» → «Для разработчиков» → включите «Отладка по ADB» и «Отладка по Wi‑Fi».",
            "Подключите часы к той же сети Wi‑Fi, что и телефон («Настройки» → «Подключение» → Wi‑Fi). Если часы держат только Bluetooth — на время установки выключите на них Bluetooth.",
            "На часах: «Отладка по Wi‑Fi» → «Подключить устройство по коду». Телефон найдёт часы (шаг 2 ниже) — введите код с экрана часов.",
            "После установки включите Bluetooth обратно, а «Отладку по Wi‑Fi» можно выключить.",
        )
    }
}
