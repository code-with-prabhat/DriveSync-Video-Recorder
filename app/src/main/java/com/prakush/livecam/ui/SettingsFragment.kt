package com.prakush.livecam.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.prakush.livecam.R
import com.prakush.livecam.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        @Suppress("DEPRECATION")
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        if (task.isSuccessful) {
            updateDriveStatus(task.result)
            Toast.makeText(requireContext(), "Google Sign-in successful", Toast.LENGTH_SHORT).show()
        } else {
            binding.textViewDriveStatus.setText(R.string.drive_status_error)
            Toast.makeText(requireContext(), "Google Sign-in failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        checkCurrentDriveStatus()

        binding.buttonConnectDrive.setOnClickListener {
            requestSignIn()
        }

        binding.textViewAbout.setOnClickListener {
            Toast.makeText(requireContext(), "About clicked", Toast.LENGTH_SHORT).show()
        }

        binding.textViewPrivacyPolicy.setOnClickListener {
            Toast.makeText(requireContext(), "Privacy Policy clicked", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkCurrentDriveStatus() {
        @Suppress("DEPRECATION")
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        updateDriveStatus(account)
    }

    private fun updateDriveStatus(account: GoogleSignInAccount?) {
        if (account != null && account.grantedScopes.contains(Scope(DriveScopes.DRIVE_FILE))) {
            binding.textViewDriveStatus.setText(R.string.drive_status_connected)
            binding.buttonConnectDrive.isEnabled = false
        } else {
            binding.textViewDriveStatus.setText(R.string.drive_status_not_connected)
            binding.buttonConnectDrive.isEnabled = true
        }
    }

    private fun requestSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        @Suppress("DEPRECATION")
        val client = GoogleSignIn.getClient(requireActivity(), gso)
        signInLauncher.launch(client.signInIntent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}