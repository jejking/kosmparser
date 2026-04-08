@file:Suppress("TooManyFunctions")

package com.jejking.kosmparser.osm.pbf

import com.google.protobuf.ByteString
import com.jejking.kosmparser.osm.ElementMetadata
import com.jejking.kosmparser.osm.Member
import com.jejking.kosmparser.osm.MemberType
import com.jejking.kosmparser.osm.Node
import com.jejking.kosmparser.osm.OsmData
import com.jejking.kosmparser.osm.Point
import com.jejking.kosmparser.osm.Relation
import com.jejking.kosmparser.osm.Way
import crosby.binary.Osmformat

/**
 * Decodes a [Osmformat.PrimitiveBlock] into a list of [OsmData] domain objects.
 *
 * Dispatches each [Osmformat.PrimitiveGroup] to the appropriate decoder based on content:
 * - `dense` → [DenseNodes] (the most common encoding)
 * - `nodes` → simple [Osmformat.Node] list (rare)
 * - `ways` → [Osmformat.Way] list
 * - `relations` → [Osmformat.Relation] list
 */
fun Osmformat.PrimitiveBlock.toOsmDataList(): List<OsmData> =
    primitivegroupList.flatMap { group ->
        when {
            group.hasDense() -> group.dense.toNodes(this)
            group.nodesList.isNotEmpty() -> group.nodesList.map { it.toNode(this) }
            group.waysList.isNotEmpty() -> group.waysList.map { it.toWay(this) }
            group.relationsList.isNotEmpty() -> group.relationsList.map { it.toRelation(this) }
            else -> emptyList()
        }
    }

private fun Osmformat.DenseNodes.toNodes(block: Osmformat.PrimitiveBlock): List<Node> {
    val ids = decodeDelta(idList)
    val lats = decodeDelta(latList)
    val lons = decodeDelta(lonList)
    val tagsByNode = if (keysValsList.isEmpty()) {
        List(ids.size) { emptyMap<String, String>() }
    } else {
        decodeDenseTags(keysValsList, block.stringtable)
    }
    val metadataByNode = if (hasDenseinfo()) {
        decodeDenseInfo(denseinfo, ids, block)
    } else {
        ids.map { id -> emptyMetadata(id) }
    }

    return ids.indices.map { i ->
        Node(
            elementMetadata = metadataByNode[i],
            tags = tagsByNode[i],
            point = Point(
                lat = decodeCoordinate(lats[i], block.latOffset, block.granularity),
                lon = decodeCoordinate(lons[i], block.lonOffset, block.granularity)
            )
        )
    }
}

private fun decodeDenseInfo(
    denseInfo: Osmformat.DenseInfo,
    ids: List<Long>,
    block: Osmformat.PrimitiveBlock
): List<ElementMetadata> {
    val timestamps = decodeDelta(denseInfo.timestampList)
    val changesets = decodeDelta(denseInfo.changesetList)
    val uids = decodeDelta(denseInfo.uidList.map { it.toLong() })
    val userSids = decodeDelta(denseInfo.userSidList.map { it.toLong() })

    return ids.indices.map { i ->
        ElementMetadata(
            id = ids[i],
            user = block.stringtable.resolveString(userSids[i].toInt()),
            uid = uids[i],
            timestamp = decodeTimestamp(timestamps[i], block.dateGranularity),
            visible = if (denseInfo.visibleList.isNotEmpty()) denseInfo.visibleList[i] else true,
            version = denseInfo.versionList[i].toLong(),
            changeSet = changesets[i]
        )
    }
}

private fun Osmformat.Node.toNode(block: Osmformat.PrimitiveBlock): Node =
    Node(
        elementMetadata = if (hasInfo()) info.toMetadata(id, block) else emptyMetadata(id),
        tags = resolveTags(keysList, valsList, block.stringtable),
        point = Point(
            lat = decodeCoordinate(lat, block.latOffset, block.granularity),
            lon = decodeCoordinate(lon, block.lonOffset, block.granularity)
        )
    )

private fun Osmformat.Way.toWay(block: Osmformat.PrimitiveBlock): Way =
    Way(
        elementMetadata = if (hasInfo()) info.toMetadata(id, block) else emptyMetadata(id),
        tags = resolveTags(keysList, valsList, block.stringtable),
        nds = decodeDelta(refsList)
    )

private fun Osmformat.Relation.toRelation(block: Osmformat.PrimitiveBlock): Relation =
    Relation(
        elementMetadata = if (hasInfo()) info.toMetadata(id, block) else emptyMetadata(id),
        tags = resolveTags(keysList, valsList, block.stringtable),
        members = decodeMembers(rolesSidList, memidsList, typesList, block.stringtable)
    )

private fun Osmformat.Info.toMetadata(elementId: Long, block: Osmformat.PrimitiveBlock) =
    ElementMetadata(
        id = elementId,
        user = block.stringtable.resolveString(userSid),
        uid = uid.toLong(),
        timestamp = decodeTimestamp(timestamp, block.dateGranularity),
        visible = if (hasVisible()) visible else true,
        version = version.toLong(),
        changeSet = changeset
    )

private fun emptyMetadata(id: Long) = ElementMetadata(
    id = id,
    user = null,
    uid = null,
    timestamp = null,
    visible = true,
    version = null,
    changeSet = null
)

/**
 * Decodes the flattened `keys_vals` array from [DenseNodes] into per-node tag maps.
 *
 * The array interleaves key and value string-table indices, using `0` as the delimiter
 * between consecutive nodes' tags. An empty array means all nodes in the block are tagless.
 */
internal fun decodeDenseTags(
    keysVals: List<Int>,
    stringTable: Osmformat.StringTable
): List<Map<String, String>> {
    val result = mutableListOf<Map<String, String>>()
    var i = 0
    var currentTags = mutableMapOf<String, String>()
    while (i < keysVals.size) {
        val keyIndex = keysVals[i]
        if (keyIndex == 0) {
            result.add(currentTags.toMap())
            currentTags = mutableMapOf()
            i++
        } else {
            val valIndex = keysVals[i + 1]
            currentTags[stringTable.resolveString(keyIndex)] = stringTable.resolveString(valIndex)
            i += 2
        }
    }
    return result
}

private fun resolveTags(
    keys: List<Int>,
    vals: List<Int>,
    stringTable: Osmformat.StringTable
): Map<String, String> = keys.indices.associate { i ->
    stringTable.resolveString(keys[i]) to stringTable.resolveString(vals[i])
}

private fun decodeMembers(
    rolesSid: List<Int>,
    memids: List<Long>,
    types: List<Osmformat.Relation.MemberType>,
    stringTable: Osmformat.StringTable
): List<Member> {
    val decodedIds = decodeDelta(memids)
    return rolesSid.indices.map { i ->
        val role = stringTable.resolveString(rolesSid[i]).ifBlank { null }
        Member(
            type = when (types[i]) {
                Osmformat.Relation.MemberType.NODE -> MemberType.NODE
                Osmformat.Relation.MemberType.WAY -> MemberType.WAY
                Osmformat.Relation.MemberType.RELATION -> MemberType.RELATION
            },
            id = decodedIds[i],
            role = role
        )
    }
}

private fun Osmformat.StringTable.resolveString(index: Int): String =
    getS(index).toStringUtf8()
