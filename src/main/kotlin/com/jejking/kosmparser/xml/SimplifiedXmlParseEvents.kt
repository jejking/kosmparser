package com.jejking.kosmparser.xml

import javax.xml.stream.XMLStreamConstants

/**
 * Inspired by the Akka example at https://akka.io/blog/2016/09/16/custom-flows-parsing-xml-part-1
 */

/**
 * Encapsulates a subset of XML Parse Events.
 */
sealed interface SimpleXmlParseEvent

sealed interface TextEventSimpleXml : SimpleXmlParseEvent {
  val text: String
}

/**
 * Start of an Element.
 *
 * @see [XMLStreamConstants.START_ELEMENT]
 * @see [XMLStreamConstants.ATTRIBUTE]
 */
data class StartElement(val localName: String, val attributes: Map<String, String> = emptyMap()) : SimpleXmlParseEvent

/**
 * End of an Element.
 *
 * @see [XMLStreamConstants.END_ELEMENT]
 */
@JvmInline
value class EndElement(val localName: String) : SimpleXmlParseEvent

/**
 * Processing Instruction.
 *
 * @see [XMLStreamConstants.PROCESSING_INSTRUCTION]
 */
data class ProcessingInstruction(val target: String?, val data: String?) : SimpleXmlParseEvent

/**
 * Characters.
 *
 * @see [XMLStreamConstants.CHARACTERS]
 */
@JvmInline
value class Characters(override val text: String) : TextEventSimpleXml

/**
 * Comment.
 *
 * @see [XMLStreamConstants.COMMENT]
 */
@JvmInline
value class Comment(override val text: String) : TextEventSimpleXml

/**
 * Space (ignorable whitespace).
 *
 * @see [XMLStreamConstants.SPACE]
 */
@JvmInline
value class Space(override val text: String) : TextEventSimpleXml

/**
 * Start of document.
 *
 * @see [XMLStreamConstants.START_DOCUMENT]
 */
data class StartDocument(
  val systemId: String,
  val characterEncodingScheme: String,
  val isStandalone: Boolean
) : SimpleXmlParseEvent

/**
 * End of document.
 *
 * @see [XMLStreamConstants.END_DOCUMENT]
 */
object EndDocument : SimpleXmlParseEvent

/**
 * Characters data.
 *
 * @see [XMLStreamConstants.CDATA]
 */
@JvmInline
value class CData(override val text: String) : TextEventSimpleXml
