package com.jejking.kosmparser.io

import kotlinx.coroutines.flow.Flow
import java.net.URI
import java.net.http.HttpClient

fun URI.asFlow(httpClient: HttpClient): Flow<ByteArray> = TODO()