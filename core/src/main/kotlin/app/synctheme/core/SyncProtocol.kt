package app.synctheme.core

/** Пути и ключи Wearable Data Layer, общие для телефона и часов. */
object SyncProtocol {
    const val APP_PACKAGE = "app.synctheme"

    /** Имя пакета циферблата: Watch Face Push требует формат <пакет приложения>.watchfacepush.<имя>. */
    const val WATCHFACE_PACKAGE = "$APP_PACKAGE.watchfacepush.theme"

    /** DataItem с готовым APK циферблата и токеном валидации. */
    const val PATH_WATCHFACE = "/synctheme/watchface"
    const val KEY_APK = "apk"
    const val KEY_TOKEN = "token"
    const val KEY_THEME_NAME = "name"
    const val KEY_TIMESTAMP = "ts"
    const val KEY_ACTIVATE = "activate"

    /** Для классического циферблата (часы без Watch Face Push): настройки JSON, фон JPEG и слой глубины PNG. */
    const val KEY_SETTINGS = "settings"
    const val KEY_BACKGROUND = "bg"
    const val KEY_FOREGROUND = "fg"

    /** Сообщение часы -> телефон с результатом установки. */
    const val PATH_STATUS = "/synctheme/status"

    /** Проверка времени: телефон шлёт ping, часы сразу отвечают pong со своим временем (мс, текстом). */
    const val PATH_PING = "/synctheme/ping"
    const val PATH_PONG = "/synctheme/pong"

    /** Ссылки, которыми телефон и часы открывают приложение друг друга (RemoteActivityHelper). */
    const val WATCH_OPEN_URI = "synctheme://watch"
    const val PHONE_OPEN_URI = "synctheme://phone"

    const val CAPABILITY_WATCH = "synctheme_watch"
}
