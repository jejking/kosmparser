package com.jejking.kosmparser.io

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import io.kotlintest.Spec
import io.kotlintest.specs.StringSpec

class URIFlowTest: StringSpec() {

    private lateinit var wireMockServer: WireMockServer
    private lateinit var wiremock: WireMock

    override fun beforeSpec(spec: Spec) {
        super.beforeSpec(spec)
        wireMockServer = WireMockServer(8888)
        wireMockServer.start()
        wiremock = WireMock(8888)
    }


    init {
        "should GET content of an HTTP URI and expose response as flow of byte array" {

        }

        "should throw exception if HTTP response is 4xx" {

        }

        "should throw exception if HTTP response is 5xx" {

        }

        "should propagate any exceptions" {

        }

        "should throw exception if URI is not http or https" {

        }
    }
}