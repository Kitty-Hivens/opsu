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

import itdelatrisu.opsu.downloads.DownloadNode
import java.io.IOException

/** Abstract class for beatmap download servers. */
abstract class DownloadServer {
    abstract fun getName(): String
    abstract fun getDownloadURL(beatmapSetID: Int): String?

    @Throws(IOException::class)
    abstract fun resultList(query: String, page: Int, rankedOnly: Boolean): Array<DownloadNode>?

    abstract fun minQueryLength(): Int
    abstract fun totalResults(): Int

    fun getPreviewURL(beatmapSetID: Int) = "http://b.ppy.sh/preview/$beatmapSetID.mp3"
    open fun getDownloadRequestHeaders(): Map<String, String>? = null
    open fun isDownloadInBrowser() = false
    open fun disableSSLInDownloads() = false

    override fun toString() = getName()
}
