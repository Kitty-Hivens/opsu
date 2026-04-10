/*
 * opsu! - an open-source osu! client
 * Copyright (C) 2014-2017 Jeffrey Han
 *
 * opsu! is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * opsu! is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with opsu!.  If not, see <http://www.gnu.org/licenses/>.
 */
package itdelatrisu.opsu.beatmap

class BeatmapSet(private val maps: ArrayList<Beatmap>) : Iterable<Beatmap> {
	private var favorite = false

	// --- access ---

	operator fun get(index: Int): Beatmap = maps[index]
	fun size(): Int = maps.size
	override fun iterator(): Iterator<Beatmap> = maps.iterator()

	fun sortDifficulties() = maps.sortWith(compareBy({ it.starRating }, { it.version }))

	// --- favorite ---

	fun isFavorite() = favorite
	fun setFavorite(v: Boolean) { favorite = v }
	fun toggleFavorite() { favorite = !favorite }

	// --- display helpers ---

	fun getArtistString(): String    = maps[0].artist.let { if (maps[0].artistUnicode.isNullOrBlank()) it else "${maps[0].artistUnicode} ($it)" }
	fun getTitleString(): String     = maps[0].title.let { if (maps[0].titleUnicode.isNullOrBlank()) it else "${maps[0].titleUnicode} ($it)" }

	/** True if the search term matches this set. */
	fun matches(term: String): Boolean {
		val low = term.lowercase()
		return maps.any { b ->
			b.title.lowercase().contains(low) ||
				b.titleUnicode?.lowercase()?.contains(low) == true ||
			b.artist.lowercase().contains(low) ||
			b.artistUnicode?.lowercase()?.contains(low) == true ||
			b.creator.lowercase().contains(low) ||
			b.source?.lowercase()?.contains(low) == true ||
			b.tags?.lowercase()?.contains(low) == true
		}
	}

	/** True if the search returns no result (map filtered out). */
	fun filteredOut(term: String): Boolean = !matches(term)

	override fun toString(): String = "${maps[0].artist} - ${maps[0].title} [${maps.size} diff(s)]"
}
