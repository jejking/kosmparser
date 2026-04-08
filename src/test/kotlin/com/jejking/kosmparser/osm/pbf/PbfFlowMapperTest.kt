package com.jejking.kosmparser.osm.pbf

import com.jejking.kosmparser.osm.Bounds
import com.jejking.kosmparser.osm.Node
import com.jejking.kosmparser.osm.OsmData
import com.jejking.kosmparser.osm.OsmMetadata
import com.jejking.kosmparser.osm.Point
import com.jejking.kosmparser.osm.Relation
import com.jejking.kosmparser.osm.Way
import com.jejking.kosmparser.osm.pbf.toOsmDataFlow as pbfToOsmDataFlow
import com.jejking.kosmparser.osm.xml.OsmFlowMapper.toOsmDataFlow as xmlToOsmDataFlow
import com.jejking.kosmparser.util.getPath
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

/**
 * Cross-validates the PBF parser against the XML parser using Osmosis-generated PBF fixtures.
 *
 * For each XML/PBF fixture pair, the test asserts that:
 * - The domain elements (Node, Way, Relation) are identical
 * - The bounds in OsmMetadata match (if present)
 *
 * OsmMetadata.version and OsmMetadata.generator are intentionally NOT compared, as they differ:
 * - XML parser produces version="0.6", generator="manual"
 * - PBF parser produces version=null, generator="Osmosis X.Y.Z"
 */
class PbfFlowMapperTest : FunSpec({

    fun domainElements(items: List<OsmData>): List<OsmData> =
        items.filterIsInstance<Node>() +
            items.filterIsInstance<Way>() +
            items.filterIsInstance<Relation>()

    context("test1 fixture — bounds, nodes, way, relation") {
        test("domain elements from PBF match XML") {
            runBlocking {
                val xmlItems = getPath("test1.osm").xmlToOsmDataFlow().toList()
                val pbfItems = getPath("test1.osm.pbf").pbfToOsmDataFlow().toList()

                val xmlElements = domainElements(xmlItems)
                val pbfElements = domainElements(pbfItems)

                pbfElements shouldBe xmlElements
            }
        }

        test("PBF OsmMetadata has correct bounds matching XML") {
            runBlocking {
                val xmlMeta = getPath("test1.osm").xmlToOsmDataFlow()
                    .filterIsInstance<OsmMetadata>().first()
                val pbfMeta = getPath("test1.osm.pbf").pbfToOsmDataFlow()
                    .filterIsInstance<OsmMetadata>().first()

                pbfMeta.bounds.shouldNotBeNull()
                pbfMeta.bounds shouldBe xmlMeta.bounds
            }
        }

        test("PBF OsmMetadata has null version") {
            runBlocking {
                val pbfMeta = getPath("test1.osm.pbf").pbfToOsmDataFlow()
                    .filterIsInstance<OsmMetadata>().first()
                pbfMeta.version shouldBe null
            }
        }
    }

    context("test-no-bounds fixture — no bounds element") {
        test("domain elements from PBF match XML") {
            runBlocking {
                val xmlItems = getPath("test-no-bounds.osm").xmlToOsmDataFlow().toList()
                val pbfItems = getPath("test-no-bounds.osm.pbf").pbfToOsmDataFlow().toList()

                domainElements(pbfItems) shouldBe domainElements(xmlItems)
            }
        }

        test("PBF OsmMetadata has null bounds") {
            runBlocking {
                val pbfMeta = getPath("test-no-bounds.osm.pbf").pbfToOsmDataFlow()
                    .filterIsInstance<OsmMetadata>().first()
                pbfMeta.bounds shouldBe null
            }
        }
    }
})
