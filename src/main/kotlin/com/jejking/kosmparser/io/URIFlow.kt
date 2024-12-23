package com.jejking.kosmparser.io

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat
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

const val DEFAULT_TIMEOUT_SECONDS = 10L
const val HTTP_200_OK = 200

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
fun URI.asFlow(
  httpClient: HttpClient,
  timeout: Duration = Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS)
): Flow<ByteArray> {

  val theURI = this

  return flow {
    check(schemeIsHttpOrHttps(theURI.scheme))
    val response = doGet(timeout, theURI, httpClient)
    checkStatusCode(response, theURI)
    toPublisher(response.body())
    .asFlow()
    .flatMapConcat { bbl -> bbl.asFlow() }
    .collect { bb ->
      val target = ByteArray(bb.limit())
      bb.get(target)
      emit(target)
    }
  }
}

private fun schemeIsHttpOrHttps(scheme: String?): Boolean {
  return when (scheme) {
    "http", "https" -> true
    else -> false
  }
}

private fun checkStatusCode(response: HttpResponse<Publisher<List<ByteBuffer>>>, theURI: URI) {
  val statusCode = response.statusCode()

  if (statusCode != HTTP_200_OK) {
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
