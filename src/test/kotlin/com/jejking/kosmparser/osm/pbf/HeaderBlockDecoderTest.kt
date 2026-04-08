package com.jejking.kosmparser.osm.pbf

import com.jejking.kosmparser.osm.Bounds
import com.jejking.kosmparser.osm.OsmMetadata
import com.jejking.kosmparser.osm.Point
import crosby.binary.headerBBox
import crosby.binary.headerBlock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull

class HeaderBlockDecoderTest : FunSpec({

    context("toOsmMetadata") {
        test("block without bbox produces null bounds") {
            val block = headerBlock {
                requiredFeatures += "OsmSchema-V0.6"
            }
            val metadata = block.toOsmMetadata()
            metadata.bounds.shouldBeNull()
        }

        test("block with bbox decodes bounds from nanodegrees") {
            // Hamburg-ish bounding box in nanodegrees
            val block = headerBlock {
                requiredFeatures += "OsmSchema-V0.6"
                bbox = headerBBox {
                    left = 10_015_500_000L   // 10.0155 degrees
                    right = 10_031_400_000L  // 10.0314 degrees
                    bottom = 53_564_600_000L // 53.5646 degrees
                    top = 53_570_700_000L    // 53.5707 degrees
                }
            }
            val metadata = block.toOsmMetadata()
            metadata.bounds shouldBe Bounds(
                minPoint = Point(lat = 53.5646, lon = 10.0155),
                maxPoint = Point(lat = 53.5707, lon = 10.0314)
            )
        }

        test("writingprogram maps to generator") {
            val block = headerBlock {
                requiredFeatures += "OsmSchema-V0.6"
                writingprogram = "TestWriter 1.0"
            }
            block.toOsmMetadata().generator shouldBe "TestWriter 1.0"
        }

        test("version is always null") {
            val block = headerBlock {
                requiredFeatures += "OsmSchema-V0.6"
            }
            block.toOsmMetadata().version.shouldBeNull()
        }

        test("DenseNodes is a supported required feature") {
            val block = headerBlock {
                requiredFeatures += "OsmSchema-V0.6"
                requiredFeatures += "DenseNodes"
            }
            block.toOsmMetadata() // should not throw
        }

        test("HasSorting required feature is accepted") {
            val block = headerBlock {
                requiredFeatures += "OsmSchema-V0.6"
                requiredFeatures += "DenseNodes"
                requiredFeatures += "HasSorting"
            }
            block.toOsmMetadata() // should not throw
        }

        test("unknown required feature throws IllegalArgumentException") {
            val block = headerBlock {
                requiredFeatures += "OsmSchema-V0.6"
                requiredFeatures += "HistoricalInformation"
            }
            shouldThrow<IllegalArgumentException> {
                block.toOsmMetadata()
            }
        }

        test("block with no required features succeeds") {
            val block = headerBlock {}
            val metadata = block.toOsmMetadata()
            metadata.version.shouldBeNull()
        }
    }
})
