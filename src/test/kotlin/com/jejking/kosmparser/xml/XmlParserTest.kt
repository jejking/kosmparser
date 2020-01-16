package com.jejking.kosmparser.xml

import com.jejking.kosmparser.xml.XmlParser.toParseEvents
import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@ExperimentalStdlibApi
class XmlParserTest: FunSpec() {

    init {
        context("correct, simple xml") {
            test("should emit start document") {
                val byteArrayFlow = listOf("<myxml/>").map { it -> it.encodeToByteArray() }.asFlow()
                val parseEventFlow = toParseEvents(byteArrayFlow)
                runBlocking {
                    parseEventFlow.first() shouldBe StartDocument
                }
            }
        }
    }



}