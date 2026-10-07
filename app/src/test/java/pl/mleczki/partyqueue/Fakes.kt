package pl.mleczki.partyqueue

import android.content.SharedPreferences

/** In-memory SharedPreferences so the controller can be tested on the JVM. */
class FakePrefs : SharedPreferences {
    private val data = HashMap<String, Any?>()

    override fun getAll(): MutableMap<String, *> = HashMap(data)
    override fun getString(key: String, defValue: String?) = data[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String, defValue: Int) = data[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = data[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float) = data[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = data[key] as? Boolean ?: defValue
    override fun contains(key: String) = key in data
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { pending[key] = null }
        override fun clear() = apply { data.clear() }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            pending.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }
            pending.clear()
        }
    }
}

class FakePlayer : PlayerPort {
    val loads = mutableListOf<String>()
    val commands = mutableListOf<Pair<String, Double>>()

    override fun load(videoId: String) { loads += videoId }
    override fun command(cmd: String, arg: Double) { commands += cmd to arg }
}

/** Serves canned metadata; ids are "v" + 10 characters so they pass validation. */
class FakeSource(private val playlist: List<Meta> = emptyList()) : MetaSource {
    val known = HashMap<String, Meta>()

    fun add(m: Meta): Meta = m.also { known[it.videoId] = it }

    override suspend fun search(query: String): List<Meta> = known.values.filter { query.lowercase() in it.title.lowercase() }
    override suspend fun playlist(urlOrId: String): Pair<String, List<Meta>> = "Test playlist" to playlist
    override suspend fun meta(videoId: String): Meta =
        known[videoId] ?: throw IllegalArgumentException("Nie znaleziono filmu")
}

fun meta(n: Int) = Meta("v%010d".format(n), "Song $n", "Artist $n", "3:%02d".format(n % 60))
