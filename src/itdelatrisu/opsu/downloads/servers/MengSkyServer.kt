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

/** Download server: http://osu.mengsky.net/ (offline as of September 2017) */
class MengSkyServer : DownloadServer() {
    private var _totalResults = -1
    private val pageLimit = 20

    override fun getName() = "MengSky"
    override fun minQueryLength() = 1
    override fun totalResults() = _totalResults
    override fun getDownloadURL(beatmapSetID: Int) = "http://osu.mengsky.net/api/download/$beatmapSetID"
    override fun getDownloadRequestHeaders() = mapOf("Referer" to "http://osu.mengsky.net/")

    override fun resultList(query: String, page: Int, rankedOnly: Boolean): Array<DownloadNode>? {
        return try {
            val flag = if (rankedOnly) 0 else 1
            val url = "http://osu.mengsky.net/api/beatmapinfo" +
                "?query=${URLEncoder.encode(query, Charsets.UTF_8)}&page=$page" +
                "&ranked=1&unrank=$flag&approved=$flag&qualified=$flag"
            val json = Utils.readJsonObjectFromUrl(URL(url)) ?: run { _totalResults = -1; return null }
            val arr = json.getJSONArray("data")
            Array(arr.length()) { i ->
                arr.getJSONObject(i).run {
                    val title = getString("title")
                    val artist = getString("artist")
                    val artistU = getString("artistU")
                    var titleU = getString("titleU")
                    // bug with v1.x API: sometimes titleU is artistU instead of the proper title
                    if (titleU == artistU && titleU != title) titleU = title
                    DownloadNode(getInt("id"), getString("syncedDateTime"), title, titleU, artist, artistU, getString("creator"))
                }
            }.also { nodes ->
                val pageTotal = json.getInt("pageTotal")
                _totalResults = if (page == pageTotal) nodes.size + (pageTotal - 1) * pageLimit
                                 else 1 + (pageTotal - 1) * pageLimit
            }
        } catch (e: MalformedURLException) {
            ErrorHandler.error("Problem loading result list for query '$query'.", e, true); null
        } catch (e: JSONException) {
            Log.error(e); null
        }
    }
}
