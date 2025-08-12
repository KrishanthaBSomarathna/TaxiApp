package com.aerotech.taxiapp.utils

import android.location.Location

object DistanceUtils {
    /** returns meters between two lat/lng */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val res = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, res)
        return res[0].toDouble()
    }
}
