package com.motionenergy.bridge

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.motionenergy.bridge.databinding.ActivityMainBinding
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("motion_bridge", MODE_PRIVATE)

        if (!prefs.contains("supabase_url")) {
            openSettings()
            return
        }

        if (prefs.contains("pin_hash") && !intent.getBooleanExtra("pin_verified", false)) {
            promptPin()
            return
        }

        setupUI()
        requestPermissions()
        startKeepAliveService()
        refreshDashboard()
    }

    private fun promptPin() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pin, null)
        val pinInput = dialogView.findViewById<android.widget.EditText>(R.id.pinInput)
        
        AlertDialog.Builder(this)
            .setTitle("Enter PIN")
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Unlock") { _, _ ->
                val entered = pinInput.text.toString()
                val stored = prefs.getString("pin_hash", "")
                if (entered.hashCode().toString() == stored) {
                    setupUI()
                    requestPermissions()
                    startKeepAliveService()
                    refreshDashboard()
                } else {
                    Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .show()
    }

    private fun setupUI() {
        binding.appTitle.text = "⚡ Motion Bridge"
        binding.deviceName.text = "Device: ${prefs.getString("device_name", "Unnamed")}"
        binding.networkText.text = "Network: ${prefs.getString("network", "MTN")}"

        binding.settingsBtn.setOnClickListener { openSettings() }
        
        binding.testBtn.setOnClickListener { sendTestSms() }
        
        binding.refreshBtn.setOnClickListener { 
            refreshDashboard()
            Toast.makeText(this, "Refreshed", Toast.LENGTH_SHORT).show()
        }

        binding.batteryBtn.setOnClickListener { requestBatteryOptimization() }

        binding.toggleActive.isChecked = prefs.getBoolean("active", true)
        binding.toggleActive.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("active", checked).apply()
            Toast.makeText(this, if (checked) "Active" else "Paused", Toast.LENGTH_SHORT).show()
            updateStatusIndicator()
        }

        updateStatusIndicator()
    }

    private fun updateStatusIndicator() {
        val active = prefs.getBoolean("active", true)
        binding.statusIndicator.text = if (active) "🟢 ONLINE" else "🔴 PAUSED"
        binding.statusIndicator.setTextColor(
            if (active) 0xFF10B981.toInt() else 0xFFF43F5E.toInt()
        )
    }

    private fun refreshDashboard() {
        val smsCount = prefs.getInt("sms_count_today", 0)
        val totalAmount = prefs.getFloat("total_amount_today", 0f)
        val lastSms = prefs.getString("last_sms_time", "Never")
        val lastServerPing = prefs.getString("last_server_ping", "Never")

        binding.smsCount.text = smsCount.toString()
        binding.totalAmount.text = "GH ${String.format("%.2f", totalAmount)}"
        binding.lastSms.text = "Last SMS: $lastSms"
        binding.lastPing.text = "Last sync: $lastServerPing"
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECEIVE_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_SMS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
        }
    }

    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } else {
                Toast.makeText(this, "Already optimized ✅", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startKeepAliveService() {
        val intent = Intent(this, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun sendTestSms() {
        scope.launch(Dispatchers.IO) {
            val testMessage = "You have received GHS 10.00 from 233201234567 TEST USER. Transaction ID: TESTMP123456. Balance: GHS 100.00"
            val result = SmsUploader(this@MainActivity).uploadSms(testMessage)
            
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, 
                    if (result) "✅ Test sent successfully!" else "❌ Test failed - check settings",
                    Toast.LENGTH_LONG).show()
                refreshDashboard()
            }
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun onResume() {
        super.onResume()
        if (prefs.contains("supabase_url")) refreshDashboard()
    }
}
