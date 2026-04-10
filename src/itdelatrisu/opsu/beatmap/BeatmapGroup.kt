package itdelatrisu.opsu.beatmap

import itdelatrisu.opsu.GameImage
import itdelatrisu.opsu.Options
import org.newdawn.slick.Image

enum class BeatmapGroup(val displayName: String, val menuIcon: GameImage?) {
	ALL        ("All Maps",         null),
	FAVORITE   ("Favorites",        GameImage.MENU_ICON_HEART),
	PLAYED     ("Played",           GameImage.MENU_ICON_PLAY),
	UNPLAYED   ("Unplayed",         GameImage.MENU_ICON_UNPLAYED),
	RANKED     ("Ranked & Approved",GameImage.MENU_ICON_RANKED),
	UNRANKED   ("Unranked",         GameImage.MENU_ICON_UNRANKED),
	COLLECTION ("Collections",      GameImage.MENU_ICON_COLLECTION),
	RECENT     ("Recently Played",  GameImage.MENU_ICON_RECENT);

	fun contains(set: BeatmapSet): Boolean = when (this) {
		ALL        -> true
		FAVORITE   -> set.isFavorite()
		PLAYED     -> set.any { it.playCount > 0 }
		UNPLAYED   -> set.all { it.playCount == 0 }
		RANKED     -> set.any { it.ranked > 0 }
		UNRANKED   -> set.all { it.ranked <= 0 }
		COLLECTION -> Options.isCollectionFilter() && set.any { Options.getCollection()?.contains(it) == true }
		RECENT     -> set.any { it.lastPlayed > 0 }
	}

	override fun toString() = displayName

	companion object {
		private var current = ALL
		@JvmStatic fun current() = current
		@JvmStatic fun set(g: BeatmapGroup) { current = g }
	}
}
