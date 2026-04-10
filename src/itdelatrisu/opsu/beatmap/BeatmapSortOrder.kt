package itdelatrisu.opsu.beatmap

enum class BeatmapSortOrder(val displayName: String, private val cmp: Comparator<BeatmapSetNode>) {
    TITLE  ("Title",       compareBy { it.getBeatmapSet().get(0).title.lowercase() }),
    ARTIST ("Artist",      compareBy { it.getBeatmapSet().get(0).artist.lowercase() }),
    CREATOR("Creator",     compareBy { it.getBeatmapSet().get(0).creator.lowercase() }),
    BPM    ("BPM",         compareBy { it.getBeatmapSet().get(0).bpmMax }),
    LENGTH ("Length",      compareBy { it.getBeatmapSet().maxOf { b -> b.endTime } }),
    DATE   ("Date Added",  compareBy { it.getBeatmapSet().maxOf { b -> b.dateAdded } }),
    PLAYS  ("Most Played", compareBy { it.getBeatmapSet().sumOf { b -> b.playCount } });

    fun getComparator(): Comparator<BeatmapSetNode> = cmp

    companion object {
        private var current = TITLE
        @JvmStatic fun current() = current
        @JvmStatic fun set(s: BeatmapSortOrder) { current = s }
    }

    override fun toString() = displayName
}
