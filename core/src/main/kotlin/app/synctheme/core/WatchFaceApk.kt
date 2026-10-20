package app.synctheme.core

import com.android.apksig.ApkSigner
import com.android.apksig.KeyConfig
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Собирает APK циферблата из шаблона: подменяет watchface.xml, фон и превью, затем подписывает.
 *
 * В WFF-шаблоне эти файлы лежат как есть (res/raw не компилируется, картинки не переименовываются),
 * поэтому resources.arsc и манифест можно оставить без изменений.
 */
object WatchFaceApk {
    const val ENTRY_WATCHFACE_XML = "res/raw/watchface.xml"
    const val ENTRY_BACKGROUND = "res/drawable-nodpi-v4/wf_bg.jpg"
    const val ENTRY_PREVIEW = "res/drawable-nodpi-v4/preview.png"

    /** Watch Face Push принимает только эти файлы. */
    private fun isAllowed(name: String) =
        name == "AndroidManifest.xml" || name == "resources.arsc" ||
            name.startsWith("res/") || name.startsWith("META-INF/")

    class Signer(val privateKey: PrivateKey, val certificate: X509Certificate) {
        companion object {
            fun fromDer(pkcs8Key: ByteArray, certificateDer: ByteArray): Signer {
                val cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(ByteArrayInputStream(certificateDer)) as X509Certificate
                val key = KeyFactory.getInstance(cert.publicKey.algorithm)
                    .generatePrivate(PKCS8EncodedKeySpec(pkcs8Key))
                return Signer(key, cert)
            }
        }
    }

    /**
     * @param template байты шаблонного APK (модуль :watchface).
     * @param background JPEG 450x450.
     * @param preview PNG-превью для списка циферблатов на часах (или null — оставить из шаблона).
     * @param workDir каталог для временных файлов.
     */
    fun build(
        template: ByteArray,
        watchFaceXml: String,
        background: ByteArray,
        preview: ByteArray?,
        signer: Signer,
        workDir: File,
    ): ByteArray {
        workDir.mkdirs()
        val templateFile = File.createTempFile("wf_template", ".apk", workDir)
        val unsignedFile = File.createTempFile("wf_unsigned", ".apk", workDir)
        val signedFile = File.createTempFile("wf_signed", ".apk", workDir)
        try {
            templateFile.writeBytes(template)
            val replacements = buildMap {
                put(ENTRY_WATCHFACE_XML, watchFaceXml.toByteArray(Charsets.UTF_8))
                put(ENTRY_BACKGROUND, background)
                if (preview != null) put(ENTRY_PREVIEW, preview)
            }
            val found = mutableSetOf<String>()
            ZipFile(templateFile).use { zip ->
                ZipOutputStream(unsignedFile.outputStream().buffered()).use { out ->
                    for (entry in zip.entries()) {
                        val name = entry.name
                        // Старую подпись выбрасываем — APK подписывается заново.
                        if (!isAllowed(name) || name.startsWith("META-INF/")) continue
                        val data = replacements[name]?.also { found += name }
                            ?: zip.getInputStream(entry).use { it.readBytes() }
                        out.putNextEntry(newEntry(name, data, entry.method))
                        out.write(data)
                        out.closeEntry()
                    }
                }
            }
            val missing = replacements.keys - found
            check(missing.isEmpty()) { "В шаблоне циферблата нет файлов: $missing" }

            val config = ApkSigner.SignerConfig.Builder(
                "watchface", KeyConfig.Jca(signer.privateKey), listOf(signer.certificate),
            ).build()
            ApkSigner.Builder(listOf(config))
                .setInputApk(unsignedFile)
                .setOutputApk(signedFile)
                .setMinSdkVersion(33)
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build()
                .sign()
            return signedFile.readBytes()
        } finally {
            templateFile.delete()
            unsignedFile.delete()
            signedFile.delete()
        }
    }

    private fun newEntry(name: String, data: ByteArray, method: Int): ZipEntry =
        ZipEntry(name).apply {
            // Фиксированное время — одинаковые темы дают одинаковые APK.
            time = 1_577_836_800_000L
            if (method == ZipEntry.STORED) {
                // resources.arsc обязан лежать без сжатия; выравнивание сделает apksig.
                this.method = ZipEntry.STORED
                size = data.size.toLong()
                compressedSize = data.size.toLong()
                crc = CRC32().apply { update(data) }.value
            } else {
                this.method = ZipEntry.DEFLATED
            }
        }
}
