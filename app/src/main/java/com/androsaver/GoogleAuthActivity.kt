package com.androsaver

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.androsaver.auth.AuthResult
import com.androsaver.auth.GoogleAuthManager
import com.androsaver.databinding.ActivityGoogleAuthBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GoogleAuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGoogleAuthBinding
    private val authManager by lazy { GoogleAuthManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGoogleAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        startDeviceAuthFlow()
    }

    private fun startDeviceAuthFlow() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.statusText.text = getString(R.string.requesting_device_code)
            binding.codeContainer.visibility = View.GONE

            val deviceCode = authManager.requestDeviceCode()
            if (deviceCode == null) {
                binding.progressBar.visibility = View.GONE
                binding.statusText.text = getString(R.string.auth_failed_check_client_id)
                return@launch
            }

            binding.progressBar.visibility = View.GONE
            binding.codeContainer.visibility = View.VISIBLE
            binding.verificationUrlText.text = deviceCode.verificationUrl
            binding.userCodeText.text = deviceCode.userCode
            binding.statusText.text = getString(R.string.waiting_for_authorization)

            val expiresAt = System.currentTimeMillis() + deviceCode.expiresIn * 1000L
            val pollIntervalMs = (deviceCode.interval * 1000L).coerceAtLeast(5000L)

            while (System.currentTimeMillis() < expiresAt) {
                delay(pollIntervalMs)
                when (val result = authManager.pollForToken(deviceCode.deviceCode)) {
                    is AuthResult.Success -> {
                        binding.statusText.text = getString(R.string.auth_success)
                        binding.codeContainer.visibility = View.GONE
                        delay(1500)
                        finish()
                        return@launch
                    }
                    is AuthResult.Pending -> { /* keep polling */ }
                    is AuthResult.Expired -> {
                        binding.statusText.text = getString(R.string.auth_expired)
                        return@launch
                    }
                    is AuthResult.Error -> {
                        binding.statusText.text = getString(R.string.auth_error, result.message)
                        return@launch
                    }
                }
            }

            binding.statusText.text = getString(R.string.auth_expired)
        }
    }
}
