package com.jejking.kosmparser.xml

import com.fasterxml.aalto.AsyncByteArrayFeeder
import com.fasterxml.aalto.AsyncXMLStreamReader
import com.fasterxml.aalto.stax.InputFactoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transform
import javax.xml.stream.XMLStreamConstants

object XmlFlowMapper {

  /**
   * Creates a Flow of ParseEvents from an underlying flow of byte arrays.
   *
   * Note that the flow created does not coalesce text. An additional utility function is provided for this,
   * but as OSM XML does not contain useful text outside of attributes which are parsed fully, this
   * function is sufficient in itself.
   */
  @kotlinx.coroutines.ExperimentalCoroutinesApi
  fun toParseEvents(byteArrayFlow: Flow<ByteArray>): Flow<SimpleXmlParseEvent> {
    val inputFactory = InputFactoryImpl()
    val parser = inputFactory.createAsyncFor(ByteArray(0))

    return byteArrayFlow.transform {
      parser.inputFeeder.feedInput(it, 0, it.size)
      var next = parser.next()
      while (next != AsyncXMLStreamReader.EVENT_INCOMPLETE) {
        when (next) {
          XMLStreamConstants.START_DOCUMENT -> emit(startDocument(parser))
          XMLStreamConstants.START_ELEMENT -> emit(startElement(parser))
          XMLStreamConstants.END_ELEMENT -> emit(endElement(parser))
          XMLStreamConstants.PROCESSING_INSTRUCTION -> emit(processingInstruction(parser))
          XMLStreamConstants.CHARACTERS -> emit(characters(parser))
          XMLStreamConstants.COMMENT -> emit(comment(parser))
          XMLStreamConstants.CDATA -> emit(cdata(parser))
          else -> println("Got $next, cannot handle yet")
        }
        next = parser.next()
      }
    }.onCompletion {
      parser.inputFeeder.endOfInput()
      emit(endDocument(parser))
    }
  }

  /**
   * Coalesces text elements together if they follow each other in the flow. This can be useful
   * to simplify subsequent processing.
   *
   * This function is provided to improve the general utility of the XML flow.
   */
  fun Flow<SimpleXmlParseEvent>.coalesceText(): Flow<SimpleXmlParseEvent> {

    var textBuffer: StringBuilder = StringBuilder()
    var buffering = false

    return this.transform {
      if (it is Characters) {
        if (!buffering) {
          buffering = true
        }
        textBuffer.append(it.text)
      } else {
        if (buffering) {
          buffering = false
          val buffered = textBuffer.toString()
          textBuffer = StringBuilder()
          emit(Characters(buffered))
        }
        emit(it)
      }
    }
  }

  private fun cdata(parser: AsyncXMLStreamReader<AsyncByteArrayFeeder>): SimpleXmlParseEvent {
    return CData(parser.text)
  }

  private fun comment(parser: AsyncXMLStreamReader<AsyncByteArrayFeeder>): SimpleXmlParseEvent {
    return Comment(parser.text)
  }

  private fun characters(parser: AsyncXMLStreamReader<AsyncByteArrayFeeder>): SimpleXmlParseEvent {
    return Characters(parser.text)
  }

  private fun processingInstruction(parser: AsyncXMLStreamReader<AsyncByteArrayFeeder>): SimpleXmlParseEvent {
    return ProcessingInstruction(parser.piTarget, parser.piData)
  }

  private fun endDocument(parser: AsyncXMLStreamReader<AsyncByteArrayFeeder>): SimpleXmlParseEvent {
    parser.close()
    return EndDocument
  }

  private fun endElement(parser: AsyncXMLStreamReader<AsyncByteArrayFeeder>): EndElement {
    return EndElement(parser.localName)
  }

  private fun startElement(parser: AsyncXMLStreamReader<AsyncByteArrayFeeder>): StartElement {
    val attributes = (0 until parser.attributeCount).associate {
      Pair(parser.getAttributeLocalName(it), parser.getAttributeValue(it))
    }
    return StartElement(parser.localName, attributes)
  }

  private fun startDocument(parser: AsyncXMLStreamReader<AsyncByteArrayFeeder>): StartDocument {
    return StartDocument("", parser.encoding ?: "UTF-8", parser.isStandalone)
  }
}
