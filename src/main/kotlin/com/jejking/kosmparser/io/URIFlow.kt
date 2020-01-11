package com.jejking.kosmparser.io

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.reactive.asFlow
import org.reactivestreams.FlowAdapters.toPublisher
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

val validSchemes = setOf("http", "https")

@kotlinx.coroutines.ExperimentalCoroutinesApi
fun URI.asFlow(httpClient: HttpClient, duration: Duration = Duration.ofSeconds(10)): Flow<ByteArray> {

    check(scheme in validSchemes)
    val theURI = this

    return flow {

        val request = HttpRequest.newBuilder()
                .timeout(duration)
                .uri(theURI)
                .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofPublisher())

        toPublisher(response.body()).asFlow().collect { byteBufferList ->
            byteBufferList.forEach { byteBuffer ->
                val target = ByteArray(byteBuffer.limit())
                byteBuffer.get(target)
                emit(target)
            }
        }
    }


}