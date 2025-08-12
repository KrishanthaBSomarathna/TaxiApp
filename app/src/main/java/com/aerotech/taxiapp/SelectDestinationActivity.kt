package com.aerotech.taxiapp

import android.app.Activity
import android.location.Geocoder
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aerotech.taxiapp.databinding.ActivitySelectDestinationBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import java.util.*

class SelectDestinationActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivitySelectDestinationBinding
    private lateinit var map: GoogleMap
    private var chosenLatLng: LatLng? = null
    private var chosenAddress: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySelectDestinationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val mapFrag = supportFragmentManager.findFragmentById(R.id.destMap) as com.google.android.gms.maps.SupportMapFragment
        mapFrag.getMapAsync(this)

        binding.myLocationBtn.setOnClickListener {
            centerToMockCurrentLocation()
        }

        binding.chooseBtn.setOnClickListener {
            chosenLatLng?.let {
                val data = intent
                data.putExtra("destLat", it.latitude)
                data.putExtra("destLng", it.longitude)
                data.putExtra("destAddress", chosenAddress ?: "")
                setResult(Activity.RESULT_OK, data)
                finish()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isZoomControlsEnabled = true
        // Default to mock current location
        centerToMockCurrentLocation()
        map.setOnMapClickListener { latLng ->
            chosenLatLng = latLng
            map.clear()
            map.addMarker(MarkerOptions().position(latLng).title("Destination"))
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
            try {
                val ge = Geocoder(this, Locale.getDefault())
                val list = ge.getFromLocation(latLng.latitude, latLng.longitude, 1)
                chosenAddress = if (list != null && list.isNotEmpty()) list[0].getAddressLine(0) else ""
            } catch (e: Exception) { chosenAddress = "" }
        }
    }

    private fun centerToMockCurrentLocation() {
        // MOCK current location for development (near Driver A ~800m north)
        val mock = LatLng(13.068500 + 0.00719, 80.234938)
        chosenLatLng = mock
        chosenAddress = ""
        map.clear()
        map.addMarker(MarkerOptions().position(mock).title("My location"))
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(mock, 15f))

        // Real device location (uncomment to use real GPS):
        // val fused = LocationServices.getFusedLocationProviderClient(this)
        // if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
        //     ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        //     ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 200)
        //     return
        // }
        // fused.lastLocation.addOnSuccessListener { loc ->
        //     loc?.let {
        //         val here = LatLng(it.latitude, it.longitude)
        //         chosenLatLng = here
        //         map.clear()
        //         map.addMarker(MarkerOptions().position(here).title("My location"))
        //         map.animateCamera(CameraUpdateFactory.newLatLngZoom(here, 15f))
        //     }
        // }
    }
}
