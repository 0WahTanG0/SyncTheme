package app.synctheme.mobile.link

import android.content.Context
import android.util.Base64
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Что за устройство на том конце: от этого зависят значок, превью и что оно делает с темой. */
enum class DeviceKind(val title: String) {
    PHONE("Телефон"), TABLET("Планшет"), TV("Телевизор");

    companion object {
        fun of(name: String?): DeviceKind = entries.firstOrNull { it.name == name } ?: TABLET
    }
}

/**
 * Связанное устройство: постоянный [id], имя, тип, общий секрет и где его видели в сети в последний раз.
 * [enabled] — получает ли оно тему по «Применить».
 */
data class Peer(
    val id: String,
    val name: String,
    val kind: DeviceKind,
    val secret: ByteArray,
    val service: String? = null,
    val host: String? = null,
    val port: Int = 0,
    val enabled: Boolean = true,
) {
    override fun equals(other: Any?) = other is Peer && other.id == id && other.name == name && other.kind == kind &&
        other.secret.contentEquals(secret) && other.service == service && other.host == host && other.port == port && other.enabled == enabled

    override fun hashCode() = id.hashCode() * 31 + secret.contentHashCode()
}

/**
 * Как прошла последняя отправка на устройство: [ok] — тема на нём; иначе [text] — что не так,
 * а [pending] — телефон сам повторит, когда устройство появится в сети.
 */
data class LinkPush(val ok: Boolean, val text: String, val at: Long, val pending: Boolean = false)

/**
 * Связи между устройствами. На телефоне — список устройств, которым уходит тема; на устройстве —
 * телефон, от которого она приходит, и включён ли приём.
 */
object LinkStore {
    private fun prefs(context: Context) = context.getSharedPreferences("tablet_link", Context.MODE_PRIVATE)

    /** Постоянный id этого устройства: по нему телефон узнаёт его и после смены имени или адреса. */
    fun id(context: Context): String {
        val p = prefs(context)
        return p.getString("device_id", null) ?: UUID.randomUUID().toString().take(12).also { p.edit().putString("device_id", it).apply() }
    }

    // ---------------------------------------------------------------- телефон: устройства

    private val peersFlow = MutableStateFlow<List<Peer>>(emptyList())
    @Volatile private var peersLoaded = false

    fun peers(context: Context): List<Peer> {
        if (!peersLoaded) {
            peersLoaded = true
            peersFlow.value = load(context)
        }
        return peersFlow.value
    }

    /** Список устройств — для экрана; меняется при связывании, отключении и отправке. */
    fun observePeers(context: Context): StateFlow<List<Peer>> {
        peers(context)
        return peersFlow.asStateFlow()
    }

    /** Перечитать из настроек при следующем обращении (в тестах у каждого приложения свои настройки). */
    internal fun reload() {
        peersLoaded = false
        pushLoaded = false
    }

    fun peer(context: Context, id: String): Peer? = peers(context).firstOrNull { it.id == id }

    /** Новое устройство или то же заново (по id) — заменяет прежнюю запись. */
    fun savePeer(context: Context, peer: Peer) = store(context, listOf(peer) + peers(context).filterNot { it.id == peer.id })

    /** Обновить сведения об устройстве (адрес, включено ли), не меняя порядок списка. */
    fun updatePeer(context: Context, peer: Peer) = store(context, peers(context).map { if (it.id == peer.id) peer else it })

    fun removePeer(context: Context, id: String) {
        store(context, peers(context).filterNot { it.id == id })
        setPush(context, id, null)
    }

    private fun store(context: Context, list: List<Peer>) {
        peersLoaded = true
        peersFlow.value = list
        val json = JSONArray(list.map { p ->
            JSONObject().put("id", p.id).put("name", p.name).put("kind", p.kind.name)
                .put("secret", Base64.encodeToString(p.secret, Base64.NO_WRAP))
                .put("service", p.service).put("host", p.host).put("port", p.port).put("enabled", p.enabled)
        })
        prefs(context).edit().putString("peers", json.toString()).apply()
    }

    private fun load(context: Context): List<Peer> {
        val p = prefs(context)
        val list = p.getString("peers", null)?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    Peer(
                        o.getString("id"), o.getString("name"), DeviceKind.of(o.optString("kind")),
                        Base64.decode(o.getString("secret"), Base64.NO_WRAP),
                        o.optString("service").ifEmpty { null }, o.optString("host").ifEmpty { null },
                        o.optInt("port"), o.optBoolean("enabled", true),
                    )
                }
            }.getOrNull()
        }
        if (list != null) return list
        // Версии до 1.17 хранили один планшет — переносим его, связывать заново не нужно.
        val name = p.getString("tablet_name", null) ?: return emptyList()
        val secret = p.getString("tablet_secret", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return emptyList()
        return listOf(Peer("legacy-tablet", name, DeviceKind.TABLET, secret, p.getString("tablet_service", null), p.getString("tablet_host", null), p.getInt("tablet_port", 0)))
    }

    // ---------------------------------------------------------------- телефон: как прошла отправка

    private val pushFlow = MutableStateFlow<Map<String, LinkPush>>(emptyMap())
    @Volatile private var pushLoaded = false

    /** Последняя отправка на каждое устройство (по id) — для строки устройства и шапки. */
    fun push(context: Context): StateFlow<Map<String, LinkPush>> {
        if (!pushLoaded) {
            pushLoaded = true
            pushFlow.value = runCatching {
                val o = JSONObject(prefs(context).getString("push", "{}")!!)
                o.keys().asSequence().associateWith { k ->
                    val s = o.getJSONObject(k)
                    LinkPush(s.getBoolean("ok"), s.getString("text"), s.getLong("at"), s.optBoolean("pending"))
                }
            }.getOrDefault(emptyMap())
        }
        return pushFlow.asStateFlow()
    }

    fun setPush(context: Context, id: String, status: LinkPush?) {
        push(context)
        pushFlow.value = if (status == null) pushFlow.value - id else pushFlow.value + (id to status)
        val o = JSONObject()
        pushFlow.value.forEach { (k, s) -> o.put(k, JSONObject().put("ok", s.ok).put("text", s.text).put("at", s.at).put("pending", s.pending)) }
        prefs(context).edit().putString("push", o.toString()).apply()
    }

    // ---------------------------------------------------------------- устройство: телефон и приём

    /** Устройство: телефон, от которого принимается тема. */
    fun phone(context: Context): Peer? {
        val p = prefs(context)
        val name = p.getString("phone_name", null) ?: return null
        val secret = p.getString("phone_secret", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        return Peer(p.getString("phone_id", null) ?: "phone", name, DeviceKind.PHONE, secret)
    }

    fun setPhone(context: Context, peer: Peer?) {
        val e = prefs(context).edit()
        if (peer == null) listOf("phone_name", "phone_secret", "phone_id").forEach(e::remove)
        else e.putString("phone_name", peer.name).putString("phone_id", peer.id)
            .putString("phone_secret", Base64.encodeToString(peer.secret, Base64.NO_WRAP))
        e.apply()
    }

    /** Принимать тему с телефона (служба в фоне). */
    fun receiving(context: Context): Boolean = prefs(context).getBoolean("receiving", false)
    fun setReceiving(context: Context, on: Boolean) = prefs(context).edit().putBoolean("receiving", on).apply()
}
