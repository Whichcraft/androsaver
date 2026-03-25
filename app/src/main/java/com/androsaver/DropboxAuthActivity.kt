package com.androsaver

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.androsaver.auth.AuthResult
import com.androsaver.auth.DropboxAuthManager
import com.androsaver.databinding.ActivityDropboxAuthBinding
import kotlinx.coroutines.launch

class DropboxAuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDropboxAuthBinding
    private val authManager by lazy { DropboxAuthManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDropboxAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val authUrl = authManager.buildAuthUrl()
        if (authUrl == null) {
            Toast.makeText(this, R.string.dropbox_app_key_required, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.verificationUrlText.text = authUrl
        binding.submitButton.setOnClickListener { submitCode() }
    }

    private fun submitCode() {
        val code = binding.codeEdit.text.toString().trim()
        if (code.isEmpty()) {
            Toast.makeText(this, R.string.dropbox_code_required, Toast.LENGTH_SHORT).show()
            return
        }

        binding.submitButton.isEnabled = false
        binding.statusText.visibility = View.VISIBLE
        binding.statusText.text = getString(R.string.dropbox_exchanging_code)

        lifecycleScope.launch {
            when (val result = authManager.exchangeCode(code)) {
                is AuthResult.Success -> {
                    binding.statusText.text = getString(R.string.auth_success)
                    kotlinx.coroutines.delay(1200)
                    finish()
                }
                is AuthResult.Error -> {
                    binding.statusText.text = getString(R.string.auth_error, result.message)
                    binding.submitButton.isEnabled = true
                }
                else -> {
                    binding.statusText.text = getString(R.string.auth_error, "Unexpected response")
                    binding.submitButton.isEnabled = true
                }
            }
        }
    }
}
