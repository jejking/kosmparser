package com.jejking.kosmparser.io

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactive.asFlow
import org.reactivestreams.FlowAdapters.toPublisher
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.Flow.Publisher

val validSchemes = setOf("http", "https")

/**
 * Extends URI to `GET` the content of the URI and expose the body of the response as [Flow] of [ByteArray].
 *
 * The required HTTP request is only made when the flow is collected.
 *
 * @throws IOException when the flow is collected and a non-200 Status code is returned.
 *                    Other IO exceptions from the HTTP client are propagated into the flow.
 * @throws IllegalStateException if the URI scheme is **not** one of `http` or `https`,
 *                               before the flow is created
 * @param httpClient the client to use to make the request
 * @param timeout the timeout to set on the client, default 10 seconds
 */
@kotlinx.coroutines.ExperimentalCoroutinesApi
fun URI.asFlow(httpClient: HttpClient, timeout: Duration = Duration.ofSeconds(10)): Flow<ByteArray> {

  check(scheme in validSchemes)
  val theURI = this

  return flow {
    val response = doGet(timeout, theURI, httpClient)
    checkStatusCode(response, theURI)

    toPublisher(response.body()).asFlow().collect { byteBufferList ->
      byteBufferList.forEach { byteBuffer ->
        val target = ByteArray(byteBuffer.limit())
        byteBuffer.get(target)
        emit(target)
      }
    }
  }
}

private fun checkStatusCode(response: HttpResponse<Publisher<List<ByteBuffer>>>, theURI: URI) {
  val statusCode = response.statusCode()

  if (statusCode != 200) {
    throw IOException("Non-200 Status Code $statusCode at URI $theURI")
  }
}

private fun doGet(timeout: Duration, theURI: URI, httpClient: HttpClient): HttpResponse<Publisher<List<ByteBuffer>>> {
  val request = HttpRequest.newBuilder()
    .timeout(timeout)
    .uri(theURI)
    .GET()
    .build()

  return httpClient.send(request, HttpResponse.BodyHandlers.ofPublisher())
}
