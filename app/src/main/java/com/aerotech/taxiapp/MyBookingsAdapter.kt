 package com.aerotech.taxiapp

 import android.view.LayoutInflater
 import android.view.ViewGroup
 import androidx.recyclerview.widget.RecyclerView
 import com.aerotech.taxiapp.databinding.ItemMyBookingBinding
 import java.text.SimpleDateFormat
 import java.util.*

 class MyBookingsAdapter(
     private val items: List<BookingListItem>,
     private val onCancel: (BookingListItem) -> Unit
 ) : RecyclerView.Adapter<MyBookingsAdapter.ViewHolder>() {

     private val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())

     inner class ViewHolder(val binding: ItemMyBookingBinding) : RecyclerView.ViewHolder(binding.root)

     override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
         val inflater = LayoutInflater.from(parent.context)
         val binding = ItemMyBookingBinding.inflate(inflater, parent, false)
         return ViewHolder(binding)
     }

     override fun getItemCount(): Int = items.size

     override fun onBindViewHolder(holder: ViewHolder, position: Int) {
         val item = items[position]
         val b = holder.binding

         // Set destination address
         b.title.text = item.booking.destinationAddress.ifBlank { "Destination not set" }
         
         // Set driver name
         b.driverName.text = "Driver: ${item.booking.driverName}"
         
         // Format and set trip date/time
         try {
             val tripDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                 .parse(item.booking.tripDateTime)
             if (tripDate != null) {
                 b.tripDateTime.text = "Trip: ${dateFormat.format(tripDate)}"
             } else {
                 b.tripDateTime.text = "Trip: ${item.booking.tripDateTime}"
             }
         } catch (e: Exception) {
             b.tripDateTime.text = "Trip: ${item.booking.tripDateTime}"
         }
         
         // Set payment info
         b.paymentInfo.text = "Payment: ${item.booking.paymentType}"

         // Set up cancel button
         b.cancelBtn.setOnClickListener { onCancel(item) }
     }
 }


