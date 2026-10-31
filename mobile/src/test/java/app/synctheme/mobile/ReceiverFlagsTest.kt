package app.synctheme.mobile

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android 14+ требует флаг экспорта у каждого приёмника рассылок, кроме чисто системных, — иначе
 * регистрация падает (так в 1.23.2 перестали запускаться обои с часами). Robolectric этого не
 * проверяет, поэтому смотрим в сами исходники: приёмники регистрируются только через ContextCompat с флагом.
 */
class ReceiverFlagsTest {
    @Test
    fun receiversDeclareExportFlag() {
        val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("исходники найдены", sources.isNotEmpty())
        val bad = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line ->
                val call = Regex("""(?<![.\w])registerReceiver\(|\.registerReceiver\(""").containsMatchIn(line) && !line.contains("unregisterReceiver")
                // registerReceiver(null, …) — чтение «липкой» рассылки, приёмника нет.
                val ok = line.contains("ContextCompat.registerReceiver") || line.contains("registerReceiver(null,")
                "${file.name}:${i + 1}: ${line.trim()}".takeIf { call && !ok }
            }
        }
        assertTrue("приёмники без флага экспорта:\n" + bad.joinToString("\n"), bad.isEmpty())
    }
}
