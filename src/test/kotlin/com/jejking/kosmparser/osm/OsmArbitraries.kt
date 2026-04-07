package com.jejking.kosmparser.osm

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Shared Kotest property-based testing arbitraries for OSM domain types.
 * Used across [OsmElementEventTest] and [OsmDataBuilderTest].
 */
object OsmArbitraries {

    /** Arbitrary [ZonedDateTime] values in UTC, covering a range of valid OSM timestamps. */
    val arbZonedDateTime: Arb<ZonedDateTime> = Arb.long(0L..2_000_000_000L).map { epochSecond ->
        ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC)
    }

    /**
     * Arbitrary [ElementMetadata] with all combinations of nullable fields.
     * `uid`, `timestamp`, `version`, `changeSet` may each be null or non-null.
     * `visible` defaults to `true` but is varied. IDs may be negative (editor placeholders).
     * Strings are restricted to printable ASCII to ensure round-trip XML validity.
     */
    val arbElementMetadata: Arb<ElementMetadata> = arbitrary { rs ->
        val safeString = Arb.string(1..50).map { s -> s.filter { c -> c.code in 32..126 } }
        ElementMetadata(
            id = Arb.long(-1000L..1_000_000L).next(rs),
            user = safeString.orNull(0.2).next(rs),
            uid = Arb.long(1L..1_000_000L).orNull(0.2).next(rs),
            timestamp = arbZonedDateTime.orNull(0.2).next(rs),
            visible = Arb.boolean().next(rs),
            version = Arb.long(1L..100L).orNull(0.2).next(rs),
            changeSet = Arb.long(1L..1_000_000L).orNull(0.2).next(rs)
        )
    }

    /** Arbitrary valid [Point] within WGS84 bounds. */
    val arbPoint: Arb<Point> = arbitrary { rs ->
        Point(
            lat = Arb.numericDouble(Point.MIN_LAT, Point.MAX_LAT).next(rs),
            lon = Arb.numericDouble(Point.MIN_LON, Point.MAX_LON).next(rs)
        )
    }

    /** Arbitrary tag map (0–10 entries), keys and values restricted to printable ASCII. */
    val arbTags: Arb<Map<String, String>> = arbitrary { rs ->
        val size = Arb.long(0L..10L).next(rs).toInt()
        (0 until size).associate {
            Arb.string(1..20).map { s -> s.filter { c -> c.code in 32..126 } }.next(rs).ifEmpty { "k" } to
                Arb.string(0..50).map { s -> s.filter { c -> c.code in 32..126 } }.next(rs)
        }
    }

    /** Arbitrary [Node]. */
    val arbNode: Arb<Node> = arbitrary { rs ->
        Node(
            elementMetadata = arbElementMetadata.next(rs),
            tags = arbTags.next(rs),
            point = arbPoint.next(rs)
        )
    }

    /** Arbitrary [Way] with 1–2000 node refs, as per the OSM spec. */
    val arbWay: Arb<Way> = arbitrary { rs ->
        val ndCount = Arb.long(1L..Way.MAX_NODES_IN_WAY.toLong()).next(rs).toInt()
        Way(
            elementMetadata = arbElementMetadata.next(rs),
            tags = arbTags.next(rs),
            nds = (1..ndCount).map { Arb.long(-1000L..1_000_000L).next(rs) }
        )
    }

    /** Arbitrary [Member] for relations. */
    private val arbMember: Arb<Member> = arbitrary { rs ->
        val type = listOf(MemberType.NODE, MemberType.WAY, MemberType.RELATION).random(rs.random)
        Member(
            type = type,
            id = Arb.long(-1000L..1_000_000L).next(rs),
            role = Arb.string(1..20)
                .map { s -> s.filter { c -> c.code in 32..126 }.ifBlank { "r" } }
                .orNull(0.3)
                .next(rs)
        )
    }

    /** Arbitrary [Relation] with 0–20 members. */
    val arbRelation: Arb<Relation> = arbitrary { rs ->
        val memberCount = Arb.long(0L..20L).next(rs).toInt()
        Relation(
            elementMetadata = arbElementMetadata.next(rs),
            tags = arbTags.next(rs),
            members = (1..memberCount).map { arbMember.next(rs) }
        )
    }

    /** Arbitrary [Bounds]. */
    val arbBounds: Arb<Bounds> = arbitrary { rs ->
        val lat1 = Arb.numericDouble(Point.MIN_LAT, Point.MAX_LAT).next(rs)
        val lat2 = Arb.numericDouble(Point.MIN_LAT, Point.MAX_LAT).next(rs)
        val lon1 = Arb.numericDouble(Point.MIN_LON, Point.MAX_LON).next(rs)
        val lon2 = Arb.numericDouble(Point.MIN_LON, Point.MAX_LON).next(rs)
        Bounds(
            minPoint = Point(minOf(lat1, lat2), minOf(lon1, lon2)),
            maxPoint = Point(maxOf(lat1, lat2), maxOf(lon1, lon2))
        )
    }

    /** Arbitrary [OsmMetadata] with optional bounds. */
    val arbOsmMetadata: Arb<OsmMetadata> = arbitrary { rs ->
        OsmMetadata(
            version = Arb.string(1..10).orNull(0.2).next(rs),
            generator = Arb.string(1..30).orNull(0.2).next(rs),
            bounds = arbBounds.orNull(0.3).next(rs)
        )
    }
}
