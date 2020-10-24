package com.jejking.kosmparser.io

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.http.Fault
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient

@kotlinx.coroutines.ExperimentalCoroutinesApi
class URIFlowTest : StringSpec() {

  private val port = 8888
  private val host = "localhost"
  private val baseUri = "http://$host:$port"

  private lateinit var wireMockServer: WireMockServer
  private lateinit var wiremock: WireMock

  private val httpClient = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_2)
    .build()

  override fun beforeSpec(spec: Spec) {
    super.beforeSpec(spec)
    wireMockServer = WireMockServer(port)
    wireMockServer.start()
    wiremock = WireMock(port)
  }

  override fun afterSpec(spec: Spec) {
    super.afterSpec(spec)
    wireMockServer.stop()
  }

  override fun beforeTest(testCase: TestCase) {
    super.beforeTest(testCase)
    wireMockServer.resetAll()
  }

  init {
    "should GET content of an HTTP URI and expose response as flow of byte array" {

      val response = """{"success":true"}"""
      wiremock.register(
        WireMock.get("/info")
          .willReturn(
            WireMock.aResponse()
              .withStatus(200)
              .withBody(response)
              .withHeader("Content-Type", "application/json")
          )
      )
      val uri = URI.create("$baseUri/info")

      runBlocking {
        val actualResponse = uri.asFlow(httpClient)
          .map { String(it) }
          .reduce { accumulator, value -> accumulator + value }

        actualResponse shouldBe response
      }
    }

    "should throw exception if HTTP response is 4xx" {
      val response = """{"success":false"}"""
      wiremock.register(
        WireMock.get("/400")
          .willReturn(
            WireMock.aResponse()
              .withStatus(404)
              .withBody(response)
              .withHeader("Content-Type", "application/json")
          )
      )
      val flow = URI.create("$baseUri/400").asFlow(httpClient)
      runBlocking {
        shouldThrow<IOException> {
          flow.collect({ })
        }
      }
    }

    "should propagate any exceptions" {
      wiremock.register(
        WireMock.get("/fault")
          .willReturn(
            WireMock.aResponse()
              .withFault(Fault.EMPTY_RESPONSE)
          )
      )
      val uri = URI.create("$baseUri/fault")

      val flow = uri.asFlow(httpClient)
      runBlocking {
        shouldThrow<IOException> { flow.collect {} }
      }
    }

    "should throw exception if URI is not http or https" {
      val uris = listOf(
        "file:///tmp/file",
        "mailto:foo@bar.com",
        "data:text/vnd-example+xyz;foo=bar;base64,R0lGODdh"
      )
        .map { URI.create(it) }

      uris.forEach {
        shouldThrow<IllegalStateException> {
          it.asFlow(httpClient)
        }
      }
    }
  }
}
