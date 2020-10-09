package com.jejking.kosmparser.xml

import javax.xml.stream.XMLStreamConstants

/**
 * Inspired by the Akka example at https://akka.io/blog/2016/09/16/custom-flows-parsing-xml-part-1
 */

/**
 * Encapsulates a subset of XML Parse Events.
 */
sealed class SimpleXmlParseEvent()

sealed class TextEventSimpleXml() : SimpleXmlParseEvent() {
  abstract val text: String
}

/**
 * Start of an Element.
 *
 * @see [XMLStreamConstants.START_ELEMENT]
 * @see [XMLStreamConstants.ATTRIBUTE]
 */
data class StartElement(val localName: String, val attributes: Map<String, String> = emptyMap()) : SimpleXmlParseEvent()

/**
 * End of an Element.
 *
 * @see [XMLStreamConstants.END_ELEMENT]
 */
data class EndElement(val localName: String) : SimpleXmlParseEvent()

/**
 * Processing Instruction.
 *
 * @see [XMLStreamConstants.PROCESSING_INSTRUCTION]
 */
data class ProcessingInstruction(val target: String?, val data: String?) : SimpleXmlParseEvent()

/**
 * Characters.
 *
 * @see [XMLStreamConstants.CHARACTERS]
 */
data class Characters(override val text: String) : TextEventSimpleXml()

/**
 * Comment.
 *
 * @see [XMLStreamConstants.COMMENT]
 */
data class Comment(val text: String) : SimpleXmlParseEvent()


/**
 * Space (ignorable whitespace).
 *
 * @see [XMLStreamConstants.SPACE]
 */
data class Space(val text: String) : SimpleXmlParseEvent()

/**
 * Start of document.
 *
 * @see [XMLStreamConstants.START_DOCUMENT]
 */
data class StartDocument(val systemId: String,
                         val characterEncodingScheme: String,
                         val isStandalone: Boolean) : SimpleXmlParseEvent()

/**
 * End of document.
 *
 * @see [XMLStreamConstants.END_DOCUMENT]
 */
object EndDocument : SimpleXmlParseEvent()

/**
 * Characters data.
 *
 * @see [XMLStreamConstants.CDATA]
 */
data class CData(override val text: String) : TextEventSimpleXml()

