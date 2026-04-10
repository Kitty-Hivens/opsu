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

package itdelatrisu.opsu.downloads

import itdelatrisu.opsu.OpsuConstants

class DownloadList private constructor() {

    private val nodes = mutableListOf<DownloadNode>()
    private val map   = mutableMapOf<Int, DownloadNode>()

    fun getNode(index: Int): DownloadNode? = nodes.getOrNull(index)
    fun getDownload(beatmapSetID: Int): Download? = map[beatmapSetID]?.download
    fun size()  = nodes.size
    fun isEmpty() = nodes.isEmpty()
    fun contains(beatmapSetID: Int) = map.containsKey(beatmapSetID)

    fun addNode(node: DownloadNode) {
        nodes.add(node)
        map[node.id] = node
    }

    fun remove(node: DownloadNode) = remove(nodes.indexOf(node))

    fun remove(index: Int) {
        if (index < 0 || index >= nodes.size) return
        val node = nodes.removeAt(index)
        map.remove(node.id)
    }

    fun hasActiveDownloads() = nodes.any { it.download?.isActive == true }

    fun cancelAllDownloads() = nodes.forEach {
        it.download?.takeIf { dl -> dl.isActive }?.cancel()
    }

    fun clearInactiveDownloads() {
        val iter = nodes.iterator()
        while (iter.hasNext()) {
            val node = iter.next()
            val dl = node.download
            if (dl != null && !dl.isActive) {
                node.clearDownload()
                iter.remove()
                map.remove(node.id)
            }
        }
    }

    fun clearDownloads(status: Download.Status) {
        val iter = nodes.iterator()
        while (iter.hasNext()) {
            val node = iter.next()
            val dl = node.download
            if (dl != null && dl.status == status) {
                node.clearDownload()
                iter.remove()
                map.remove(node.id)
            }
        }
    }

    companion object {
        const val EXIT_CONFIRMATION = "Beatmap downloads are in progress.\nAre you sure you want to quit ${OpsuConstants.PROJECT_NAME}?"

        private val list = DownloadList()

        @JvmStatic fun get() = list
    }
}
