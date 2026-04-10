package itdelatrisu.opsu.beatmap

class LRUCache<K, V>(private val capacity: Int) : LinkedHashMap<K, V>(capacity + 1, 1.1f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean {
        if (size > capacity) { eldestRemoved(eldest); return true }
        return false
    }
    open fun eldestRemoved(eldest: MutableMap.MutableEntry<K, V>) {}
}
