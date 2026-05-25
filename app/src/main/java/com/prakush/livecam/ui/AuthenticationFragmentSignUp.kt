package com.prakush.livecam.ui

import android.os.Bundle
import android.util.Patterns
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.prakush.livecam.databinding.FragmentAuthenticationLoginBinding
import com.prakush.livecam.databinding.FragmentAuthenticationSignUpBinding

class AuthenticationFragmentSignUp : Fragment() {
    
    private  var _binding : FragmentAuthenticationSignUpBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthenticationSignUpBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        return binding.root    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.textLogin.setOnClickListener {
            // Navigate back to login
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.buttonSignup.setOnClickListener {
            signUpUser()
        }
    }

    private fun signUpUser() {
        val name = binding.editName.text.toString().trim()
        val email = binding.editEmail.text.toString().trim()
        val password = binding.editPassword.text.toString().trim()
        val confirmPassword = binding.editConfirmPassword.text.toString().trim()

        if (name.isEmpty()) {
            binding.editName.error = "Name is required"
            binding.editName.requestFocus()
            return
        }

        if (email.isEmpty()) {
            binding.editEmail.error = "Email is required"
            binding.editEmail.requestFocus()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.editEmail.error = "Valid email is required"
            binding.editEmail.requestFocus()
            return
        }

        if (password.isEmpty() || password.length < 6) {
            binding.editPassword.error = "Password must be at least 6 characters"
            binding.editPassword.requestFocus()
            return
        }

        if (password != confirmPassword) {
            binding.editConfirmPassword.error = "Passwords do not match"
            binding.editConfirmPassword.requestFocus()
            return
        }

        // Show loading or disable button if needed
        binding.buttonSignup.isEnabled = false

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.sendEmailVerification()
                        ?.addOnCompleteListener { verificationTask ->
                            if (verificationTask.isSuccessful) {
                                Toast.makeText(
                                    context,
                                    "Registration successful. Please check your email for verification.",
                                    Toast.LENGTH_LONG
                                ).show()
                                // Optionally sign out until verified
                                auth.signOut()
                                requireActivity().onBackPressedDispatcher.onBackPressed()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Failed to send verification email.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                } else {
                    binding.buttonSignup.isEnabled = true
                    Toast.makeText(
                        context,
                        "Registration failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}