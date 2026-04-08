package com.jejking.kosmparser.osm.pbf

import com.google.protobuf.ByteString
import com.jejking.kosmparser.osm.ElementMetadata
import com.jejking.kosmparser.osm.Member
import com.jejking.kosmparser.osm.MemberType
import com.jejking.kosmparser.osm.Node
import com.jejking.kosmparser.osm.Point
import com.jejking.kosmparser.osm.Relation
import com.jejking.kosmparser.osm.Way
import crosby.binary.Osmformat
import crosby.binary.denseInfo
import crosby.binary.denseNodes
import crosby.binary.primitiveBlock
import crosby.binary.primitiveGroup
import crosby.binary.relation
import crosby.binary.stringTable
import crosby.binary.way
import crosby.binary.info
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Tests [Osmformat.PrimitiveBlock.toOsmDataList] using programmatically built protobuf fixtures.
 *
 * String table convention used throughout:
 * - index 0: "" (mandatory empty entry)
 * - index 1..N: data strings
 */
class PrimitiveBlockDecoderTest : FunSpec({

    // Helper: build a StringTable from a list of strings; index 0 is always ""
    fun makeStringTable(vararg strings: String): Osmformat.StringTable = stringTable {
        s += ByteString.copyFromUtf8("")
        strings.forEach { s += ByteString.copyFromUtf8(it) }
    }

    // Timestamp for 2014-05-14T14:12:39Z in seconds (dateGranularity=1000)
    val testTimestampEncoded = 1400076759L
    val testTimestamp = ZonedDateTime.of(2014, 5, 14, 14, 12, 39, 0, ZoneOffset.UTC)

    context("DenseNodes decoding") {

        test("decodes two tagless nodes without denseinfo") {
            // node 1: lat=53.12345, lon=10.2345 → encoded with granularity=100
            // 53.12345 / 1e-7 = 531234500; delta for first node = 531234500
            // 10.2345 / 1e-7 = 102345000
            val block = primitiveBlock {
                stringtable = makeStringTable()
                granularity = 100
                latOffset = 0
                lonOffset = 0
                dateGranularity = 1000
                primitivegroup += primitiveGroup {
                    dense = denseNodes {
                        // Two nodes: IDs 1 and 2 (delta encoded: 1, 1)
                        id += 1L
                        id += 1L
                        // lat delta: 531234500, then (542345600 - 531234500) = 11111100
                        lat += 531234500L
                        lat += 11111100L
                        // lon delta: 102345000, then (115432000 - 102345000) = 13087000
                        lon += 102345000L
                        lon += 13087000L
                        // no keys_vals means no tags
                    }
                }
            }
            val result = block.toOsmDataList()
            result shouldHaveSize 2
            val node1 = result[0] as Node
            node1.elementMetadata.id shouldBe 1L
            node1.tags shouldBe emptyMap()
            node1.point.lat shouldBe 53.12345
            node1.point.lon shouldBe 10.2345

            val node2 = result[1] as Node
            node2.elementMetadata.id shouldBe 2L
            node2.tags shouldBe emptyMap()
        }

        test("decodes nodes with tags via keys_vals") {
            // One node (id=2) with two tags: foo=bar, wibble=wobble
            // StringTable: ["", "foo", "bar", "wibble", "wobble"]
            val block = primitiveBlock {
                stringtable = makeStringTable("foo", "bar", "wibble", "wobble")
                granularity = 100
                latOffset = 0
                lonOffset = 0
                dateGranularity = 1000
                primitivegroup += primitiveGroup {
                    dense = denseNodes {
                        id += 2L
                        lat += 542345600L
                        lon += 115432000L
                        // keys_vals: [1(foo), 2(bar), 3(wibble), 4(wobble), 0(delimiter)]
                        keysVals += 1
                        keysVals += 2
                        keysVals += 3
                        keysVals += 4
                        keysVals += 0
                    }
                }
            }
            val result = block.toOsmDataList()
            result shouldHaveSize 1
            val node = result[0] as Node
            node.tags shouldBe mapOf("foo" to "bar", "wibble" to "wobble")
        }

        test("decodes nodes with denseinfo metadata") {
            // StringTable: ["", "foo"]  (user="foo" at index 1)
            val block = primitiveBlock {
                stringtable = makeStringTable("foo")
                granularity = 100
                latOffset = 0
                lonOffset = 0
                dateGranularity = 1000
                primitivegroup += primitiveGroup {
                    dense = denseNodes {
                        id += 1L
                        lat += 531234500L
                        lon += 102345000L
                        denseinfo = denseInfo {
                            version += 1
                            timestamp += testTimestampEncoded
                            changeset += 1L
                            uid += 1
                            userSid += 1
                        }
                    }
                }
            }
            val result = block.toOsmDataList()
            result shouldHaveSize 1
            val meta = (result[0] as Node).elementMetadata
            meta.id shouldBe 1L
            meta.version shouldBe 1L
            meta.timestamp shouldBe testTimestamp
            meta.changeSet shouldBe 1L
            meta.uid shouldBe 1L
            meta.user shouldBe "foo"
            meta.visible shouldBe true
        }

        test("second tagless node has empty tag map when first node has tags") {
            // StringTable: ["", "k", "v"]
            // two nodes: node 1 has [k=v], node 2 has no tags
            // keys_vals = [1, 2, 0, 0]
            val block = primitiveBlock {
                stringtable = makeStringTable("k", "v")
                granularity = 100
                latOffset = 0
                lonOffset = 0
                dateGranularity = 1000
                primitivegroup += primitiveGroup {
                    dense = denseNodes {
                        id += 1L
                        id += 1L
                        lat += 531234500L
                        lat += 0L
                        lon += 102345000L
                        lon += 0L
                        keysVals += 1
                        keysVals += 2
                        keysVals += 0
                        keysVals += 0
                    }
                }
            }
            val result = block.toOsmDataList()
            result shouldHaveSize 2
            (result[0] as Node).tags shouldBe mapOf("k" to "v")
            (result[1] as Node).tags shouldBe emptyMap()
        }
    }

    context("Way decoding") {
        test("decodes way with node refs and tags") {
            // StringTable: ["", "highway", "motorway"]
            val block = primitiveBlock {
                stringtable = makeStringTable("highway", "motorway")
                granularity = 100
                latOffset = 0
                lonOffset = 0
                dateGranularity = 1000
                primitivegroup += primitiveGroup {
                    ways += way {
                        id = 3L
                        keys += 1
                        vals += 2
                        // refs delta-encoded: [1, 1] → absolute [1, 2]
                        refs += 1L
                        refs += 1L
                        info = info {
                            version = 1
                            timestamp = testTimestampEncoded
                            changeset = 1L
                            uid = 1
                            userSid = 0  // empty string → null user
                        }
                    }
                }
            }
            val result = block.toOsmDataList()
            result shouldHaveSize 1
            val w = result[0] as Way
            w.elementMetadata.id shouldBe 3L
            w.tags shouldBe mapOf("highway" to "motorway")
            w.nds shouldBe listOf(1L, 2L)
        }
    }

    context("Relation decoding") {
        test("decodes relation with members and tags") {
            // StringTable: ["", "route", "secret", "thing", "dark-satanic"]
            val block = primitiveBlock {
                stringtable = makeStringTable("route", "secret", "thing", "dark-satanic")
                granularity = 100
                latOffset = 0
                lonOffset = 0
                dateGranularity = 1000
                primitivegroup += primitiveGroup {
                    relations += relation {
                        id = 4L
                        keys += 1 // "route"
                        vals += 2 // "secret"
                        // member: node 1, role "thing"
                        rolesSid += 3
                        memids += 1L
                        types += Osmformat.Relation.MemberType.NODE
                        // member: way 3, role "" (empty → null)
                        rolesSid += 0
                        memids += 2L // delta: 3 - 1 = 2
                        types += Osmformat.Relation.MemberType.WAY
                        // member: relation 666, role "dark-satanic"
                        rolesSid += 4
                        memids += 663L // delta: 666 - 3 = 663
                        types += Osmformat.Relation.MemberType.RELATION
                    }
                }
            }
            val result = block.toOsmDataList()
            result shouldHaveSize 1
            val rel = result[0] as Relation
            rel.elementMetadata.id shouldBe 4L
            rel.tags shouldBe mapOf("route" to "secret")
            rel.members shouldBe listOf(
                Member(MemberType.NODE, 1L, "thing"),
                Member(MemberType.WAY, 3L, null),
                Member(MemberType.RELATION, 666L, "dark-satanic")
            )
        }
    }

    context("mixed primitive groups") {
        test("empty primitive group produces no elements") {
            val block = primitiveBlock {
                stringtable = makeStringTable()
                granularity = 100
                primitivegroup += primitiveGroup { }
            }
            block.toOsmDataList() shouldBe emptyList()
        }
    }
})
