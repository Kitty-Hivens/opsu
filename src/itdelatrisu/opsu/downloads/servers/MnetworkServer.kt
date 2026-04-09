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

package itdelatrisu.opsu.downloads.servers

import itdelatrisu.opsu.ErrorHandler
import itdelatrisu.opsu.Utils
import itdelatrisu.opsu.downloads.DownloadNode
import org.json.JSONException
import org.newdawn.slick.util.Log
import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

/** Download server: http://osu.uu.gl/ */
class MnetworkServer : DownloadServer() {
    private var _totalResults = -1
    private val beatmapPattern = Pattern.compile("""^(\d+) ([^-]+) - (.+)\.osz$""")

    override fun getName() = "Mnetwork"
    override fun minQueryLength() = 0
    override fun totalResults() = _totalResults
    override fun getDownloadURL(beatmapSetID: Int) = "http://osu.uu.gl/s/$beatmapSetID"

    override fun resultList(query: String, page: Int, rankedOnly: Boolean): Array<DownloadNode>? {
        return try {
            val queryStr = if (query.isEmpty()) "-" else query
            val url = "http://osu.uu.gl/d/${URLEncoder.encode(queryStr, Charsets.UTF_8).replace("+", "%20")}"
            val html = Utils.readDataFromUrl(URL(url)) ?: run { _totalResults = -1; return null }

            // Parse HTML manually — the format is simple enough without a full parser:
            //   <div class="tr_title">
            //   <b><a href='/s/{{id}}'>{{id}} {{artist}} - {{title}}.osz</a></b><br />
            //   BPM: {{bpm}} <b>|</b> Total Time: {{m}}:{{s}}<br/>
            //   Genre: {{genre}} <b>|</b> Updated: {{MMM}} {{d}}, {{yyyy}}<br />
            val startTag = "<div class=\"tr_title\">"
            val hrefTag = "<a href="
            val hrefTagEnd = "</a>"
            val updatedTag = "Updated: "
            val nodeList = mutableListOf<DownloadNode>()
            var index = -1
            var nextIndex = html.indexOf(startTag, index + 1)
            while (nextIndex.also { index = it } != -1) {
                nextIndex = html.indexOf(startTag, index + 1)
                val n = if (nextIndex == -1) html.length else nextIndex

                var i = html.indexOf(hrefTag, index + startTag.length).takeIf { it != -1 && it < n } ?: continue
                i = html.indexOf('>', i + hrefTag.length).takeIf { it != -1 && it < n } ?: continue
                val j1 = html.indexOf(hrefTagEnd, i + 1).takeIf { it != -1 && it <= n } ?: continue
                val beatmap = html.substring(i + 1, j1).trim()

                val i2 = html.indexOf(updatedTag, j1).takeIf { it != -1 && it < n } ?: continue
                val j2 = html.indexOf('<', i2 + updatedTag.length).takeIf { it != -1 && it <= n } ?: continue
                val date = html.substring(i2 + updatedTag.length, j2).trim()

                val m = beatmapPattern.matcher(beatmap)
                if (!m.matches()) continue
                nodeList.add(DownloadNode(m.group(1).toInt(), date, m.group(3), null, m.group(2), null, ""))
            }
            nodeList.toTypedArray().also { _totalResults = it.size }
        } catch (e: MalformedURLException) {
            ErrorHandler.error("Problem loading result list for query '$query'.", e, true); null
        } catch (e: JSONException) {
            Log.error(e); null
        }
    }
}
