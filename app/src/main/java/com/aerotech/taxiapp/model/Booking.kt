package com.aerotech.taxiapp.model

data class Booking(
    val userId: String = "",
    val userName: String = "",
    val userPhone: String = "",
    val userLat: Double = 0.0,
    val userLng: Double = 0.0,
    val driverName: String = "",
    val driverLat: Double = 0.0,
    val driverLng: Double = 0.0,
    val destinationLat: Double = 0.0,
    val destinationLng: Double = 0.0,
    val destinationAddress: String = "",
    val tripDateTime: String = "",
    val paymentType: String = "",
    val bookingDateTime: String = ""
)