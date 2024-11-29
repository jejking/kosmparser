package com.jejking.kosmparser.xml

import com.jejking.kosmparser.xml.XmlFlowMapper.coalesceText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

@kotlinx.coroutines.ExperimentalCoroutinesApi
object XmlFlowTools {

  fun toCoalescingParseEventFlow(vararg xmlParts: String): Flow<SimpleXmlParseEvent> {
    return toParseEventFlow(*xmlParts).coalesceText()
  }

  fun toParseEventFlow(vararg xmlParts: String): Flow<SimpleXmlParseEvent> {
    val byteArrayFlow = listOf(*xmlParts).map { it.encodeToByteArray() }.asFlow()
    return XmlFlowMapper.toParseEvents(byteArrayFlow)
  }
}
