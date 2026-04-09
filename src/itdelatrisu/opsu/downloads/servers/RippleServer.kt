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

/** Download server: https://ripple.moe/ */
class RippleServer : DownloadServer() {
    private var _totalResults = -1
    private val pageLimit = 20

    override fun getName() = "Ripple"
    override fun minQueryLength() = 3
    override fun totalResults() = _totalResults
    override fun disableSSLInDownloads() = true
    override fun getDownloadURL(beatmapSetID: Int) = "https://storage.ripple.moe/d/$beatmapSetID"

    override fun resultList(query: String, page: Int, rankedOnly: Boolean): Array<DownloadNode>? {
        return try {
            Utils.setSSLCertValidation(false)
            val offset = (page - 1) * pageLimit
            val url = "https://storage.ripple.moe/api/search?query=${URLEncoder.encode(query, Charsets.UTF_8)}" +
                "&mode=0&amount=$pageLimit&offset=$offset${if (rankedOnly) "&status=1" else ""}"
            val arr = Utils.readJsonArrayFromUrl(URL(url)) ?: run { _totalResults = -1; return null }
            Array(arr.length()) { i ->
                arr.getJSONObject(i).run {
                    DownloadNode(getInt("SetID"), formatDate(getString("LastUpdate")),
                        getString("Title"), null, getString("Artist"), null, getString("Creator"))
                }
            }.also { nodes ->
                var count = nodes.size + offset
                if (nodes.size == pageLimit) count++
                _totalResults = count
            }
        } catch (e: MalformedURLException) {
            ErrorHandler.error("Problem loading result list for query '$query'.", e, true); null
        } catch (e: JSONException) {
            Log.error(e); null
        } finally {
            Utils.setSSLCertValidation(true)
        }
    }

    private fun formatDate(s: String) = try {
        val d = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(s)
        SimpleDateFormat("d MMM yyyy HH:mm:ss").format(d)
    } catch (e: Exception) { s }
}
