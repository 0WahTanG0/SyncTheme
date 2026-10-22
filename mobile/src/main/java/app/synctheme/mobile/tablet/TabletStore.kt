package app.synctheme.mobile.tablet

import android.content.Context
import android.util.Base64

/** Устройство на другом конце связи: имя, общий секрет и где его видели в сети в последний раз. */
data class Peer(val name: String, val secret: ByteArray, val service: String? = null, val host: String? = null, val port: Int = 0) {
    override fun equals(other: Any?) = other is Peer && other.name == name && other.secret.contentEquals(secret) &&
        other.service == service && other.host == host && other.port == port

    override fun hashCode() = name.hashCode() * 31 + secret.contentHashCode()
}

/** На телефоне — связанный планшет; на планшете — связанный телефон и включён ли приём. */
object TabletStore {
    private fun prefs(context: Context) = context.getSharedPreferences("tablet_link", Context.MODE_PRIVATE)

    private fun load(context: Context, prefix: String): Peer? {
        val p = prefs(context)
        val name = p.getString("${prefix}_name", null) ?: return null
        val secret = p.getString("${prefix}_secret", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        return Peer(name, secret, p.getString("${prefix}_service", null), p.getString("${prefix}_host", null), p.getInt("${prefix}_port", 0))
    }

    private fun save(context: Context, prefix: String, peer: Peer?) {
        val e = prefs(context).edit()
        if (peer == null) {
            listOf("name", "secret", "service", "host", "port").forEach { e.remove("${prefix}_$it") }
        } else {
            e.putString("${prefix}_name", peer.name)
                .putString("${prefix}_secret", Base64.encodeToString(peer.secret, Base64.NO_WRAP))
                .putString("${prefix}_service", peer.service)
                .putString("${prefix}_host", peer.host)
                .putInt("${prefix}_port", peer.port)
        }
        e.apply()
    }

    /** Телефон: планшет, которому уходит тема. */
    fun tablet(context: Context): Peer? = load(context, "tablet")
    fun setTablet(context: Context, peer: Peer?) = save(context, "tablet", peer)

    /** Планшет: телефон, от которого принимается тема. */
    fun phone(context: Context): Peer? = load(context, "phone")
    fun setPhone(context: Context, peer: Peer?) = save(context, "phone", peer)

    /** Планшет: принимать тему с телефона (служба в фоне). */
    fun receiving(context: Context): Boolean = prefs(context).getBoolean("receiving", false)
    fun setReceiving(context: Context, on: Boolean) = prefs(context).edit().putBoolean("receiving", on).apply()
}
