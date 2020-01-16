package com.jejking.kosmparser.xml

import com.fasterxml.aalto.AsyncXMLStreamReader
import com.fasterxml.aalto.stax.InputFactoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform

object XmlParser {

    fun toParseEvents(byteArrayFlow: Flow<ByteArray>): Flow<ParseEvent> {
        val inputFactory = InputFactoryImpl()
        val parser = inputFactory.createAsyncFor(ByteArray(0))

        return byteArrayFlow.transform {
            parser.inputFeeder.feedInput(it, 0, it.size)
            while (parser.next() != AsyncXMLStreamReader.EVENT_INCOMPLETE) {
                emit(StartDocument)
            }
        }
    }
}