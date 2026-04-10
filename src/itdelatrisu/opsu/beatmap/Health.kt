package itdelatrisu.opsu.beatmap

import itdelatrisu.opsu.GameData
import itdelatrisu.opsu.Utils

class Health {
    companion object {
        const val HP_MAX = 200f
        private const val HP_50 = 0.4f;  private const val HP_100 = 2.2f
        private const val HP_300 = 6f;   private const val HP_100K = 10f
        private const val HP_300K = 10f; private const val HP_300G = 14f
        private const val HP_MU = 6f;    private const val HP_SLIDER10 = 3f
        private const val HP_SLIDER30 = 4f
        private const val HP_SPINNERSPIN = 1.7f; private const val HP_SPINNERBONUS = 2f
    }

    private var health = HP_MAX
    private var healthDisplay = HP_MAX
    private var healthUncapped = HP_MAX
    private var hpDrainRate = 5f
    private var hpMultiplierNormal = 1f
    private var hpMultiplierComboEnd = 1f

    fun setModifiers(drain: Float, normal: Float, comboEnd: Float) {
        hpDrainRate = drain; hpMultiplierNormal = normal; hpMultiplierComboEnd = comboEnd
    }

    fun update(delta: Int) {
        val m = delta / 32f
        healthDisplay = when {
            healthDisplay < health -> Utils.clamp(healthDisplay + (health - healthDisplay) * m, 0f, health)
            healthDisplay > health -> {
                val mul = if (health < 10f) m * 10f else m
                Utils.clamp(healthDisplay - (healthDisplay - health) * mul, health, HP_MAX)
            }
            else -> healthDisplay
        }
    }

    fun getHealth() = health / HP_MAX * 100f
    fun getHealthDisplay() = healthDisplay / HP_MAX * 100f
    fun getRawHealth() = health
    fun getUncappedRawHealth() = healthUncapped

    fun setHealth(v: Float) { health = v; healthUncapped = v }

    fun changeHealth(v: Float) {
        health = Utils.clamp(health + v, 0f, HP_MAX)
        healthUncapped += v
    }

    fun changeHealthForHit(type: Int) = changeHealth(when (type) {
        GameData.HIT_MISS        -> Utils.mapDifficultyRange(hpDrainRate, -6f, -25f, -40f)
        GameData.HIT_50          -> hpMultiplierNormal  * Utils.mapDifficultyRange(hpDrainRate, HP_50 * 8, HP_50, HP_50)
        GameData.HIT_100         -> hpMultiplierNormal  * Utils.mapDifficultyRange(hpDrainRate, HP_100 * 8, HP_100, HP_100)
        GameData.HIT_300         -> hpMultiplierNormal  * HP_300
        GameData.HIT_100K        -> hpMultiplierComboEnd * HP_100K
        GameData.HIT_300K        -> hpMultiplierComboEnd * HP_300K
        GameData.HIT_300G        -> hpMultiplierComboEnd * HP_300G
        GameData.HIT_MU          -> hpMultiplierNormal  * HP_MU
        GameData.HIT_SLIDER10    -> hpMultiplierNormal  * HP_SLIDER10
        GameData.HIT_SLIDER30    -> hpMultiplierNormal  * HP_SLIDER30
        GameData.HIT_SPINNERSPIN  -> hpMultiplierNormal * HP_SPINNERSPIN
        GameData.HIT_SPINNERBONUS -> hpMultiplierNormal * HP_SPINNERBONUS
        else -> 0f
    })

    fun reset() {
        health = HP_MAX; healthDisplay = HP_MAX; healthUncapped = HP_MAX
        hpDrainRate = 5f; hpMultiplierNormal = 1f; hpMultiplierComboEnd = 1f
    }
}
