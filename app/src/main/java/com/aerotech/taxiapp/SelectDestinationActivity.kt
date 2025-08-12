package com.aerotech.taxiapp

import android.app.Activity
import android.location.Geocoder
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
        binding = ActivitySelectDestinationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFrag = supportFragmentManager.findFragmentById(R.id.destMap) as com.google.android.gms.maps.SupportMapFragment
        mapFrag.getMapAsync(this)

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
}
