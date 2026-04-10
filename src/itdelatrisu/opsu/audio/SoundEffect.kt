package itdelatrisu.opsu.audio

enum class SoundEffect(private val filename: String) : SoundController.SoundComponent {
    APPLAUSE      ("applause"),
    COMBOBREAK    ("combobreak"),
    COUNT1        ("count1s"),
    COUNT2        ("count2s"),
    COUNT3        ("count3s"),
    FAIL          ("failsound"),
    GO            ("gos"),
    MENUBACK      ("menuback"),
    MENUCLICK     ("menuclick"),
    MENUHIT       ("menuhit"),
    READY         ("readys"),
    SECTIONFAIL   ("sectionfail"),
    SECTIONPASS   ("sectionpass"),
    SHUTTER       ("shutter"),
    SPINNERBONUS  ("spinnerbonus"),
    SPINNEROSU    ("spinner-osu"),
    SPINNERSPIN   ("spinnerspin");

    fun getFileName(): String = filename

    override fun getClip(): MultiClip? = clip

    fun setClip(clip: MultiClip?) { this.clip = clip }

    private var clip: MultiClip? = null

    companion object {
        @JvmField val SIZE: Int = values().size
    }
}
