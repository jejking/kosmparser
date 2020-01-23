package com.jejking.kosmparser.xml

import com.jejking.kosmparser.xml.XmlParser.toParseEvents
import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking

@ExperimentalStdlibApi
class XmlParserTest: FunSpec() {

    init {
        context("single byte array") {

            test("should emit start document") {
                val parseEventFlow = toParseEventFlow("<myxml/>")
                runBlocking {
                    parseEventFlow.first() shouldBe StartDocument("", "UTF-8", false)
                }
            }
            
            test("should handle xml declaration") {
                val xml =
                """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <myxml/>
                """.trimIndent()
                val parseEventFlow = toParseEventFlow(xml)
                runBlocking {
                    parseEventFlow.first() shouldBe StartDocument("", "UTF-8", true)
                }
            }

            test("should emit start element") {
                val parseEventFlow = toParseEventFlow("<myxml/>")
                runBlocking {
                    parseEventFlow.filter{ it is StartElement}.first() shouldBe StartElement("myxml", emptyMap())
                }
            }

            test("should emit start element with one attribute") {
                val parseEventFlow = toParseEventFlow("""<myxml foo="bar"/>""")
                runBlocking {
                    val element = parseEventFlow.filter{ it is StartElement}.first()
                    element shouldBe StartElement("myxml", mapOf("foo" to "bar"))
                }
            }

            test("should emit start element with two attributes") {
                val parseEventFlow = toParseEventFlow("""<myxml foo="bar" wibble="wobble"/>""")
                runBlocking {
                    val element = parseEventFlow.filter{ it is StartElement }.first()
                    element shouldBe StartElement("myxml", mapOf("foo" to "bar", "wibble" to "wobble"))
                }
            }

            test("should emit end element") {
                val parseEventFlow = toParseEventFlow("<myxml/>")
                runBlocking {
                    val element = parseEventFlow.filter{ it is EndElement }.first()
                    element shouldBe EndElement("myxml")
                }
            }

            test("should emit end document") {
                val parseEventFlow = toParseEventFlow("<myxml/>")
                runBlocking {
                    val parseEvent = parseEventFlow.filter{ it is EndDocument }.first()
                    parseEvent shouldBe EndDocument
                }
            }
        }
    }

    private fun toParseEventFlow(xml: String): Flow<ParseEvent> {
        val byteArrayFlow = listOf(xml).map { it.encodeToByteArray() }.asFlow()
        return toParseEvents(byteArrayFlow)
    }


}