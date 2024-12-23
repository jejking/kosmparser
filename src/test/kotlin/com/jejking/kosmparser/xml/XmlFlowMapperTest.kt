package com.jejking.kosmparser.xml

import com.jejking.kosmparser.xml.XmlFlowTools.toCoalescingParseEventFlow
import com.jejking.kosmparser.xml.XmlFlowTools.toParseEventFlow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@ExperimentalCoroutinesApi
class XmlFlowMapperTest : FunSpec() {

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
          parseEventFlow.filter { it is StartElement }.first() shouldBe StartElement("myxml", emptyMap())
        }
      }

      test("should emit start element with one attribute") {
        val parseEventFlow = toParseEventFlow("""<myxml foo="bar"/>""")
        runBlocking {
          val element = parseEventFlow.filter { it is StartElement }.first()
          element shouldBe StartElement("myxml", mapOf("foo" to "bar"))
        }
      }

      test("should emit start element with two attributes") {
        val parseEventFlow = toParseEventFlow("""<myxml foo="bar" wibble="wobble"/>""")
        runBlocking {
          val element = parseEventFlow.filter { it is StartElement }.first()
          element shouldBe StartElement("myxml", mapOf("foo" to "bar", "wibble" to "wobble"))
        }
      }

      test("should emit end element") {
        val parseEventFlow = toParseEventFlow("<myxml/>")
        runBlocking {
          val element = parseEventFlow.filter { it is EndElement }.first()
          element shouldBe EndElement("myxml")
        }
      }

      test("should emit end document") {
        val parseEventFlow = toParseEventFlow("<myxml/>")
        runBlocking {
          val parseEvent = parseEventFlow.filter { it is EndDocument }.first()
          parseEvent shouldBe EndDocument
        }
      }

      test("should emit processing instruction") {
        val xml = """
                    <myxml>
                    <?xml-stylesheet href="mystyle.xslt" type="text/xsl"?>
                    </myxml>
        """.trimIndent()
        val parseEventFlow = toParseEventFlow(xml)
        runBlocking {
          val parseEvent = parseEventFlow.filter { it is ProcessingInstruction }.first()
          parseEvent shouldBe ProcessingInstruction("xml-stylesheet", "href=\"mystyle.xslt\" type=\"text/xsl\"")
        }
      }

      test("should emit characters") {
        val parseEventFlow = toParseEventFlow("<myxml>some text</myxml>")
        runBlocking {
          val parseEvent = parseEventFlow.filter { it is Characters }.first()
          parseEvent shouldBe Characters("some text")
        }
      }

      test("should emit comment") {
        val xml = """
                    <myxml>
                        <!-- comment text -->
                    </myxml>
        """.trimIndent()
        val parseEventFlow = toParseEventFlow(xml)
        runBlocking {
          val parseEvent = parseEventFlow.filter { it is Comment }.first()
          parseEvent shouldBe Comment(" comment text ")
        }
      }

      test("should emit space between elements as characters") {
        val xml = "<myxml>  <anotherElement/></myxml>"
        val parseEventFlow = toParseEventFlow(xml)
        runBlocking {
          val parseEvent = parseEventFlow.filter { it is Characters }.first()
          parseEvent shouldBe Characters("  ")
        }
      }

      test("should emit cdata") {
        val xml = """
                    <myxml>
                        <![CDATA[ some cdata ]]>
                    </myxml>
        """.trimIndent()
        val parseEventFlow = toParseEventFlow(xml)
        runBlocking {
          val parseEvent = parseEventFlow.filter { it is CData }.first()
          parseEvent shouldBe CData(" some cdata ")
        }
      }
    }

    context("multiple byte arrays") {
      test("should work with multiple input byte arrays") {
        val parseEventFlow = toParseEventFlow("""<myxml foo="bar"""", """ wibble="wobble"/>""")
        runBlocking {
          val element = parseEventFlow.filter { it is StartElement }.first()
          element shouldBe StartElement("myxml", mapOf("foo" to "bar", "wibble" to "wobble"))
        }
      }
    }

    context("coalescing characters") {
      test("one characters parse event when characters followed by end element") {
        val parseEventFlow = toCoalescingParseEventFlow("<myxml>some text</myxml>")
        runBlocking {
          val parseEvent = parseEventFlow.filter { it is Characters }.first()
          parseEvent shouldBe Characters("some text")
        }
      }

      test("one characters parse event when two characters parse events emitted before end element") {
        val parseEventFlow = toCoalescingParseEventFlow("<myxml>some", " text</myxml>")
        runBlocking {
          val parseEvent = parseEventFlow.filter { it is Characters }.first()
          parseEvent shouldBe Characters("some text")
        }
      }
    }

    context("coalescing cdata") {
      test("one cdata parse event when followed by end element") {
        val xml = """
                    <myxml>
                        <![CDATA[ some cdata ]]>
                    </myxml>
        """.trimIndent()
        val parseEventFlow = toCoalescingParseEventFlow(xml)
        runBlocking {
          val parseEvent = parseEventFlow.filter { it is CData }.first()
          parseEvent shouldBe CData(" some cdata ")
        }
      }

      test("one cdata parse event when two cdata parse events emitted before end element") {
        val xml1 = """<myxml><![CDATA[ some"""
        val xml2 = """ cdata ]]></myxml>"""
        val parseEventFlow = toCoalescingParseEventFlow(xml1, xml2)
        runBlocking {
          val parseEvent = parseEventFlow.filter { it is CData }.first()
          parseEvent shouldBe CData(" some cdata ")
        }
      }

      test("text between element tags should be coalesced") {
        val xml1 = """<myxml><tag>abc"""
        val xml2 = """def</tag></myxml>"""
        val parseEventFlow = toCoalescingParseEventFlow(xml1, xml2)
        runBlocking {
          val parseEvent = parseEventFlow.filter { it is Characters }.first()
          parseEvent shouldBe Characters("abcdef")
        }
      }
    }
  }
}
