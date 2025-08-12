package com.aerotech.taxiapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.aerotech.taxiapp.databinding.ActivityAuthBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.FirebaseException
import com.google.firebase.database.FirebaseDatabase
import java.util.concurrent.TimeUnit

class AuthActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAuthBinding
    private lateinit var auth: FirebaseAuth
    private var verificationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth = FirebaseAuth.getInstance()

        // if already signed in, go to MainActivity
        auth.currentUser?.let {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        binding.sendOtpBtn.setOnClickListener {
            val name = binding.nameEt.text.toString().trim()
            val phone = binding.phoneEt.text.toString().trim()
            if (name.isEmpty() || phone.isEmpty()) {
                if (name.isEmpty()) binding.nameEt.error = "Required"
                if (phone.isEmpty()) binding.phoneEt.error = "Required"
                return@setOnClickListener
            }
            binding.progress.visibility = View.VISIBLE
            sendOtp(phone)
        }

        binding.verifyOtpBtn.setOnClickListener {
            val code = binding.otpEt.text.toString().trim()
            val id = verificationId ?: return@setOnClickListener
            val credential = PhoneAuthProvider.getCredential(id, code)
            signInWithPhoneAuthCredential(credential)
        }
    }

    private fun sendOtp(phoneNumber: String) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                signInWithPhoneAuthCredential(credential)
            }
            override fun onVerificationFailed(e: FirebaseException) {
                binding.progress.visibility = View.GONE
                e.printStackTrace()
            }
            override fun onCodeSent(vid: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = vid
                binding.progress.visibility = View.GONE
                binding.otpContainer.visibility = View.VISIBLE
            }
        }
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            binding.progress.visibility = View.GONE
            if (task.isSuccessful) {
                val firebaseUser = task.result?.user ?: return@addOnCompleteListener
                val uid = firebaseUser.uid
                val name = binding.nameEt.text.toString().trim()
                val phone = binding.phoneEt.text.toString().trim()
                val userRef = FirebaseDatabase.getInstance().getReference("users").child(uid)
                val map = mapOf("name" to name, "phone" to phone, "createdAt" to System.currentTimeMillis())
                userRef.updateChildren(map).addOnCompleteListener {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            } else {
                task.exception?.printStackTrace()
            }
        }
    }
}
