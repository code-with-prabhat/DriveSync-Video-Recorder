package com.prakush.livecam.ui

import android.os.Bundle
import android.util.Patterns
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.prakush.livecam.R
import com.prakush.livecam.databinding.FragmentAuthenticationLoginBinding

class AuthenticationFragmentLogin : Fragment() {

    private var _binding: FragmentAuthenticationLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthenticationLoginBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textSignup.setOnClickListener {
            findNavController().navigate(R.id.action_Login_to_SignUp)
        }

        binding.buttonLogin.setOnClickListener {
            loginUser()
        }

        binding.buttonSecond.setOnClickListener {
            findNavController().navigate(R.id.action_Login_to_First)
        }

        binding.textForgotPassword.setOnClickListener {
            forgotPassword()
        }
    }

    private fun forgotPassword() {
        val email = binding.editEmail.text.toString().trim()
        if (email.isEmpty()) {
            binding.editEmail.error = "Enter email to reset password"
            binding.editEmail.requestFocus()
            return
        }

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(context, "Password reset email sent", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun loginUser() {
        val email = binding.editEmail.text.toString().trim()
        val password = binding.editPassword.text.toString().trim()

        if (email.isEmpty()) {
            binding.editEmail.error = "Email is required"
            binding.editEmail.requestFocus()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.editEmail.error = "Please enter a valid email"
            binding.editEmail.requestFocus()
            return
        }

        if (password.isEmpty()) {
            binding.editPassword.error = "Password is required"
            binding.editPassword.requestFocus()
            return
        }

        binding.buttonLogin.isEnabled = false

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null && user.isEmailVerified) {
                        Toast.makeText(context, "Login Successful", Toast.LENGTH_SHORT).show()
                        findNavController().navigate(R.id.action_Login_to_First)
                    } else {
                        user?.sendEmailVerification()
                        auth.signOut()
                        binding.buttonLogin.isEnabled = true
                        Toast.makeText(
                            context,
                            "Please verify your email. A new verification link has been sent.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    binding.buttonLogin.isEnabled = true
                    Toast.makeText(
                        context,
                        "Login failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
