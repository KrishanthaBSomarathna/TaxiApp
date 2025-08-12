package com.aerotech.taxiapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aerotech.taxiapp.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        binding.signUpBtn.setOnClickListener {
            registerUser()
        }

        binding.loginBtn.setOnClickListener {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
        }
    }

    private fun registerUser() {
        val name = binding.nameEt.text.toString().trim()
        val email = binding.emailEt.text.toString().trim()
        val phone = binding.phoneEt.text.toString().trim()
        val password = binding.passwordEt.text.toString().trim()
        val confirmPassword = binding.confirmPasswordEt.text.toString().trim()

        if (name.isEmpty()) {
            binding.nameEt.error = "Name is required"
            return
        }
        if (email.isEmpty()) {
            binding.emailEt.error = "Email is required"
            return
        }
        if (phone.isEmpty()) {
            binding.phoneEt.error = "Phone is required"
            return
        }
        if (password.isEmpty()) {
            binding.passwordEt.error = "Password is required"
            return
        }
        if (confirmPassword.isEmpty()) {
            binding.confirmPasswordEt.error = "Confirm password is required"
            return
        }
        if (password != confirmPassword) {
            binding.confirmPasswordEt.error = "Passwords do not match"
            return
        }

        binding.progress.visibility = View.VISIBLE
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val userId = user!!.uid
                    val userMap = HashMap<String, Any>()
                    userMap["name"] = name
                    userMap["email"] = email
                    userMap["phone"] = phone

                    database.reference.child("users").child(userId).setValue(userMap)
                        .addOnCompleteListener { dbTask ->
                            binding.progress.visibility = View.GONE
                            if (dbTask.isSuccessful) {
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            } else {
                                Toast.makeText(this, dbTask.exception?.localizedMessage ?: "Database error", Toast.LENGTH_LONG).show()
                            }
                        }
                } else {
                    binding.progress.visibility = View.GONE
                    task.exception?.printStackTrace()
                    Toast.makeText(this, task.exception?.localizedMessage ?: "Sign up failed", Toast.LENGTH_LONG).show()
                }
            }
    }
}
