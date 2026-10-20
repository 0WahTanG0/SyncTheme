package app.synctheme.buildtools

import com.google.android.wearable.watchface.validator.client.DwfValidatorFactory
import java.io.File
import kotlin.system.exitProcess

/**
 * Проверяет APK циферблата официальным валидатором Watch Face Push и сохраняет токен.
 *
 * Аргументы: <apk> <пакет приложения-маркетплейса> <файл для токена>
 */
fun main(args: Array<String>) {
    val (apk, packageName, tokenOut) = args
    val result = DwfValidatorFactory.create().validate(File(apk), packageName)
    if (result.failures().isNotEmpty()) {
        result.failures().forEach { System.err.println("FAILURE ${it.name()}: ${it.failureMessage()}") }
        exitProcess(1)
    }
    File(tokenOut).apply { parentFile.mkdirs() }.writeText(result.validationToken())
    println("Watch face validated, token written to $tokenOut")
}
