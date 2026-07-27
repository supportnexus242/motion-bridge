package com.motionenergy.bridge

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.motionenergy.bridge.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("motion_bridge", MODE_PRIVATE)
        loadExisting()

        binding.saveBtn.setOnClickListener { saveSettings() }
        binding.cancelBtn.setOnClickListener { finish() }
    }

    private fun loadExisting() {
        binding.deviceNameInput.setText(prefs.getString("device_name", ""))
        binding.deviceIdInput.setText(prefs.getString("device_id", ""))
        binding.secretInput.setText(prefs.getString("secret", ""))
        binding.urlInput.setText(prefs.getString("supabase_url", ""))

        when (prefs.getString("network", "MTN")) {
            "TELECEL" -> binding.telecelRadio.isChecked = true
            else -> binding.mtnRadio.isChecked = true
        }
    }

    private fun saveSettings() {
        val deviceName = binding.deviceNameInput.text.toString().trim()
        val deviceId = binding.deviceIdInput.text.toString().trim()
        val secret = binding.secretInput.text.toString().trim()
        val url = binding.urlInput.text.toString().trim().trimEnd('/')
        val pin = binding.pinInput.text.toString().trim()

        if (deviceId.isEmpty() || secret.isEmpty() || url.isEmpty()) {
            Toast.makeText(this, "Device ID, Secret and Supabase URL are required", Toast.LENGTH_LONG).show()
            return
        }

        if (!url.startsWith("https://")) {
            Toast.makeText(this, "Supabase URL must start with https://", Toast.LENGTH_LONG).show()
            return
        }

        val network = if (binding.telecelRadio.isChecked) "TELECEL" else "MTN"

        val editor = prefs.edit()
        editor.putString("device_name", deviceName)
        editor.putString("device_id", deviceId)
        editor.putString("secret", secret)
        editor.putString("supabase_url", url)
        editor.putString("network", network)

        if (pin.isNotEmpty()) {
            if (pin.length < 4) {
                Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_LONG).show()
                return
            }
            editor.putString("pin_hash", pin.hashCode().toString())
        } else {
            editor.remove("pin_hash")
        }

        editor.apply()

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }
}
