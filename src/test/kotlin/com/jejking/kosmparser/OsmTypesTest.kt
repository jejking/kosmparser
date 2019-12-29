package com.jejking.kosmparser

import io.kotlintest.data.forall
import io.kotlintest.properties.Gen
import io.kotlintest.properties.forAll
import io.kotlintest.shouldThrow
import io.kotlintest.specs.FunSpec
import kotlin.random.Random

class OsmTypesTest: FunSpec() {
    init {
        context("point") {
            test("should accept lat values between -90 and 90 and long between -180 and 180") {
                class PointGen: Gen<Point> {

                    override fun constants(): Iterable<Point> {
                        return listOf(Point(0.0, 0.0), Point(-90.0, -180.0), Point(90.0, 180.0))
                    }

                    override fun random(): Sequence<Point> {
                        return generateSequence {
                            Point(Random.nextDouble(-90.0, 90.0), Random.nextDouble(-180.0, 180.0))
                        }
                    }
                }

                forAll(PointGen(), { p: Point -> LAT_RANGE.contains(p.lat) && LON_RANGE.contains(p.lon) })
            }
            test("should accept lat value of -90") {
                Point(-90.0, 0.0)
            }
            test("should accept lat value of 90") {
                Point(90.0, 0.0)
            }
            test("should accept long value of -180") {
                Point(0.0, -180.0)
            }
            test("should accept long value of 180") {
                Point(0.0, 180.0)
            }
            test("should reject lat value smaller than -90") {
                shouldThrow<IllegalStateException> {
                    Point(-90.1, 0.0)
                }
            }
            test("should reject lat value greater than 90") {
                shouldThrow<IllegalStateException> {
                    Point(90.1, 0.0)
                }
            }
            test("should reject long value smaller than -180") {
                shouldThrow<IllegalStateException> {
                    Point(0.0, -180.1)
                }
            }
            test("should reject long value greater than 180") {
                shouldThrow<IllegalStateException> {
                    Point(0.0, 180.1)
                }
            }
        }

        context("way") {

            test("should accept list of 2 nodes") {

            }

            test("should accept list of 2000 nodes") {

            }

            test("should reject list of nodes with 2001 elements") {

            }

            test("should declare a list of 0..1 node element to be faulty") {

            }

            test("should identify a closed way") {

            }
        }
    }

}