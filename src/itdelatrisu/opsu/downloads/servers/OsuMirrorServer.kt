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
import java.text.SimpleDateFormat
import java.util.TimeZone

/** Download server: http://loli.al/ (went offline August 2015) */
class OsuMirrorServer : DownloadServer() {
    private var _totalResults = -1
    private var maxServerID = 0
    private val idTable = HashMap<Int, Int>()

    override fun getName() = "osu!Mirror"
    override fun minQueryLength() = 3
    override fun totalResults() = _totalResults
    override fun getDownloadURL(beatmapSetID: Int) = idTable[beatmapSetID]?.let { "http://loli.al/d/$it/" }

    override fun resultList(query: String, page: Int, rankedOnly: Boolean): Array<DownloadNode>? {
        // NOTE: ignores 'rankedOnly' flag.
        return try {
            val isSearch = query.isNotEmpty()
            val url = if (isSearch)
                "http://loli.al/mirror/search/$page.json?keyword=${URLEncoder.encode(query, Charsets.UTF_8)}"
            else
                "http://loli.al/mirror/home/$page.json"
            val json = Utils.readJsonObjectFromUrl(URL(url))
            if (json == null || json.getInt("code") != 0) { _totalResults = -1; return null }
            val arr = json.getJSONArray("maplist")
            Array(arr.length()) { i ->
                arr.getJSONObject(i).run {
                    val beatmapSetID = getInt("OSUSetid")
                    val serverID = getInt("id")
                    idTable[beatmapSetID] = serverID
                    if (serverID > maxServerID) maxServerID = serverID
                    DownloadNode(beatmapSetID, formatDate(getString("ModifyDate")),
                        getString("Title"), null, getString("Artist"), null, getString("Mapper"))
                }
            }.also { _totalResults = if (isSearch) json.getInt("totalRows") else maxServerID }
        } catch (e: MalformedURLException) {
            ErrorHandler.error("Problem loading result list for query '$query'.", e, true); null
        } catch (e: JSONException) {
            Log.error(e); null
        }
    }

    private fun formatDate(s: String) = try {
        val d = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(s)
        SimpleDateFormat("d MMM yyyy HH:mm:ss").format(d)
    } catch (e: Exception) { s }
}
