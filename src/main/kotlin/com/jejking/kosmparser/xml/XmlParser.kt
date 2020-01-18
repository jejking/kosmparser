package com.jejking.kosmparser.xml

import com.fasterxml.aalto.AsyncByteArrayFeeder
import com.fasterxml.aalto.AsyncXMLStreamReader
import com.fasterxml.aalto.stax.InputFactoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform
import javax.xml.stream.XMLStreamConstants

object XmlParser {

    fun toParseEvents(byteArrayFlow: Flow<ByteArray>): Flow<ParseEvent> {
        val inputFactory = InputFactoryImpl()
        val parser = inputFactory.createAsyncFor(ByteArray(0))

        return byteArrayFlow.transform {
            parser.inputFeeder.feedInput(it, 0, it.size)
            while (parser.hasNext()) {
                when(val next = parser.next()) {
                    XMLStreamConstants.START_DOCUMENT -> emit(startDocument(parser))
                    XMLStreamConstants.START_ELEMENT -> emit(startElement(parser))
                    XMLStreamConstants.END_ELEMENT -> emit(endElement(parser))
                    XMLStreamConstants.END_DOCUMENT -> {
                        parser.close()
                        emit(EndDocument)
                    }
                }

            }
        }
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