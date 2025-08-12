 package com.aerotech.taxiapp

 import android.view.LayoutInflater
 import android.view.ViewGroup
 import androidx.recyclerview.widget.RecyclerView
 import com.aerotech.taxiapp.databinding.ItemMyBookingBinding

 class MyBookingsAdapter(
     private val items: List<BookingListItem>,
     private val onCancel: (BookingListItem) -> Unit
 ) : RecyclerView.Adapter<MyBookingsAdapter.ViewHolder>() {

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

         b.title.text = item.booking.destinationAddress.ifBlank { "Destination not set" }
         b.subtitle.text = "Driver: ${item.booking.driverName}\nTrip: ${item.booking.tripDateTime}"
         b.meta.text = "Payment: ${item.booking.paymentType}"

         b.cancelBtn.setOnClickListener { onCancel(item) }
     }
 }


