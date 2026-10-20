package app.synctheme.mobile.devices

import android.content.Context
import android.os.Build
import java.util.Locale

/**
 * Устройства, на которых работает всё, что умеет SyncTheme:
 * телефоны Samsung Galaxy с One UI 8+ (там есть «Адаптивные часы» для глубины на экране
 * блокировки) и часы на Wear OS 6+ (там есть Watch Face Push — установка своего циферблата).
 */
enum class Phone(val title: String, val models: String) {
    S26("Galaxy S26 / S26+ / S26 Ultra", "SM-S94"),
    S25("Galaxy S25 / S25+ / S25 Ultra / S25 Edge / S25 FE", "SM-S93|SM-S731"),
    S24("Galaxy S24 / S24+ / S24 Ultra / S24 FE", "SM-S92|SM-S721"),
    S23("Galaxy S23 / S23+ / S23 Ultra / S23 FE", "SM-S91|SM-S711"),
    FOLD7("Galaxy Z Fold7 / Z Flip7 / Z Flip7 FE", "SM-F966|SM-F766|SM-F761"),
    FOLD6("Galaxy Z Fold6 / Z Flip6", "SM-F956|SM-F741"),
    FOLD5("Galaxy Z Fold5 / Z Flip5", "SM-F946|SM-F731"),
    A5X("Galaxy A56 / A55 / A36 / A35 / A26", "SM-A566|SM-A556|SM-A366|SM-A356|SM-A266");

    companion object {
        /** Модель телефона по коду (SM-S936B → S25). */
        fun of(model: String): Phone? = entries.firstOrNull { p -> p.models.split('|').any { model.uppercase(Locale.ROOT).startsWith(it) } }
    }
}

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

/** Что известно о телефоне, на котором запущено приложение. */
data class PhoneInfo(val samsung: Boolean, val model: String, val phone: Phone?, val oneUi: Double?) {
    /** Телефон из списка поддерживаемых, с One UI 8 или новее (или версия неизвестна). */
    val supported get() = samsung && (oneUi == null || oneUi >= 8.0)

    companion object {
        fun detect(): PhoneInfo {
            val samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
            return PhoneInfo(samsung, Build.MODEL.orEmpty(), Phone.of(Build.MODEL.orEmpty()), if (samsung) oneUiVersion() else null)
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

/** Выбор пользователя: его телефон и часы (по умолчанию — то, что удалось определить). */
object MyDevices {
    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun phone(context: Context): Phone? =
        prefs(context).getString("my_phone", null)?.let { runCatching { Phone.valueOf(it) }.getOrNull() } ?: PhoneInfo.detect().phone

    fun setPhone(context: Context, phone: Phone) = prefs(context).edit().putString("my_phone", phone.name).apply()

    fun watch(context: Context, connectedName: String? = null): Watch? =
        prefs(context).getString("my_watch", null)?.let { runCatching { Watch.valueOf(it) }.getOrNull() }
            ?: connectedName?.let(Watch::of)

    fun setWatch(context: Context, watch: Watch) = prefs(context).edit().putString("my_watch", watch.name).apply()
}

/** Шаги инструкции под выбранные устройства. */
object InstallGuide {
    /** Как поставить и обновлять SyncTheme на телефоне Samsung. */
    fun phone(): List<String> = listOf(
        "Проверьте One UI: «Настройки» → «Сведения о телефоне» → «Сведения о ПО» → «Версия One UI» — нужна 8.0 или новее. Если ниже — «Настройки» → «Обновление ПО».",
        "Скачайте SyncTheme-phone.apk из релизов на GitHub и откройте его. Если установка заблокирована — временно выключите «Настройки» → «Безопасность и конфиденциальность» → «Автоблокировщик».",
        "Разрешите установку из этого источника и установите приложение. Дальше обновления приходят прямо в SyncTheme: «Ещё» → «Обновления».",
        "Для эффекта глубины на экране блокировки зажмите экран блокировки → «Изменить» → часы → «Шрифт и цвет» → «Адаптивные часы».",
    )

    /** Как поставить SyncTheme на часы — пункты меню зависят от марки. */
    fun watch(watch: Watch): List<String> = when (watch.brand) {
        WatchBrand.SAMSUNG -> listOf(
            "Обновите часы до One UI 8 Watch (Wear OS 6): в Galaxy Wearable → «Настройки часов» → «Обновление ПО часов».",
            "На часах: «Настройки» → «О часах» → «Программное обеспечение» → 7 раз нажмите «Версия ПО».",
            "«Настройки» → «Параметры разработчика» → включите «Отладка ADB» и «Отладка по Wi‑Fi».",
            "Подключите часы к той же сети Wi‑Fi, что и телефон («Настройки» → «Подключения» → Wi‑Fi). Galaxy Watch выключают Wi‑Fi, пока связаны по Bluetooth, — на время установки выключите на часах Bluetooth.",
            "На часах: «Отладка по Wi‑Fi» → «Сопряжение нового устройства». В SyncTheme: «Часы» → «Установить SyncTheme на часы», введите код с часов.",
            "После установки включите Bluetooth обратно, а «Отладку по Wi‑Fi» можно выключить.",
        )
        WatchBrand.PIXEL -> listOf(
            "Обновите часы до Wear OS 6: приложение Google Pixel Watch → «Настройки часов» → «Система» → «Обновления системы».",
            "На часах: «Настройки» → «Система» → «О часах» → «Версии» → 7 раз нажмите «Номер сборки».",
            "«Настройки» → «Для разработчиков» → включите «Отладка по ADB» и «Отладка по Wi‑Fi».",
            "Подключите часы к той же сети Wi‑Fi, что и телефон («Настройки» → «Подключение» → Wi‑Fi). Если часы держат только Bluetooth — на время установки выключите на них Bluetooth.",
            "На часах: «Отладка по Wi‑Fi» → «Подключить устройство по коду». В SyncTheme: «Часы» → «Установить SyncTheme на часы», введите код с часов.",
            "После установки включите Bluetooth обратно, а «Отладку по Wi‑Fi» можно выключить.",
        )
    }
}
