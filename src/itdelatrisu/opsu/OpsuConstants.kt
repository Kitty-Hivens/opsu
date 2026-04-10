package itdelatrisu.opsu

import java.net.URI
import java.net.URLEncoder

object OpsuConstants {
    const val PROJECT_NAME   = "opsu!"
    const val PROJECT_AUTHOR = "@itdelatrisu"

    val WEBSITE_URI    = URI.create("https://itdelatrisu.github.io/opsu/")
    val REPOSITORY_URI = URI.create("https://github.com/itdelatrisu/opsu")
    val CREDITS_URI    = URI.create("https://github.com/itdelatrisu/opsu/blob/master/CREDITS.md")

    const val ISSUES_URL     = "https://github.com/itdelatrisu/opsu/issues/new?title=%s&body=%s"
    const val VERSION_REMOTE = "https://raw.githubusercontent.com/itdelatrisu/opsu/gh-pages/version"

    private const val CHANGELOG_URL = "https://github.com/itdelatrisu/opsu/releases/tag/%s"

    fun getChangelogURI(version: String): URI = runCatching {
        URI.create(CHANGELOG_URL.format(URLEncoder.encode(version, "UTF-8")))
    }.getOrDefault(WEBSITE_URI)
}
