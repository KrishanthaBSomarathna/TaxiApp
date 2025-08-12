package com.aerotech.taxiapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aerotech.taxiapp.databinding.ActivityMainBinding
import com.aerotech.taxiapp.model.Driver
import com.aerotech.taxiapp.utils.DistanceUtils
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.Toast
import com.google.android.gms.maps.model.BitmapDescriptor
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivityMainBinding
    private lateinit var map: GoogleMap
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val drivers = listOf(
        Driver("Driver A", 13.068500, 80.234938),
        Driver("Driver B", 13.062306, 80.231172),
        Driver("Driver C", 13.071086, 80.230709)
    )
    private var userLocation: Location? = null

    private val reqPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) fetchLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // redirect to authentication if user is not signed in yet
        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        // keep your edge-to-edge behavior
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val mapFrag = supportFragmentManager.findFragmentById(R.id.map) as com.google.android.gms.maps.SupportMapFragment
        mapFrag.getMapAsync(this)

        binding.myBookingsBtn.setOnClickListener {
            startActivity(Intent(this, MyBookingsActivity::class.java))
        }

        binding.bookNowBtn.isEnabled = false
        binding.bookNowBtn.setOnClickListener {
            val nearest = findNearestDriverWithin1Km()
            nearest?.let {
                val loc = userLocation ?: return@setOnClickListener
                val i = Intent(this, BookingActivity::class.java)
                i.putExtra("driverName", it.name)
                i.putExtra("driverLat", it.latitude)
                i.putExtra("driverLng", it.longitude)
                i.putExtra("userLat", loc.latitude)
                i.putExtra("userLng", loc.longitude)
                startActivity(i)
            }
        }
        
        // Add a button to check driver availability
        binding.checkDriverAvailabilityBtn.setOnClickListener {
            showDriverAvailabilityDialog()
        }

        // request location
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            reqPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            fetchLocation()
        }
    }

    private fun fetchLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        // MOCK CURRENT LOCATION (~800m from Driver A)
        // To change the mock location, edit the latitude/longitude below.
        // Original location fetch has been commented out for mocking.
        val mockLocation = Location("mock").apply {
            // Driver A: 13.068500, 80.234938
            // ~800m north is about +0.00719 degrees in latitude
            latitude = 13.068500 + 0.00719
            longitude = 80.234938
        }
        userLocation = mockLocation
        if (::map.isInitialized) updateMap()

        // Real location fetch (restore this block to use device GPS):
        // fused.lastLocation.addOnSuccessListener { loc ->
        //     loc?.let {
        //         userLocation = it
        //         if (::map.isInitialized) updateMap()
        //     }
        // }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isZoomControlsEnabled = true
        updateMap()
    }

    private fun updateMap() {
        if (!::map.isInitialized) return
        map.clear()
        // draw drivers
        for (d in drivers) {
            val pos = LatLng(d.latitude, d.longitude)
            map.addMarker(
                MarkerOptions()
                    .position(pos)
                    .title(d.name)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
            )
        }
        userLocation?.let { u ->
            val userLatLng = LatLng(u.latitude, u.longitude)
            map.addMarker(
                MarkerOptions().position(userLatLng).title("You")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))

            val nearest = findNearestDriverWithin1Km()
            binding.bookNowBtn.isEnabled = nearest != null

            for (d in drivers) {
                val dist = DistanceUtils.distanceMeters(u.latitude, u.longitude, d.latitude, d.longitude).roundToInt()
                val pos = LatLng(d.latitude, d.longitude)
                map.addMarker(
                    MarkerOptions()
                        .position(pos)
                        .title("${d.name} — ${dist} m")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
                )
            }
        } ?: run {
            binding.bookNowBtn.isEnabled = false
        }
    }

    private fun bitmapDescriptorFromVector(vectorResId: Int): BitmapDescriptor {
        val drawable = ContextCompat.getDrawable(this, vectorResId)
            ?: throw IllegalArgumentException("Drawable not found")
        val width = drawable.intrinsicWidth
        val height = drawable.intrinsicHeight
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun findNearestDriverWithin1Km(): Driver? {
        val loc = userLocation ?: return null
        var best: Driver? = null
        var bestDist = Double.MAX_VALUE
        for (d in drivers) {
            val dist = DistanceUtils.distanceMeters(loc.latitude, loc.longitude, d.latitude, d.longitude)
            if (dist < bestDist) { bestDist = dist; best = d }
        }
        return if (bestDist <= 1000.0) best else null
    }

    /**
     * Shows a dialog with current driver availability status
     */
    private fun showDriverAvailabilityDialog() {
        // Get tomorrow's date for checking availability
        val tomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", 
            tomorrow.get(Calendar.YEAR), 
            tomorrow.get(Calendar.MONTH) + 1, 
            tomorrow.get(Calendar.DAY_OF_MONTH))
        
        val message = StringBuilder()
        message.append("Driver availability for tomorrow ($dateStr):\n\n")
        
        // Check each driver's availability
        var checkedDrivers = 0
        val totalDrivers = drivers.size
        
        drivers.forEach { driver ->
            val sanitizedDriverName = driver.name
                .replace(".", "_")
                .replace("#", "_")
                .replace("$", "_")
                .replace("[", "_")
                .replace("]", "_")
            
            val driverLocksRef = com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("driver_locks")
                .child(sanitizedDriverName)
                .child(dateStr)
            
            driverLocksRef.get().addOnSuccessListener { snapshot ->
                checkedDrivers++
                
                if (snapshot.exists()) {
                    val bookedSlots = snapshot.childrenCount
                    message.append("${driver.name}: ${bookedSlots} time slots booked\n")
                } else {
                    message.append("${driver.name}: Available all day\n")
                }
                
                // Show dialog when all drivers have been checked
                if (checkedDrivers == totalDrivers) {
                    showAvailabilityStatusDialog(message.toString())
                }
            }.addOnFailureListener { exception ->
                checkedDrivers++
                message.append("${driver.name}: Unable to check availability\n")
                
                if (checkedDrivers == totalDrivers) {
                    showAvailabilityStatusDialog(message.toString())
                }
            }
        }
    }

    /**
     * Shows the availability status dialog
     */
    private fun showAvailabilityStatusDialog(message: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Driver Availability Status")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> }
            .show()
    }

    /**
     * Shows detailed booking information for a specific driver and date
     */
    private fun showDriverBookingsDetail(driverName: String, date: String) {
        val sanitizedDriverName = driverName
            .replace(".", "_")
            .replace("#", "_")
            .replace("$", "_")
            .replace("[", "_")
            .replace("]", "_")
        
        val driverLocksRef = com.google.firebase.database.FirebaseDatabase.getInstance()
            .getReference("driver_locks")
            .child(sanitizedDriverName)
            .child(date)
        
        driverLocksRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val message = StringBuilder()
                message.append("Bookings for $driverName on $date:\n\n")
                
                for (timeSlot in snapshot.children) {
                    val time = timeSlot.key ?: ""
                    val bookingId = timeSlot.getValue(String::class.java) ?: ""
                    message.append("• $time: Booking ID $bookingId\n")
                }
                
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Driver Bookings Detail")
                    .setMessage(message.toString())
                    .setPositiveButton("OK") { _, _ -> }
                    .show()
            } else {
                Toast.makeText(this, "No bookings found for $driverName on $date", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { exception ->
            Toast.makeText(this, "Error loading booking details: ${exception.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
