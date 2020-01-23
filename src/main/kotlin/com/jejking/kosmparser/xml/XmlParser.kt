package com.jejking.kosmparser.xml

import com.fasterxml.aalto.AsyncByteArrayFeeder
import com.fasterxml.aalto.AsyncXMLStreamReader
import com.fasterxml.aalto.stax.InputFactoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transform
import javax.xml.stream.XMLStreamConstants

object XmlParser {

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    fun toParseEvents(byteArrayFlow: Flow<ByteArray>): Flow<ParseEvent> {
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
                    else -> println("Got ${next}, cannot handle yet")
                }
                next = parser.next()
            }
        }.onCompletion {
            parser.inputFeeder.endOfInput()
            emit(endDocument(parser))
        }
    }


    private fun endDocument(parser: AsyncXMLStreamReader<AsyncByteArrayFeeder>): ParseEvent {
        parser.close()
        return EndDocument
    }

    private fun endElement(parser: AsyncXMLStreamReader<AsyncByteArrayFeeder>): EndElement {
        return EndElement(parser.localName)
    }

    private fun startElement(parser: AsyncXMLStreamReader<AsyncByteArrayFeeder>): StartElement {
        val attributes = (0 until parser.attributeCount).map { it ->
            Pair(parser.getAttributeLocalName(it), parser.getAttributeValue(it))
        }.toMap()
        return StartElement(parser.localName, attributes)
    }

    private fun startDocument(parser: AsyncXMLStreamReader<AsyncByteArrayFeeder>): StartDocument {
        return StartDocument("", parser.encoding ?: "UTF-8", parser.isStandalone)
    }
}