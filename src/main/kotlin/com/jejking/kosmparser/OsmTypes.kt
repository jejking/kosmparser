package com.jejking.kosmparser

import java.time.ZonedDateTime

const val MIN_LAT = -90.0
const val MAX_LAT = 90.0
const val MIN_LON = -180.0
const val MAX_LON = 180.0

val LAT_RANGE = MIN_LAT.rangeTo(MAX_LAT)
val LON_RANGE = MIN_LON.rangeTo(MAX_LON)

data class Point(val lat: Double, val lon: Double) {
    init {
        check(LAT_RANGE.contains(lat), {"${lat} out of range (-90, 90)"})
        check(LON_RANGE.contains(lon), {"${lon} out of range (-180, 180)"})
    }
}

enum class MemberType {
    NODE, WAY, RELATION
}

data class ElementMetadata(val id: Long, val visible: Boolean = true,
                           val timestamp: ZonedDateTime?, val changeSet: Long?,
                           val uuid: Long?, val user: String?, val version: Long?)

sealed class Element() {
    abstract val elementMetadata: ElementMetadata
    abstract val tags: Map<String, String>
}

data class Node(override val elementMetadata: ElementMetadata,
                override val tags: Map<String, String>,
                val point: Point): Element()

data class Way(override val elementMetadata: ElementMetadata,
               override val tags: Map<String, String>,
               val nds: List<Long>): Element()

data class Member(val type: MemberType, val ref: Long, val role: String?)

data class Relation(override val elementMetadata: ElementMetadata,
                    override val tags: Map<String, String>,
                    val members: List<Member>): Element()
