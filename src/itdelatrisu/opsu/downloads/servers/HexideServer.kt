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
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder

/** Download server: https://osu.hexide.com/ */
class HexideServer : DownloadServer() {
    private var _totalResults = -1
    private val pageLimit = 20
    private val apiFields = "maps.ranked_id;maps.title;maps.date;metadata.m_title;metadata.m_artist;metadata.m_creator"

    override fun getName() = "Hexide"
    override fun minQueryLength() = 0
    override fun totalResults() = _totalResults
    override fun disableSSLInDownloads() = true
    override fun getDownloadURL(beatmapSetID: Int) =
        "https://osu.hexide.com/beatmaps/$beatmapSetID/download/$beatmapSetID.osz"

    override fun resultList(query: String, page: Int, rankedOnly: Boolean): Array<DownloadNode>? {
        return try {
            Utils.setSSLCertValidation(false)
            val resultIndex = (page - 1) * pageLimit
            val base = "https://osu.hexide.com/search/$apiFields"
            val url = if (query.isEmpty())
                "$base/maps.size.gt.0/order.date.desc/limit.$resultIndex.${pageLimit + 1}"
            else
                "$base/maps.title.like.${URLEncoder.encode(query, Charsets.UTF_8)}/order.date.desc/limit.$resultIndex.${pageLimit + 1}"

            val arr = try {
                Utils.readJsonArrayFromUrl(URL(url))
            } catch (e: IOException) {
                // a valid search with no results still throws an exception (?)
                _totalResults = 0
                return arrayOf()
            } ?: run { _totalResults = -1; return null }

            val count = minOf(arr.length(), pageLimit)
            Array(count) { i ->
                arr.getJSONObject(i).run {
                    val (title, artist, creator) = if (has("versions")) {
                        getJSONArray("versions").getJSONObject(0).run {
                            Triple(getString("m_title"), getString("m_artist"), getString("m_creator"))
                        }
                    } else {
                        // "versions" is sometimes missing (?)
                        val str = getString("title")
                        val idx = str.indexOf(" - ")
                        if (idx > -1) Triple(str.substring(0, idx), str.substring(idx + 3), "?")
                        else Triple(str, "?", "?")
                    }
                    DownloadNode(getInt("ranked_id"), getString("date"), title, null, artist, null, creator)
                }
            }.also { _totalResults = arr.length() + resultIndex }
        } catch (e: MalformedURLException) {
            ErrorHandler.error("Problem loading result list for query '$query'.", e, true); null
        } catch (e: JSONException) {
            Log.error(e); null
        } finally {
            Utils.setSSLCertValidation(true)
        }
    }
}
