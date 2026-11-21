package com.sensebridge.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var adapter: BluetoothAdapter? = null
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var running = false

    private lateinit var status: TextView
    private lateinit var alert: TextView
    private lateinit var temp: TextView
    private lateinit var bpm: TextView
    private lateinit var patient: TextView
    private lateinit var command: TextView
    private lateinit var gps: TextView
    private lateinit var log: TextView
    private lateinit var deviceSpinner: Spinner
    private val logLines = ArrayDeque<String>()
    private var devices: List<BluetoothDevice> = emptyList()
    private val bg = Color.rgb(11, 15, 23)
    private val card = Color.rgb(19, 26, 38)
    private val white = Color.rgb(230, 237, 247)
    private val muted = Color.rgb(139, 155, 180)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = BluetoothAdapter.getDefaultAdapter()
        buildUi()
        requestBtPermissionIfNeeded()
        refreshPairedDevices()
        addLog("Ready. Pair with SenseBridge_Device in Android Bluetooth settings.")
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(24))
        }
        scroll.addView(page)
        setContentView(scroll)

        val heading = TextView(this).apply {
            text = "SenseBridge Monitor\nWearable Vital-Sign Tracker & Emergency Link"
            setTextColor(Color.WHITE); textSize = 22f; typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(14))
        }
        page.addView(heading)

        status = text("Bluetooth: Disconnected", 14f, muted)
        page.addView(status)
        deviceSpinner = Spinner(this)
        page.addView(deviceSpinner, matchWrap())
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        actions.addView(button("Refresh devices") { refreshPairedDevices() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        actions.addView(button("Connect") { connectSelected() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(6) })
        page.addView(actions)
        page.addView(button("Disconnect") { disconnect("Disconnected by user") }.apply { setBackgroundColor(Color.rgb(70, 80, 98)) })

        alert = text("SYSTEM NORMAL\nWaiting for ESP32 telemetry", 16f, Color.rgb(110, 231, 183), true)
        addCard(page, alert)

        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        temp = metric("BODY TEMPERATURE", "-- °C")
        bpm = metric("HEART RATE / PULSE", "-- BPM")
        metrics.addView(metricCard(temp), LinearLayout.LayoutParams(0, dp(112), 1f).apply { marginEnd = dp(6) })
        metrics.addView(metricCard(bpm), LinearLayout.LayoutParams(0, dp(112), 1f).apply { marginStart = dp(6) })
        page.addView(metrics)

        patient = text("Patient message: Ready", 17f, white, true)
        addCard(page, patient)
        command = text("Last family command: None", 16f, white)
        addCard(page, command)

        val cmdTitle = text("SEND COMMAND TO PATIENT", 13f, muted, true)
        page.addView(cmdTitle)
        val cmds = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        cmds.addView(button("💧 Water (W)") { sendCommand("W", "Want Water?") }.apply { setBackgroundColor(Color.rgb(17, 110, 85)) },
            LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginEnd = dp(4) })
        cmds.addView(button("🍚 Food (F)") { sendCommand("F", "Want Food?") }.apply { setBackgroundColor(Color.rgb(37, 89, 155)) },
            LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
        cmds.addView(button("🆘 Help (H)") { sendCommand("H", "Need Help?") }.apply { setBackgroundColor(Color.rgb(150, 45, 55)) },
            LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginStart = dp(4) })
        page.addView(cmds)

        gps = text("GPS: Waiting for NEO-6M fix", 14f, muted)
        addCard(page, gps)
        page.addView(button("Open patient location in Google Maps") { openMaps() })

        page.addView(text("LIVE DEVICE TELEMETRY & EVENT LOG", 13f, muted, true))
        log = text("No data received yet.", 12f, Color.rgb(147, 197, 253))
        log.typeface = Typeface.MONOSPACE
        addCard(page, log)
        page.addView(text("Note: Bluetooth connection must be paired first. Vital readings are for project demonstration, not medical diagnosis.", 12f, muted))
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(4), dp(8), dp(4), dp(8))
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 12f; isAllCaps = false
        setOnClickListener { action() }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun addCard(parent: LinearLayout, child: View) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(card)
        }
        box.addView(child)
        val lp = matchWrap().apply { topMargin = dp(10); bottomMargin = dp(4) }
        parent.addView(box, lp)
    }

    private fun metric(label: String, value: String) = text("$label\n$value", 18f, Color.WHITE, true)
    private fun metricCard(child: TextView) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)); setBackgroundColor(card)
        addView(child)
    }

    private fun requestBtPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 501)
        }
    }

    private fun hasBtPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun refreshPairedDevices() {
        if (!hasBtPermission()) { requestBtPermissionIfNeeded(); return }
        val a = adapter
        if (a == null) { addLog("This phone does not support Bluetooth."); return }
        if (!a.isEnabled) {
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 502)
            return
        }
        try {
            devices = a.bondedDevices?.sortedBy { it.name ?: it.address } ?: emptyList()
            val names = if (devices.isEmpty()) listOf("No paired devices — pair SenseBridge_Device in Settings") else
                devices.map { "${it.name ?: "Unknown"}  (${it.address})" }
            deviceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
            addLog("Found ${devices.size} paired Bluetooth device(s).")
        } catch (e: SecurityException) { addLog("Bluetooth permission required: ${e.message}") }
    }

    private fun connectSelected() {
        if (!hasBtPermission()) { requestBtPermissionIfNeeded(); return }
        if (devices.isEmpty()) { addLog("No paired device. Pair SenseBridge_Device in Android Settings first."); return }
        val d = devices.getOrNull(deviceSpinner.selectedItemPosition) ?: return
        disconnect(null)
        status.text = "Bluetooth: Connecting to ${d.name ?: d.address}..."
        thread {
            try {
                val s = d.createRfcommSocketToServiceRecord(sppUuid)
                socket = s
                s.connect()
                running = true
                runOnUiThread {
                    status.text = "Bluetooth: Connected to ${d.name ?: "SenseBridge"}"
                    addLog("Connected. Receiving ESP32 data...")
                }
                readLoop(s)
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Bluetooth: Disconnected"
                    addLog("Connection failed: ${e.message}. Check pairing and ESP32 power.")
                }
                closeSocket()
            }
        }
    }

    private fun readLoop(s: BluetoothSocket) {
        try {
            val reader = BufferedReader(InputStreamReader(s.inputStream))
            while (running) {
                val line = reader.readLine() ?: break
                runOnUiThread { parseLine(line.trim()) }
            }
        } catch (e: Exception) {
            if (running) runOnUiThread { addLog("Read error: ${e.message}") }
        } finally {
            closeSocket()
            runOnUiThread { status.text = "Bluetooth: Disconnected" }
        }
    }

    private fun parseLine(line: String) {
        if (line.isBlank()) return
        addLog(line)
        when {
            line.startsWith("ALERT:FALL_DETECTED") -> emergency("FALL DETECTED", "MPU6050 fall/impact alert received.")
            line.startsWith("ALERT:SHOCK_DETECTED") -> emergency("SHOCK DETECTED", "KY-031 shock alert received.")
            line.startsWith("PATIENT_MSG:HELP") -> emergency("PATIENT NEEDS HELP", "Patient pressed the HELP button.")
            line.startsWith("PATIENT_MSG:") -> patient.text = "Patient message: " + line.substringAfter(":")
            line.startsWith("CMD_ACK:") -> addLog("ESP32 acknowledged command: ${line.substringAfter(":")}")
            line.startsWith("STATUS:") -> addLog(line.substringAfter(":"))
            line.startsWith("STATE|") -> {
                val data = mutableMapOf<String, String>()
                line.split("|").drop(1).forEach {
                    val p = it.indexOf(':')
                    if (p > 0) data[it.substring(0, p)] = it.substring(p + 1)
                }
                data["TEMP"]?.let { temp.text = "BODY TEMPERATURE\n$it °C" }
                data["BPM"]?.let { bpm.text = "HEART RATE / PULSE\n$it BPM" }
                data["MSG"]?.let { patient.text = "Patient message: $it" }
                data["CMD"]?.let { command.text = "Last family command: $it" }
                val lat = data["LAT"]; val lng = data["LNG"]
                if (!lat.isNullOrBlank() && !lng.isNullOrBlank() && lat != "N/A" && lng != "N/A") {
                    gps.text = "GPS: $lat, $lng\nLocation received from ESP32"
                    lastLat = lat; lastLng = lng
                } else gps.text = "GPS: Waiting for NEO-6M fix"
                if (data["MSG"]?.contains("HELP", true) == true ||
                    data["MSG"]?.contains("FALL", true) == true ||
                    data["MSG"]?.contains("SHOCK", true) == true) {
                    emergency("ALERT: ${data["MSG"]}", "Immediate attention recommended.")
                }
            }
        }
    }

    private var lastLat: String? = null
    private var lastLng: String? = null

    private fun emergency(title: String, detail: String) {
        alert.text = "🚨 $title\n$detail"
        alert.setTextColor(Color.rgb(252, 165, 165))
        addLog("EMERGENCY: $title")
    }

    private fun sendCommand(code: String, label: String) {
        val s = socket
        if (s == null || !s.isConnected) { addLog("Cannot send: connect to SenseBridge_Device first."); return }
        thread {
            try {
                s.outputStream.write(code.toByteArray(Charsets.UTF_8))
                s.outputStream.flush()
                runOnUiThread {
                    command.text = "Last family command: $label"
                    addLog("Sent command $code ($label)")
                }
            } catch (e: Exception) { runOnUiThread { addLog("Send failed: ${e.message}") } }
        }
    }

    private fun openMaps() {
        val lat = lastLat; val lng = lastLng
        if (lat == null || lng == null) { addLog("No valid GPS coordinates received yet."); return }
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps?q=$lat,$lng")))
    }

    private fun addLog(message: String) {
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        logLines.addFirst("[$stamp] $message")
        while (logLines.size > 35) logLines.removeLast()
        if (::log.isInitialized) log.text = logLines.joinToString("\n")
    }

    private fun disconnect(message: String?) {
        running = false
        closeSocket()
        if (message != null) addLog(message)
        if (::status.isInitialized) status.text = "Bluetooth: Disconnected"
    }

    private fun closeSocket() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        running = false
    }

    override fun onDestroy() {
        disconnect(null)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 502 && resultCode == RESULT_OK) refreshPairedDevices()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 501 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) refreshPairedDevices()
        else if (requestCode == 501) addLog("Bluetooth Connect permission denied.")
    }
}
