package com.aerotech.taxiapp

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.aerotech.taxiapp.databinding.ActivityMyBookingsBinding
import com.aerotech.taxiapp.model.Booking
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

data class BookingListItem(
    val id: String,
    val booking: Booking
)

class MyBookingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMyBookingsBinding
    private val database by lazy { FirebaseDatabase.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val items: MutableList<BookingListItem> = mutableListOf()
    private lateinit var adapter: MyBookingsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMyBookingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        loadBookings()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = MyBookingsAdapter(items, ::onCancelClicked)
        binding.recyclerView.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener { loadBookings() }
        binding.swipeRefresh.setColorSchemeResources(
            com.aerotech.taxiapp.R.color.primary,
            com.aerotech.taxiapp.R.color.secondary,
            com.aerotech.taxiapp.R.color.accent
        )
    }

    private fun loadBookings() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "You are not signed in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        showLoadingState()

        val ref = database.getReference("bookings")
            .orderByChild("userId")
            .equalTo(user.uid)

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                items.clear()
                if (snapshot.exists()) {
                    for (child in snapshot.children) {
                        val booking = child.getValue(Booking::class.java)
                        if (booking != null) {
                            items.add(BookingListItem(child.key ?: "", booking))
                        }
                    }
                }
                items.sortByDescending { it.booking.bookingDateTime }
                adapter.notifyDataSetChanged()
                hideLoadingState()
                updateEmptyState()
                binding.swipeRefresh.isRefreshing = false
            }

            override fun onCancelled(error: DatabaseError) {
                hideLoadingState()
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(this@MyBookingsActivity, "Failed to load bookings: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun showLoadingState() {
        binding.progress.visibility = View.VISIBLE
        binding.emptyView.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
    }

    private fun hideLoadingState() {
        binding.progress.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
    }

    private fun updateEmptyState() {
        binding.emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun onCancelClicked(item: BookingListItem) {
        AlertDialog.Builder(this)
            .setTitle("Cancel Booking?")
            .setMessage("Are you sure you want to cancel this booking with ${item.booking.driverName}?")
            .setPositiveButton("Yes, Cancel") { _, _ -> cancelBooking(item) }
            .setNegativeButton("No, Keep It", null)
            .setIcon(com.aerotech.taxiapp.R.drawable.ic_cancel)
            .show()
    }

    private fun cancelBooking(item: BookingListItem) {
        val sanitizedDriverName = item.booking.driverName
            .replace(".", "_")
            .replace("#", "_")
            .replace("$", "_")
            .replace("[", "_")
            .replace("]", "_")

        val lockRef = database.getReference("driver_locks")
            .child(sanitizedDriverName)
            .child(item.booking.tripDateTime)

        // First: attempt to release the lock only if it matches this booking id
        lockRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                if (currentData.value == item.id) {
                    currentData.value = null
                }
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                // Regardless of lock result, proceed to remove the booking
                removeBooking(item)
            }
        })
    }

    private fun removeBooking(item: BookingListItem) {
        database.getReference("bookings").child(item.id)
            .removeValue()
            .addOnSuccessListener {
                Toast.makeText(this, "Booking cancelled successfully", Toast.LENGTH_SHORT).show()
                // Refresh list
                loadBookings()
            }
            .addOnFailureListener { ex ->
                Toast.makeText(this, "Failed to cancel booking: ${ex.message}", Toast.LENGTH_LONG).show()
            }
    }
}


