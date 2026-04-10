package itdelatrisu.opsu.replay

data class LifeFrame(val time: Int, val health: Float) {
	companion object {
        const val SAMPLE_INTERVAL = 2000
	}

	override fun toString() = "($time, ${"%.2f".format(health)})"
}
