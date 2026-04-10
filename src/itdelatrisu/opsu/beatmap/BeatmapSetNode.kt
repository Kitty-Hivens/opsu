package itdelatrisu.opsu.beatmap

import itdelatrisu.opsu.GameImage
import itdelatrisu.opsu.Options
import itdelatrisu.opsu.UI
import itdelatrisu.opsu.Utils
import org.newdawn.slick.Color
import org.newdawn.slick.Graphics

class BeatmapSetNode(val beatmapSet: BeatmapSet) {
	var prev: BeatmapSetNode? = null
	var next: BeatmapSetNode? = null

	var index: Int = 0          // position in the list
	var beatmapIndex: Int = -1  // selected difficulty index, -1 = collapsed

	fun getBeatmapSet(): BeatmapSet = beatmapSet

	val isExpanded get() = beatmapIndex >= 0

	// -------------------------------------------------------------------------
	// Drawing
	// -------------------------------------------------------------------------

	companion object {
		private val BUTTON_BASE_COLOR    = Color(0f, 0f, 0f, 0.45f)
		private val BUTTON_SELECT_COLOR  = Color(0.4f, 0.4f, 0.6f, 0.7f)
		private val BUTTON_HOVER_COLOR   = Color(0.2f, 0.2f, 0.4f, 0.5f)
		private val BUTTON_EXPAND_COLOR  = Color(0.1f, 0.1f, 0.3f, 0.5f)
	}

	/**
	 * Draws the node button.
	 * @param g         graphics context
	 * @param position  y-position on screen
	 * @param selected  whether this is the currently selected node
	 * @param isHover   whether the cursor is hovering
	 */
	fun draw(g: Graphics, position: Float, selected: Boolean, isHover: Boolean) {
		val map = when {
			beatmapIndex >= 0 && beatmapIndex < beatmapSet.size() -> beatmapSet[beatmapIndex]
            else                                                   -> beatmapSet[0]
		}

		val buttonWidth  = Utils.BUTTON_WIDTH
		val buttonHeight = Utils.BUTTON_HEIGHT
		val buttonX      = Utils.BUTTON_X
		val bgAlpha      = Options.getBackgroundAlpha()

		// shadow / base
		val color = when {
			selected -> BUTTON_SELECT_COLOR
			isHover  -> BUTTON_HOVER_COLOR
			isExpanded -> BUTTON_EXPAND_COLOR
            else     -> BUTTON_BASE_COLOR
		}

		g.color = color
		g.fillRoundRect(buttonX, position, buttonWidth, buttonHeight, 4)

		// thumbnail
		val thumb = GameImage.MENU_BUTTON_BG.getScaledImage(buttonWidth, buttonHeight)
		thumb?.draw(buttonX, position)

		// text
		val titleColor  = if (selected) Color.yellow else Color.white
		val artistColor = Color(0.9f, 0.9f, 0.9f, 1f)

		UI.drawString(g,
		if (Options.useUnicodeMetadata()) map.titleUnicode?.takeIf { it.isNotBlank() } ?: map.title else map.title,
			buttonX + 10, position + 8, titleColor)
		UI.drawString(g,
			buildString {
			if (Options.useUnicodeMetadata()) map.artistUnicode?.takeIf { it.isNotBlank() }?.let { append(it) } ?: append(map.artist)
                else append(map.artist)
			append(" // "); append(map.creator)
		},
		buttonX + 10, position + 28, artistColor)
	}
}
