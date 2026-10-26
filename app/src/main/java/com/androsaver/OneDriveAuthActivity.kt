package com.androsaver

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.androsaver.auth.DeviceAuthViewModel
import com.androsaver.auth.DeviceAuthorization
import com.androsaver.databinding.ActivityOneDriveAuthBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

class OneDriveAuthActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOneDriveAuthBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOneDriveAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val model = ViewModelProvider(this)[DeviceAuthViewModel::class.java]
        model.start(google = false)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    binding.progressBar.visibility = if (state.stage == DeviceAuthorization.Stage.REQUESTING) View.VISIBLE else View.GONE
                    binding.codeContainer.visibility = if (state.device != null) View.VISIBLE else View.GONE
                    state.device?.let { code ->
                        binding.verificationUrlText.text = code.verificationUrl
                        binding.userCodeText.text = code.userCode
                    }
                    binding.statusText.text = when (state.stage) {
                        DeviceAuthorization.Stage.REQUESTING -> getString(R.string.requesting_device_code)
                        DeviceAuthorization.Stage.WAITING -> getString(R.string.waiting_for_authorization)
                        DeviceAuthorization.Stage.SUCCESS -> getString(R.string.auth_success)
                        DeviceAuthorization.Stage.EXPIRED -> getString(R.string.auth_expired)
                        DeviceAuthorization.Stage.ERROR -> state.error?.let { getString(R.string.auth_error, it) }
                            ?: getString(R.string.onedrive_auth_failed)
                    }
                    if (state.stage == DeviceAuthorization.Stage.SUCCESS) { delay(1500); finish() }
                }
            }
        }
    }
}
