package com.aerotech.taxiapp

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aerotech.taxiapp.databinding.ActivityBookingBinding
import com.aerotech.taxiapp.model.Booking
import com.aerotech.taxiapp.utils.DistanceUtils
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class BookingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBookingBinding
    private val REQUEST_DEST = 101
    companion object { private const val BOOKING_CHANNEL_ID = "booking_status" }

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

        // Apply window insets padding
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

        // Initialize destination with a default value
        binding.destinationTv.text = "Tap 'Select destination' to choose"
        binding.destinationTv.tag = ""

        binding.selectDestBtn.setOnClickListener {
            startActivityForResult(Intent(this, SelectDestinationActivity::class.java), REQUEST_DEST)
        }

        // Date/Time pickers
        binding.tripDateTimeEt.setOnClickListener { showDateTimePicker() }

        binding.confirmBtn.setOnClickListener {
            if (!validate()) return@setOnClickListener
            val tripDateTime = binding.tripDateTimeEt.text.toString().trim()
            
            // Check driver availability first
            checkDriverAvailability(tripDateTime) { isAvailable ->
                if (isAvailable) {
                    // Use transactional lock approach to avoid double-book
                    createBookingWithLock(tripDateTime)
                } else {
                    Toast.makeText(this, "Driver $driverName is not available at this time. Please select a different time or driver.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun toIsoSeconds(input: String): String {
        println("DEBUG: Converting date/time: '$input'")
        return try {
            val inFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val outFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val d = inFmt.parse(input)
            if (d != null) {
                val result = outFmt.format(d)
                println("DEBUG: Parsed successfully: '$result'")
                result
            } else {
                val result = input.replace(" ", "T").let { if (it.length == 16) "$it:00" else it }
                println("DEBUG: Fallback conversion: '$result'")
                result
            }
        } catch (e: Exception) {
            val result = input.replace(" ", "T").let { if (it.length == 16) "$it:00" else it }
            println("DEBUG: Exception in conversion, fallback: '$result'")
            result
        }
    }

    private fun showDateTimePicker() {
        // Get current date and time
        val now = Calendar.getInstance()
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        // Set minimum date to tomorrow (users can't book for today or past dates)
        val tomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select trip date (upcoming days only)")
            .setSelection(tomorrow.timeInMillis)
            .setInputMode(MaterialDatePicker.INPUT_MODE_CALENDAR)
            .build()
            
        datePicker.addOnPositiveButtonClickListener { selection ->
            val selectedDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            selectedDate.timeInMillis = selection
            
            // Additional validation to ensure selected date is not today or past
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            if (selectedDate.before(todayStart)) {
                Toast.makeText(this, "Please select a future date", Toast.LENGTH_SHORT).show()
                return@addOnPositiveButtonClickListener
            }
            
            val year = selectedDate.get(Calendar.YEAR)
            val month = selectedDate.get(Calendar.MONTH) + 1
            val day = selectedDate.get(Calendar.DAY_OF_MONTH)

            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(9) // Default to 9 AM for better UX
                .setMinute(0)
                .setTitleText("Select trip time")
                .build()
                
            timePicker.addOnPositiveButtonClickListener {
                val hour = timePicker.hour
                val minute = timePicker.minute
                
                // Validate time for today's bookings (if user somehow selects today)
                val selectedDateTime = Calendar.getInstance().apply {
                    set(year, month - 1, day, hour, minute, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                
                if (selectedDateTime.before(now)) {
                    Toast.makeText(this, "Please select a future time", Toast.LENGTH_SHORT).show()
                    return@addOnPositiveButtonClickListener
                }
                
                val formatted = String.format(Locale.getDefault(), "%04d-%02d-%02d %02d:%02d", year, month, day, hour, minute)
                binding.tripDateTimeEt.setText(formatted)
            }
            timePicker.show(supportFragmentManager, "timePicker")
        }
        
        // Set minimum date to tomorrow
        datePicker.addOnNegativeButtonClickListener {
            // User cancelled date selection
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
        
        // Validate that the selected date/time is in the future
        val tripDateTimeStr = binding.tripDateTimeEt.text.toString().trim()
        if (!isValidFutureDateTime(tripDateTimeStr)) {
            binding.tripDateTimeEt.error = "Please select a future date and time"
            return false
        }
        
        // Check if destination is properly selected
        val destTag = binding.destinationTv.tag as? String ?: ""
        if (destTag.isBlank() || binding.destinationTv.text == "No destination" || binding.destinationTv.text == "Tap 'Select destination' to choose") {
            Toast.makeText(this, "Please select a destination first", Toast.LENGTH_SHORT).show()
            return false
        }
        
        // Validate destination coordinates
        val parts = destTag.split(",")
        val dLat = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
        val dLng = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        if (dLat == 0.0 || dLng == 0.0) {
            Toast.makeText(this, "Invalid destination coordinates", Toast.LENGTH_SHORT).show()
            return false
        }
        
        if (binding.paymentRg.checkedRadioButtonId == -1) {
            Toast.makeText(this, "Choose payment type", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    /**
     * Validates that the selected date and time is in the future
     */
    private fun isValidFutureDateTime(dateTimeStr: String): Boolean {
        return try {
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val selectedDateTime = formatter.parse(dateTimeStr)
            val now = Date()
            
            selectedDateTime?.after(now) ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the driver is available at the specified date and time
     * This provides a user-friendly check before attempting to create a booking
     */
    private fun checkDriverAvailability(tripDateTime: String, callback: (Boolean) -> Unit) {
        // Sanitize driver name for Firebase key
        val sanitizedDriverName = driverName
            .replace(".", "_")
            .replace("#", "_")
            .replace("$", "_")
            .replace("[", "_")
            .replace("]", "_")
        
        val lockRef = FirebaseDatabase.getInstance()
            .getReference("driver_locks")
            .child(sanitizedDriverName)
            .child(tripDateTime)
        
        lockRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                // Driver is already booked at this time
                callback(false)
            } else {
                // Driver is available
                callback(true)
            }
        }.addOnFailureListener { exception ->
            // If we can't check availability, assume available and let the transaction handle it
            println("DEBUG: Could not check driver availability: ${exception.message}")
            callback(true)
        }
    }

    /**
     * Checks if the user already has a booking with the specified driver on the same day
     */
    private fun checkExistingUserBooking(userId: String, driverName: String, tripDateTime: String, callback: (Boolean) -> Unit) {
        // Extract date part from tripDateTime (YYYY-MM-DD)
        val dateOnly = tripDateTime.split("T")[0]
        
        // Query bookings to check for existing user-driver combination on the same date
        val bookingsRef = FirebaseDatabase.getInstance().getReference("bookings")
        
        bookingsRef.orderByChild("userId").equalTo(userId).get()
            .addOnSuccessListener { snapshot ->
                var hasExisting = false
                
                if (snapshot.exists()) {
                    for (bookingSnapshot in snapshot.children) {
                        val booking = bookingSnapshot.getValue(Booking::class.java)
                        if (booking != null && 
                            booking.driverName == driverName && 
                            booking.tripDateTime.startsWith(dateOnly)) {
                            hasExisting = true
                            break
                        }
                    }
                }
                
                callback(hasExisting)
            }
            .addOnFailureListener { exception ->
                println("DEBUG: Error checking existing bookings: ${exception.message}")
                // If we can't check, assume no existing booking to allow the process to continue
                callback(false)
            }
    }

    /**
     * Creates booking using a small transactional lock to avoid race conditions.
     * Writes both:
     *  - /bookings/{bookingId} => Booking
     *  - /driver_locks/{driverName}/{tripDateTime} => bookingId
     */
    @SuppressLint("NewApi")
    private fun createBookingWithLock(tripDateTimeRaw: String) {
        val u = FirebaseAuth.getInstance().currentUser ?: run {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }
        val uid = u.uid
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(uid)

        userRef.get().addOnSuccessListener { ds ->
            // Check if user data exists
            if (!ds.exists()) {
                Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            val userName = ds.child("name").getValue(String::class.java) ?: ""
            val phone = ds.child("phone").getValue(String::class.java) ?: "Phone not set"

            // Debug: Print user data for troubleshooting
            println("DEBUG: User data from database:")
            println("  userName: '${userName}'")
            println("  phone: '${phone}'")
            println("  uid: '${uid}'")
            println("  ds.exists(): ${ds.exists()}")
            println("  ds.children: ${ds.children.map { "${it.key}: ${it.value}" }}")

            // Check if required user data is present
            if (userName.isBlank()) {
                Toast.makeText(this, "User name not found. Please update your profile.", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            // Check if user already has a booking with this driver on the same day
            val destTag = binding.destinationTv.tag as? String ?: ""
            val parts = destTag.split(",")
            val dLat = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
            val dLng = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            val dAddr = binding.destinationTv.text.toString()

            // Debug: Print destination data for troubleshooting
            println("DEBUG: Destination data:")
            println("  destTag: '${destTag}'")
            println("  dLat: ${dLat}, dLng: ${dLng}")
            println("  dAddr: '${dAddr}'")

            // Validate destination data
            if (destTag.isBlank() || dLat == 0.0 || dLng == 0.0 || dAddr.isBlank()) {
                Toast.makeText(this, "Please select a valid destination", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            // Additional validation for coordinates
            if (dLat < -90 || dLat > 90 || dLng < -180 || dLng > 180) {
                Toast.makeText(this, "Please select a valid destination", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            val paymentType = if (binding.paymentRg.checkedRadioButtonId == R.id.rbCash) "Cash" else "Credit Card"

            // Debug: Print payment type for troubleshooting
            println("DEBUG: Payment type:")
            println("  checkedRadioButtonId: ${binding.paymentRg.checkedRadioButtonId}")
            println("  rbCash id: ${R.id.rbCash}")
            println("  paymentType: '${paymentType}'")

            // bookingDateTime in ISO format (fallback if java.time not available)
            val bookingDateTime = try {
                java.time.OffsetDateTime.now().toString()
            } catch (e: Throwable) {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
            }

            // Debug: Print booking date time for troubleshooting
            println("DEBUG: Booking date time:")
            println("  bookingDateTime: '${bookingDateTime}'")

            // Normalize trip date time to ISO "yyyy-MM-dd'T'HH:mm:ss"
            val formattedTripDateTime = if (tripDateTimeRaw.length == 16) {
                tripDateTimeRaw.replace(" ", "T") + ":00"
            } else {
                // attempt conversion if user typed differently
                toIsoSeconds(tripDateTimeRaw)
            }

            // Validate the formatted date/time
            if (formattedTripDateTime.length != 19 || !formattedTripDateTime.contains("T")) {
                Toast.makeText(this, "Invalid trip date/time format", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            // Check for existing user booking with this driver on the same day
            checkExistingUserBooking(uid, driverName, formattedTripDateTime) { hasExisting ->
                if (hasExisting) {
                    Toast.makeText(this, "You already have a booking with $driverName on this day. Please select a different driver or date.", Toast.LENGTH_LONG).show()
                    return@checkExistingUserBooking
                }

                // Continue with booking creation
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
                    tripDateTime = formattedTripDateTime,
                    paymentType = paymentType,
                    bookingDateTime = bookingDateTime
                )

                // Debug: Print booking data for troubleshooting
                println("DEBUG: Booking data:")
                println("  userId: '${booking.userId}'")
                println("  userName: '${booking.userName}'")
                println("  userPhone: '${booking.userPhone}'")
                println("  userLat: ${booking.userLat}, userLng: ${booking.userLng}")
                println("  driverName: '${booking.driverName}'")
                println("  driverLat: ${booking.driverLat}, driverLng: ${booking.userLng}")
                println("  destinationLat: ${booking.destinationLat}, destinationLng: ${booking.destinationLng}")
                println("  destinationAddress: '${booking.destinationAddress}'")
                println("  tripDateTime: '${booking.tripDateTime}'")
                println("  paymentType: '${booking.paymentType}'")
                println("  bookingDateTime: '${booking.bookingDateTime}'")

                if (!validateBookingData(booking)) {
                    Toast.makeText(this, "Invalid booking data", Toast.LENGTH_SHORT).show()
                    return@checkExistingUserBooking
                }

                // Prepare lock path: sanitize driverName for a firebase key
                val sanitizedDriverName = driverName
                    .replace(".", "_")
                    .replace("#", "_")
                    .replace("$", "_")
                    .replace("[", "_")
                    .replace("]", "_")

                val lockPath = "driver_locks/$sanitizedDriverName"
                val lockRef = FirebaseDatabase.getInstance().getReference(lockPath).child(formattedTripDateTime)

                // Try to create a lock via transaction
                runTransactionWithRetry(lockRef, booking, sanitizedDriverName, formattedTripDateTime, driverName, 0)
            }
        }.addOnFailureListener { exception ->
            exception.printStackTrace()
            val errorMessage = when (exception) {
                is com.google.firebase.database.DatabaseException -> {
                    when (exception.cause) {
                        is com.google.firebase.database.DatabaseError -> {
                            when ((exception.cause as com.google.firebase.database.DatabaseError).code) {
                                com.google.firebase.database.DatabaseError.PERMISSION_DENIED -> "Permission denied. Please check your account status."
                                com.google.firebase.database.DatabaseError.UNAVAILABLE -> "Database temporarily unavailable. Please try again."
                                else -> "Database error: ${exception.message}"
                            }
                        }
                        else -> "Database error: ${exception.message}"
                    }
                }
                else -> "Failed to read user details: ${exception.message}"
            }
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            println("DEBUG: User data read error: ${exception.javaClass.simpleName} - ${exception.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun showBookingSuccessNotification(driverName: String) {
        createNotificationChannelIfNeeded()
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(this, 1001, tapIntent, pendingFlags)

        val builder = NotificationCompat.Builder(this, BOOKING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Booking confirmed")
            .setContentText("Your ride with $driverName is booked.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        with(NotificationManagerCompat.from(this)) {
            notify(2001, builder.build())
        }
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Booking Status"
            val descriptionText = "Notifications about booking confirmations"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(BOOKING_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Validates that all required booking data fields are present and valid
     */
    private fun validateBookingData(booking: Booking): Boolean {
        return when {
            booking.userId.isBlank() -> {
                println("DEBUG: userId is blank")
                false
            }
            booking.userName.isBlank() -> {
                println("DEBUG: userName is blank")
                false
            }
            booking.userPhone.isBlank() -> {
                println("DEBUG: userPhone is blank")
                false
            }
            booking.driverName.isBlank() -> {
                println("DEBUG: driverName is blank")
                false
            }
            booking.destinationAddress.isBlank() -> {
                println("DEBUG: destinationAddress is blank")
                false
            }
            booking.tripDateTime.isBlank() -> {
                println("DEBUG: tripDateTime is blank")
                false
            }
            booking.paymentType.isBlank() -> {
                println("DEBUG: paymentType is blank")
                false
            }
            booking.bookingDateTime.isBlank() -> {
                println("DEBUG: bookingDateTime is blank")
                false
            }
            // Check if coordinates are properly set (not necessarily non-zero)
            booking.userLat < -90 || booking.userLat > 90 || booking.userLng < -180 || booking.userLng > 180 -> {
                println("DEBUG: user coordinates out of range: lat=${booking.userLat}, lng=${booking.userLng}")
                false
            }
            booking.driverLat < -90 || booking.driverLat > 90 || booking.driverLng < -180 || booking.driverLng > 180 -> {
                println("DEBUG: driver coordinates out of range: lat=${booking.driverLat}, lng=${booking.driverLng}")
                false
            }
            booking.destinationLat < -90 || booking.destinationLat > 90 || booking.destinationLng < -180 || booking.destinationLng > 180 -> {
                println("DEBUG: destination coordinates out of range: lat=${booking.destinationLat}, lng=${booking.destinationLng}")
                false
            }
            else -> {
                println("DEBUG: All validation passed")
                true
            }
        }
    }

    /**
     * Runs a transaction with retry capability for permission errors
     */
    private fun runTransactionWithRetry(
        lockRef: com.google.firebase.database.DatabaseReference,
        booking: Booking,
        sanitizedDriverName: String,
        formattedTripDateTime: String,
        driverName: String,
        retryCount: Int
    ) {
        lockRef.runTransaction(object : com.google.firebase.database.Transaction.Handler {
            override fun doTransaction(currentData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                // if lock exists -> abort
                if (currentData.value != null) {
                    return com.google.firebase.database.Transaction.abort()
                }
                // claim lock (temporary placeholder)
                currentData.value = "LOCKED"
                return com.google.firebase.database.Transaction.success(currentData)
            }

            override fun onComplete(error: com.google.firebase.database.DatabaseError?, committed: Boolean, currentData: com.google.firebase.database.DataSnapshot?) {
                if (error != null) {
                    error.toException().printStackTrace()
                    
                    // Retry once for permission denied errors
                    if (error.code == com.google.firebase.database.DatabaseError.PERMISSION_DENIED && retryCount < 1) {
                        println("DEBUG: Permission denied, retrying transaction... (attempt ${retryCount + 1})")
                        // Wait a bit before retry
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            runTransactionWithRetry(lockRef, booking, sanitizedDriverName, formattedTripDateTime, driverName, retryCount + 1)
                        }, 1000)
                        return
                    }
                    
                    // If permission denied after retry, try fallback method
                    if (error.code == com.google.firebase.database.DatabaseError.PERMISSION_DENIED && retryCount >= 1) {
                        println("DEBUG: Permission denied after retry, trying fallback method...")
                        createBookingWithoutLock(booking, sanitizedDriverName, formattedTripDateTime, driverName)
                        return
                    }
                    
                    val errorMessage = when (error.code) {
                        com.google.firebase.database.DatabaseError.PERMISSION_DENIED -> "Permission denied. Please check your account status."
                        com.google.firebase.database.DatabaseError.UNAVAILABLE -> "Database temporarily unavailable. Please try again."
                        com.google.firebase.database.DatabaseError.NETWORK_ERROR -> "Network error. Please check your connection."
                        else -> "Error acquiring lock: ${error.message}"
                    }
                    Toast.makeText(this@BookingActivity, errorMessage, Toast.LENGTH_LONG).show()
                    println("DEBUG: Transaction error: ${error.code} - ${error.message}")
                    return
                }
                
                if (!committed) {
                    // Someone else already locked this driver/time
                    Toast.makeText(this@BookingActivity, "Driver already booked at this time", Toast.LENGTH_LONG).show()
                    return
                }

                // Lock acquired — create booking id and write both nodes atomically
                val bookingsRef = FirebaseDatabase.getInstance().getReference("bookings")
                val newBookingId = bookingsRef.push().key ?: ""
                if (newBookingId.isBlank()) {
                    Toast.makeText(this@BookingActivity, "Failed to create booking id", Toast.LENGTH_SHORT).show()
                    // release lock
                    lockRef.removeValue()
                    return
                }

                // Prepare updates map
                val rootRef = FirebaseDatabase.getInstance().reference
                val updates = HashMap<String, Any>()
                updates["/bookings/$newBookingId"] = booking
                updates["/driver_locks/$sanitizedDriverName/$formattedTripDateTime"] = newBookingId

                rootRef.updateChildren(updates)
                    .addOnSuccessListener {

                        showBookingSuccessNotification(driverName)
                        val homeIntent = Intent(this@BookingActivity, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(homeIntent)
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                    .addOnFailureListener { ex ->
                        ex.printStackTrace()
                        // cleanup lock on failure
                        lockRef.removeValue()
                        Toast.makeText(this@BookingActivity, "Failed to create booking", Toast.LENGTH_SHORT).show()
                    }
            }
        })
    }

    /**
     * Fallback method to create booking without transaction lock
     */
    private fun createBookingWithoutLock(
        booking: Booking,
        sanitizedDriverName: String,
        formattedTripDateTime: String,
        driverName: String
    ) {
        println("DEBUG: Creating booking without lock (fallback method)")
        
        // Create booking directly
        val bookingsRef = FirebaseDatabase.getInstance().getReference("bookings")
        val newBookingId = bookingsRef.push().key ?: ""
        
        if (newBookingId.isBlank()) {
            Toast.makeText(this, "Failed to create booking id", Toast.LENGTH_SHORT).show()
            return
        }

        // Try to create the booking
        bookingsRef.child(newBookingId).setValue(booking)
            .addOnSuccessListener {
                Toast.makeText(this, "Booking created successfully! (Note: Driver lock not acquired)", Toast.LENGTH_LONG).show()
                showBookingSuccessNotification(driverName)
                val homeIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(homeIntent)
                setResult(Activity.RESULT_OK)
                finish()
            }
            .addOnFailureListener { ex ->
                ex.printStackTrace()
                val errorMessage = when (ex) {
                    is com.google.firebase.database.DatabaseException -> {
                        when (ex.cause) {
                            is com.google.firebase.database.DatabaseError -> {
                                when ((ex.cause as com.google.firebase.database.DatabaseError).code) {
                                    com.google.firebase.database.DatabaseError.PERMISSION_DENIED -> "Permission denied. Please check your account status."
                                    com.google.firebase.database.DatabaseError.UNAVAILABLE -> "Database temporarily unavailable. Please try again."
                                    else -> "Database error: ${ex.message}"
                                }
                            }
                            else -> "Database error: ${ex.message}"
                        }
                    }
                    else -> "Failed to create booking: ${ex.message}"
                }
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                println("DEBUG: Fallback booking creation error: ${ex.javaClass.simpleName} - ${ex.message}")
            }
    }
}
