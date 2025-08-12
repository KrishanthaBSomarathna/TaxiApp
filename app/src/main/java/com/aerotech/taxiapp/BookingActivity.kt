package com.aerotech.taxiapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.aerotech.taxiapp.utils.DistanceUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aerotech.taxiapp.databinding.ActivityBookingBinding
import com.aerotech.taxiapp.model.Booking
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class BookingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBookingBinding
    private val REQUEST_DEST = 101

    private var driverName: String = ""
    private var driverLat = 0.0
    private var driverLng = 0.0
    private var userLat = 0.0
    private var userLng = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityBookingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Toolbar back
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        driverName = intent.getStringExtra("driverName") ?: ""
        driverLat = intent.getDoubleExtra("driverLat", 0.0)
        driverLng = intent.getDoubleExtra("driverLng", 0.0)
        userLat = intent.getDoubleExtra("userLat", 0.0)
        userLng = intent.getDoubleExtra("userLng", 0.0)

        binding.driverNameTv.text = "Driver: $driverName"
        val approxDistMeters = DistanceUtils.distanceMeters(userLat, userLng, driverLat, driverLng).toInt()
        binding.driverInfoSubtitleTv.text = if (approxDistMeters > 0) "Approx. ${approxDistMeters} m away" else ""

        binding.selectDestBtn.setOnClickListener {
            startActivityForResult(Intent(this, SelectDestinationActivity::class.java), REQUEST_DEST)
        }

        // Date/Time pickers
        binding.tripDateTimeEt.setOnClickListener { showDateTimePicker() }

        binding.confirmBtn.setOnClickListener {
            if (!validate()) return@setOnClickListener
            val tripDateTime = binding.tripDateTimeEt.text.toString().trim()
            checkDriverAndBook(tripDateTime)
        }
    }

    private fun showDateTimePicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select trip date")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()
        datePicker.addOnPositiveButtonClickListener { selection ->
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.timeInMillis = selection
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
                .setMinute(0)
                .setTitleText("Select time")
                .build()
            timePicker.addOnPositiveButtonClickListener {
                val hour = timePicker.hour
                val minute = timePicker.minute
                val formatted = String.format(Locale.getDefault(), "%04d-%02d-%02d %02d:%02d", year, month, day, hour, minute)
                binding.tripDateTimeEt.setText(formatted)
            }
            timePicker.show(supportFragmentManager, "timePicker")
        }
        datePicker.show(supportFragmentManager, "datePicker")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DEST && resultCode == Activity.RESULT_OK) {
            val lat = data?.getDoubleExtra("destLat", 0.0) ?: 0.0
            val lng = data?.getDoubleExtra("destLng", 0.0) ?: 0.0
            val addr = data?.getStringExtra("destAddress") ?: ""
            binding.destinationTv.text = addr.ifEmpty { "$lat, $lng" }
            binding.destinationTv.tag = "$lat,$lng"
        }
    }

    private fun validate(): Boolean {
        if (TextUtils.isEmpty(binding.tripDateTimeEt.text.toString().trim())) {
            binding.tripDateTimeEt.error = "Required"
            return false
        }
        if (binding.destinationTv.text == "No destination") {
            Toast.makeText(this, "Select destination", Toast.LENGTH_SHORT).show()
            return false
        }
        if (binding.paymentRg.checkedRadioButtonId == -1) {
            Toast.makeText(this, "Choose payment type", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun checkDriverAndBook(tripDateTime: String) {
        val bookingsRef = FirebaseDatabase.getInstance().getReference("bookings")
        bookingsRef.orderByChild("driverName").equalTo(driverName)
            .get().addOnSuccessListener { snapshot ->
                var available = true
                for (child in snapshot.children) {
                    val existingTrip = child.child("tripDateTime").getValue(String::class.java) ?: ""
                    if (existingTrip == tripDateTime) { available = false; break }
                }
                if (!available) {
                    Toast.makeText(this, "Driver already booked at this time", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                createBooking(tripDateTime)
            }.addOnFailureListener {
                it.printStackTrace()
                Toast.makeText(this, "Error checking booking", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createBooking(tripDateTime: String) {
        val u = FirebaseAuth.getInstance().currentUser ?: run {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show(); return
        }
        val uid = u.uid
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(uid)
        userRef.get().addOnSuccessListener { ds ->
            val userName = ds.child("name").getValue(String::class.java) ?: ""
            val phone = ds.child("phone").getValue(String::class.java) ?: ""
            val destTag = binding.destinationTv.tag as? String ?: ""
            val parts = destTag.split(",")
            val dLat = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
            val dLng = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            val dAddr = binding.destinationTv.text.toString()

            val paymentType = if (binding.paymentRg.checkedRadioButtonId == R.id.rbCash) "Cash" else "Credit Card"
            val bookingDateTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())

            val booking = Booking(
                userId = uid,
                userName = userName,
                userPhone = phone,
                userLat = userLat,
                userLng = userLng,
                driverName = driverName,
                driverLat = driverLat,
                driverLng = driverLng,
                destinationLat = dLat,
                destinationLng = dLng,
                destinationAddress = dAddr,
                tripDateTime = tripDateTime,
                paymentType = paymentType,
                bookingDateTime = bookingDateTime
            )

            // persist booking
            val bookingsRef = FirebaseDatabase.getInstance().getReference("bookings")
            val bookingId = bookingsRef.push().key ?: ""
            bookingsRef.child(bookingId).setValue(booking).addOnSuccessListener {
                Toast.makeText(this, "Booking created", Toast.LENGTH_LONG).show()
                setResult(Activity.RESULT_OK)
                finish()
            }.addOnFailureListener {
                it.printStackTrace()
                Toast.makeText(this, "Failed to create booking", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
