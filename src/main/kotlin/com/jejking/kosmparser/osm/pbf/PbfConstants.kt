package com.jejking.kosmparser.osm.pbf

/** Multiplier to convert PBF nanodegree integers to decimal degrees. */
internal const val NANODEGREES_TO_DEGREES = 1e-9

/** Scale factor for rounding coordinates to OSM's 7 decimal places of precision. */
internal const val OSM_COORDINATE_PRECISION_SCALE = 1e7
