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
import java.util.Date

/** Download server: http://osu.yas-online.net/ (no longer hosts downloads as of March 2017) */
class YaSOnlineServer : DownloadServer() {
    private var _totalResults = -1
    private var maxServerID = 0
    private val pageLimit = 25

    override fun getName() = "YaS Online"
    override fun minQueryLength() = 3
    override fun totalResults() = _totalResults

    override fun getDownloadURL(beatmapSetID: Int) = try {
        getDownloadURLFromMapData(beatmapSetID)
    } catch (e: Exception) { null }

    private fun getDownloadURLFromMapData(beatmapSetID: Int): String? {
        return try {
            Utils.setSSLCertValidation(false)
            val json = Utils.readJsonObjectFromUrl(URL("https://osu.yas-online.net/json.mapdata.php?mapId=$beatmapSetID"))
            if (json == null || json.getString("result") != "success") return null
            val results = json.getJSONObject("success")
            if (results.length() == 0) return null
            val key = results.keys().next() as String
            "https://osu.yas-online.net${results.getJSONObject(key).getString("downloadLink")}"
        } catch (e: MalformedURLException) {
            ErrorHandler.error("Problem retrieving download URL for beatmap '$beatmapSetID'.", e, true); null
        } finally {
            Utils.setSSLCertValidation(true)
        }
    }

    override fun resultList(query: String, page: Int, rankedOnly: Boolean): Array<DownloadNode>? {
        return try {
            Utils.setSSLCertValidation(false)
            val isSearch = query.isNotEmpty()
            val url = if (isSearch)
                "https://osu.yas-online.net/json.search.php?searchQuery=${URLEncoder.encode(query, Charsets.UTF_8)}"
            else
                "https://osu.yas-online.net/json.maplist.php?o=${(page - 1) * pageLimit}"
            val json = Utils.readJsonObjectFromUrl(URL(url)) ?: run { _totalResults = -1; return null }
            if (json.getString("result") != "success") { _totalResults = 0; return arrayOf() }
            val results = json.getJSONObject("success")
            if (results.length() == 0) { _totalResults = 0; return arrayOf() }

            val nodeList = mutableListOf<DownloadNode>()
            val iter = results.keys()
            while (iter.hasNext()) {
                val item = results.getJSONObject(iter.next() as String)
                val str = item.getString("map")
                val idx = str.indexOf(" - ")
                val (title, artist) = if (idx > -1) str.substring(0, idx) to str.substring(idx + 3) else str to "?"
                val added = item.getInt("added")
                val serverID = item.getInt("id")
                if (serverID > maxServerID) maxServerID = serverID
                nodeList.add(DownloadNode(item.getInt("mapid"), if (added == 0) "?" else formatDate(added), title, null, artist, null, ""))
            }
            nodeList.toTypedArray().also { nodes ->
                _totalResults = if (isSearch) nodes.size else maxServerID
            }
        } catch (e: MalformedURLException) {
            ErrorHandler.error("Problem loading result list for query '$query'.", e, true); null
        } catch (e: JSONException) {
            Log.error(e); null
        } finally {
            Utils.setSSLCertValidation(true)
        }
    }

    private fun formatDate(timestamp: Int) =
        SimpleDateFormat("d MMM yyyy HH:mm:ss").format(Date(timestamp * 1000L))
}
