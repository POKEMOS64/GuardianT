/*
 * Copyright (C) 2026 GuardianT Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ru.mirzadach.guardts

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.os.Bundle
import android.view.View
import android.content.Context
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.EditText
import android.text.InputType
import com.google.android.material.snackbar.Snackbar
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import android.os.Handler
import android.os.Looper
import ru.mirzadach.guardts.databinding.ActivityMainBinding
import java.util.concurrent.ConcurrentHashMap
import android.media.MediaPlayer
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.activity.result.contract.ActivityResultContracts
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import android.content.BroadcastReceiver
import android.content.IntentFilter

class MainActivity : AppCompatActivity() {

    enum class Tab { CHATS, TRUNK, SETTINGS, PROFILE }

    private lateinit var binding: ActivityMainBinding
    private var webServer: ProvisioningServer? = null
    private var lastEspIp: String? = null
    private var currentTab = Tab.CHATS
    private var isChatOpen = false
    private var activeContact: Contact? = null
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var filesAdapter: FilesAdapter
    private lateinit var contactsAdapter: ContactAdapter
    
    private val uinToMacMap = ConcurrentHashMap<String, String>()
    private val pollHandler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null
    private var isPinVerified = false

    @Volatile private var isUploadCancelled = false

    private val uploadCancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CANCEL_UPLOAD) {
                isUploadCancelled = true
            }
        }
    }

    private var pendingUploadUri: android.net.Uri? = null

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            uploadFileProcess(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        val isTerminal = sharedPref.getBoolean(PREF_IS_TERMINAL_THEME, false)

        if (isTerminal) setTheme(R.style.Theme_GardianT_Terminal)
        else setTheme(R.style.Theme_GardianT)

        super.onCreate(savedInstanceState)

        // Используем ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Инициализируем адаптеры ДО setupViews
        chatAdapter = ChatAdapter()
        filesAdapter = FilesAdapter(
            onFileClick = { file -> downloadAndDecryptFile(file) },
            onFileLongClick = { file -> showDeleteFileDialog(file) }
        )
        contactsAdapter = ContactAdapter(
            onClick = { contact -> openChat(contact) },
            onLongClick = { contact -> showRenameDialog(contact) }
        )

        setupViews(savedInstanceState)
        setupAnimations()
        setupClickListeners()
        createNotificationChannel()
        checkNotificationPermission()

        handleIncomingIntent(intent)
    }

    private fun setupViews(savedInstanceState: Bundle?) {
        binding.chatsListRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.chatsListRecyclerView.adapter = contactsAdapter

        try {
            binding.chatRecyclerView.layoutManager = LinearLayoutManager(this)
            binding.chatRecyclerView.adapter = chatAdapter

            binding.chatRecyclerView.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
                if (bottom < oldBottom) {
                    binding.chatRecyclerView.postDelayed({
                        if (chatAdapter.itemCount > 0) {
                            binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                        }
                    }, 100)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Chat views not found in XML. Please update layout.", e)
        }

        binding.filesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.filesRecyclerView.adapter = filesAdapter

        binding.sendButton.setOnClickListener {
            val message = binding.messageEditText.text?.toString() ?: ""
            if (message.isNotBlank()) {
                sendMessage(message)
                binding.messageEditText.text?.clear()
            }
        }

        binding.clearChatButton.setOnClickListener {
            activeContact?.let { contact ->
                clearChatHistory(contact.mac)
            }
        }

        if (BuildConfig.DEBUG) {
            binding.ssidEditText.setText("Домашний Wi-Fi")
            binding.serverUrlEditText.setText("http://185.251.89.73/")
        }

        loadSettings()

        setupProfile()

        if (savedInstanceState != null) {
            lastEspIp = savedInstanceState.getString("lastEspIp")
            try {
                currentTab = Tab.valueOf(savedInstanceState.getString("currentTab", Tab.CHATS.name))
            } catch (e: Exception) { currentTab = Tab.CHATS }
            isChatOpen = savedInstanceState.getBoolean("isChatOpen", false)
            isPinVerified = savedInstanceState.getBoolean("isPinVerified", false)

            activeContact = if (Build.VERSION.SDK_INT >= 33) {
                savedInstanceState.getSerializable("activeContact", Contact::class.java)
            } else {
                @Suppress("DEPRECATION")
                savedInstanceState.getSerializable("activeContact") as? Contact
            }

            val navId = when (currentTab) {
                Tab.CHATS -> R.id.nav_chats
                Tab.TRUNK -> R.id.nav_trunk
                Tab.SETTINGS -> R.id.nav_settings
                Tab.PROFILE -> R.id.nav_profile
            }
            if (binding.bottomNavigation.selectedItemId != navId) {
                binding.bottomNavigation.selectedItemId = navId
            }

            if (isChatOpen && activeContact != null) {
                if (currentTab == Tab.TRUNK) openTrunk(activeContact!!) else openChat(activeContact!!)
            }
        }

        if (lastEspIp == null) {
            discoverEspDevices()
        } else {
            fetchEspInfo(lastEspIp!!)
        }

        updateUIState()

        startMessagePolling()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isChatOpen) {
                    isChatOpen = false
                    activeContact = null
                    updateUIState()
                } else if (currentTab != Tab.CHATS) {
                    binding.bottomNavigation.selectedItemId = R.id.nav_chats
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun loadMockContacts() {
        val mockContacts = listOf(
            Contact("ESP8266 Device", "AA:BB:CC:DD:EE:FF", "Устройство в сети", "14:00", null),
            Contact("Alice", "11:22:33:44:55:66", "Привет, как дела?", "13:45", null),
            Contact("Bob", "77:88:99:00:11:22", "Секретный ключ обновлен", "Вчера", null)
        )
        contactsAdapter.setContacts(mockContacts)
    }

    private fun openChat(contact: Contact) {
        if (pendingUploadUri != null) {
            showConfirmUploadDialog(contact, pendingUploadUri!!)
            return
        }

        if (currentTab == Tab.TRUNK) {
            openTrunk(contact)
        } else {
            activeContact = contact
            isChatOpen = true

            chatAdapter.clear()
            val history = loadChatHistory(contact.mac)
            for (msg in history) {
                chatAdapter.addMessage(msg)
            }

            updateUIState()
        }
    }

    private fun openTrunk(contact: Contact) {
        activeContact = contact
        isChatOpen = true

        val history = loadChatHistory(contact.mac)
        val files = history.filter { it.contains("FILE|") || it.contains("[Файл]") }
            .map { parseFileMessage(it) }

        filesAdapter.setFiles(files)

        updateUIState()
    }

    private fun sendMessage(text: String) {
        val msg = "Вы: $text"
        chatAdapter.addMessage(msg)
        val targetMac = activeContact?.mac ?: "FF:FF:FF:FF:FF:FF"
        sendMessageToEsp(lastEspIp ?: "192.168.4.1", targetMac, text)

        saveMessageToHistory(targetMac, msg)
    }

    private fun setupAnimations() {
        try {
            val fadeIn = AnimatorInflater.loadAnimator(this, R.animator.fade_in)
            fadeIn.setTarget(binding.statusCard)
            fadeIn.start()
        } catch (e: Exception) {
            Log.w(TAG, "Animation failed to load", e)
        }
    }

    private fun setupClickListeners() {
        binding.toggleServerButton.setOnClickListener {
            toggleServer()
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_chats -> {
                    currentTab = Tab.CHATS
                    if (lastEspIp == null) discoverEspDevices() else fetchContactsFromEsp()
                }
                R.id.nav_trunk -> {
                    currentTab = Tab.TRUNK
                    fetchContactsFromEsp()
                }
                R.id.nav_settings -> currentTab = Tab.SETTINGS
                R.id.nav_profile -> currentTab = Tab.PROFILE
            }
            isChatOpen = false
            updateUIState()
            true
        }

        binding.scanNetworkButton.setOnClickListener {
            discoverEspDevices()
        }

        binding.saveIpButton.setOnClickListener {
            val ip = binding.manualIpEditText.text.toString()
            if (ip.isNotBlank()) {
                lastEspIp = ip

                saveManualIp(ip)

                showSuccess("IP адрес установлен: $ip")
            } else {
                showError("Введите корректный IP")
            }
        }

        binding.navToggleButton.setOnClickListener {
            val isVisible = binding.bottomNavCard.visibility == View.VISIBLE
            binding.bottomNavCard.visibility = if (isVisible) View.GONE else View.VISIBLE
        }

        binding.advancedSettingsButton.setOnClickListener {
            binding.serverLayout.visibility = View.GONE
            binding.dangerZoneLayout.visibility = View.VISIBLE
        }

        binding.closeDangerZoneButton.setOnClickListener {
            binding.dangerZoneLayout.visibility = View.GONE
            binding.serverLayout.visibility = View.VISIBLE
        }

        binding.resetKeysButton.setOnClickListener {
            showResetConfirmationDialog()
        }

        binding.themeSwitchButton.setOnClickListener {
            val sharedPref = getPreferences(Context.MODE_PRIVATE)
            val isTerminal = sharedPref.getBoolean(PREF_IS_TERMINAL_THEME, false)

            with(sharedPref.edit()) {
                putBoolean(PREF_IS_TERMINAL_THEME, !isTerminal)
                apply()
            }
            recreate()
        }

        binding.uploadFileButton.setOnClickListener {
            if (activeContact?.fileKey.isNullOrEmpty()) {
                showError("У этого контакта нет ключа для файлов. Пересопрягите устройства.")
            } else {
                pickFileLauncher.launch("*/*")
            }
        }
    }

    private fun discoverEspDevices() {
        runOnUiThread { binding.statusTextView.text = "Сканирование сети..." }
        Thread {
            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                socket.soTimeout = 500

                val sendData = "DISCOVER_GARDIANT".toByteArray()

                try {
                    val broadcastPacket = DatagramPacket(
                        sendData, sendData.size,
                        InetAddress.getByName("255.255.255.255"), 4211
                    )
                    socket.send(broadcastPacket)
                } catch (e: Exception) {
                    Log.e(TAG, "Broadcast send failed", e)
                }

                val localIps = getLocalIpAddresses()
                for (localIp in localIps) {
                    val prefix = localIp.substring(0, localIp.lastIndexOf('.') + 1)
                    for (i in 1..254) {
                        val targetIp = prefix + i
                        if (targetIp == localIp) continue

                        try {
                            val packet = DatagramPacket(
                                sendData, sendData.size,
                                InetAddress.getByName(targetIp), 4211
                            )
                            socket.send(packet)
                        } catch (e: Exception) {}

                        if (i % 50 == 0) Thread.sleep(5)
                    }
                }

                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < 3000) {
                    val recvBuf = ByteArray(1024)
                    val receivePacket = DatagramPacket(recvBuf, recvBuf.size)
                    try {
                        socket.receive(receivePacket)
                        val message = String(receivePacket.data, 0, receivePacket.length)
                        if (message.contains("GARDIANT_HERE")) {
                            val ip = receivePacket.address.hostAddress
                            if (ip != null) {
                                runOnUiThread {
                                    lastEspIp = ip
                                    if (currentTab == Tab.CHATS) binding.statusTextView.text = "Найдено устройство: $ip"
                                    binding.manualIpEditText.setText(ip)
                                    fetchEspInfo(ip)
                                    showSuccess("Устройство найдено: $ip")
                                }
                                break
                            }
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                    }
                }
                socket.close()

                if (lastEspIp == null) {
                    runOnUiThread { binding.statusTextView.text = "Устройства не найдены" }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Discovery failed", e)
                runOnUiThread { binding.statusTextView.text = "Ошибка поиска сети" }
            }
        }.start()
    }

    private fun getLocalIpAddresses(): List<String> {
        val ips = mutableListOf<String>()
        try {
            val en = java.net.NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is java.net.Inet4Address) {
                        inetAddress.hostAddress?.let { ips.add(it) }
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, ex.toString())
        }
        return ips
    }

    private fun updateUIState() {
        binding.apply {
            serverLayout.visibility = View.GONE
            chatLayout.visibility = View.GONE
            chatsListRecyclerView.visibility = View.GONE
            profileLayout.visibility = View.GONE
            dangerZoneLayout.visibility = View.GONE
            filesLayout.visibility = View.GONE

            if (isChatOpen) {
                bottomNavCard.visibility = View.GONE

                if (currentTab == Tab.TRUNK) {
                    filesLayout.visibility = View.VISIBLE
                    filesHeader.text = "Багаж: ${activeContact?.name}"
                } else {
                    chatLayout.visibility = View.VISIBLE
                    statusTextView.text = activeContact?.name ?: "Чат"
                }
            } else {
                bottomNavCard.visibility = View.VISIBLE

                when (currentTab) {
                    Tab.CHATS -> {
                        chatsListRecyclerView.visibility = View.VISIBLE
                        statusTextView.text = if (lastEspIp != null) "Подключено к ESP: $lastEspIp" else "GardianT"
                        fetchContactsFromEsp()
                    }
                    Tab.TRUNK -> {
                        chatsListRecyclerView.visibility = View.VISIBLE
                        statusTextView.text = "Выберите багажник"
                    }
                    Tab.SETTINGS -> {
                        serverLayout.visibility = View.VISIBLE
                        statusTextView.text = "Управление устройством"
                    }
                    Tab.PROFILE -> {
                        profileLayout.visibility = View.VISIBLE
                        statusTextView.text = "Профиль"
                    }
                }
            }
        }
    }

    private fun fetchContactsFromEsp() {
        val ip = lastEspIp ?: return
        Thread {
            try {
                val url = URL("http://$ip/contacts")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 2000

                if (connection.responseCode == 200) {
                    val stream = connection.inputStream
                    val response = stream.bufferedReader().use { it.readText() }
                    val jsonArray = org.json.JSONArray(response)

                    val newContacts = ArrayList<Contact>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val mac = obj.getString("mac")
                        val fileKey = obj.optString("file_key", "")

                        val uin = obj.optString("uin", "")
                        if (uin.isNotEmpty()) uinToMacMap[uin] = mac

                        val alias = getAlias(mac)

                        val displayName = if (!alias.isNullOrEmpty()) alias else (if (uin.isNotEmpty()) "ID: $uin" else obj.optString("name", "Unknown Device"))

                        newContacts.add(Contact(displayName, mac, "Сопряжено", "", fileKey))
                    }

                    runOnUiThread {
                        contactsAdapter.setContacts(newContacts)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching contacts", e)
            }
        }.start()
    }

    private fun startMessagePolling() {
        pollRunnable = object : Runnable {
            override fun run() {
                fetchMessagesFromEsp()
                pollHandler.postDelayed(this, 3000)
            }
        }
        pollHandler.post(pollRunnable!!)
    }

    private fun fetchMessagesFromEsp() {
        val ip = lastEspIp ?: return
        Thread {
            try {
                val url = URL("http://$ip/messages")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 1000
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = org.json.JSONArray(response)

                    if (jsonArray.length() > 0) {
                        runOnUiThread {
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val fromUin = obj.getString("from")
                                val text = obj.getString("text")
                                handleIncomingMessage(fromUin, text)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
            }
        }.start()
    }

    private fun handleIncomingMessage(fromUin: String, text: String) {
        if (fromUin == "SYSTEM") {
            runOnUiThread { showSuccess("Система: $text") }
            return
        }

        val mac = uinToMacMap[fromUin] ?: return

        val formattedMsg = "$fromUin: $text"

        if (text.startsWith("FILE|") && currentTab == Tab.TRUNK && activeContact?.mac == mac) {
            val fileItem = parseFileMessage(formattedMsg)
            filesAdapter.addFile(fileItem)
        }

        saveMessageToHistory(mac, formattedMsg)

        if (isChatOpen && activeContact?.mac == mac) {
            chatAdapter.addMessage(formattedMsg)
            playNotificationSound()
        } else {
            showInfo("Новое сообщение от $fromUin")
            playNotificationSound()
            showSystemNotification("Новое сообщение от $fromUin", text)
        }
    }

    private fun fetchEspInfo(ip: String) {
        Thread {
            try {
                val url = URL("http://$ip/")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 1000
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val id = json.optString("id")
                    val battery = json.optInt("battery", -1)

                    if (id.isNotEmpty()) {
                        val sharedPref = getPreferences(Context.MODE_PRIVATE)
                        with(sharedPref.edit()) {
                            putString(PREF_USER_ID, id)
                            apply()
                        }
                        runOnUiThread {
                            val formattedId = id.chunked(3).joinToString("-")
                            binding.userIdTextView.text = formattedId

                            if (battery >= 0 && currentTab == Tab.CHATS && !isChatOpen) {
                                binding.statusTextView.text = "ESP: $ip  |  Заряд: $battery%"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch ESP info", e)
            }
        }.start()
    }

    private fun showRenameDialog(contact: Contact) {
        val input = android.widget.EditText(this)
        input.hint = "Введите имя"
        input.setText(if (contact.name.startsWith("ID:")) "" else contact.name)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Псевдоним для контакта")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = input.text.toString()
                if (newName.isNotBlank()) {
                    saveAlias(contact.mac, newName)
                    fetchContactsFromEsp()
                }
            }
            .setNegativeButton("Отмена", null)
            .setNeutralButton("Сбросить") { _, _ ->
                saveAlias(contact.mac, "")
                fetchContactsFromEsp()
            }
            .show()
    }

    private fun toggleServer() {
        if (webServer != null && webServer!!.isAlive) {
            stopServer()
        } else {
            startServer()
        }
    }

    private fun startServer() {
        val ssid = binding.ssidEditText.text.toString()
        val pass = binding.passwordEditText.text.toString()
        var url = binding.serverUrlEditText.text.toString().trim()
        if (url.endsWith("/")) {
            url = url.substring(0, url.length - 1)
        }

        when {
            ssid.isBlank() -> {
                showError("Укажите имя Wi-Fi сети")
                binding.ssidEditText.requestFocus()
                return
            }
            url.isBlank() -> {
                showError("Укажите URL сервера")
                binding.serverUrlEditText.requestFocus()
                return
            }
        }

        saveSettings(ssid, pass, url)

        try {
            webServer = ProvisioningServer(SERVER_PORT, ssid, pass, url) { ip ->
                runOnUiThread {
                    lastEspIp = ip
                    fetchEspInfo(ip)
                    showSuccess("Устройство подключено: $ip")
                    if (currentTab == Tab.CHATS && !isChatOpen) binding.statusTextView.text = "Подключено к ESP: $ip"
                }
            }
            webServer?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)

            updateServerStatus(true)
            showSuccess("Сервер запущен на порту $SERVER_PORT")
            showLoading(true)

        } catch (e: IOException) {
            Log.e(TAG, "Failed to start server", e)
            showError("Не удалось запустить сервер. Порт $SERVER_PORT занят")
        }
    }

    private fun stopServer() {
        webServer?.stop()
        webServer = null
        updateServerStatus(false)
        showLoading(false)
        showInfo("Сервер остановлен")
    }

    private fun updateServerStatus(isRunning: Boolean) {
        binding.apply {
            if (isRunning) {
                statusTextView.text = "Статус: Сервер запущен. Ожидание подключения..."
                toggleServerButton.text = "Остановить настройку"
                toggleServerButton.setIconResource(R.drawable.ic_stop)
                statusIcon.setImageResource(R.drawable.ic_server_active)
                statusIcon.imageTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.success)
                statusCard.strokeColor = ContextCompat.getColor(this@MainActivity, R.color.success)
            } else {
                statusTextView.text = "Статус: Сервер выключен"
                toggleServerButton.text = "Начать настройку"
                toggleServerButton.setIconResource(R.drawable.ic_play)
                statusIcon.setImageResource(R.drawable.ic_info)
                statusIcon.imageTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.primary)
                statusCard.strokeColor = ContextCompat.getColor(this@MainActivity, R.color.outline)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.apply {
            if (show) {
                statusIndicator.visibility = View.VISIBLE
                statusIcon.visibility = View.GONE
            } else {
                statusIndicator.visibility = View.GONE
                statusIcon.visibility = View.VISIBLE
            }
        }
    }

    private fun saveSettings(ssid: String, pass: String, url: String) {
        val sharedPref = getPreferences(Context.MODE_PRIVATE) ?: return
        with(sharedPref.edit()) {
            putString(PREF_SSID, ssid)
            putString(PREF_PASS, pass)
            putString(PREF_URL, url)
            apply()
        }
    }

    private fun saveManualIp(ip: String) {
        val sharedPref = getPreferences(Context.MODE_PRIVATE) ?: return
        with(sharedPref.edit()) {
            putString(PREF_MANUAL_IP, ip)
            apply()
        }
    }

    private fun loadSettings() {
        val sharedPref = getPreferences(Context.MODE_PRIVATE) ?: return
        val ssid = sharedPref.getString(PREF_SSID, "")
        val pass = sharedPref.getString(PREF_PASS, "")
        val url = sharedPref.getString(PREF_URL, "")
        val manualIp = sharedPref.getString(PREF_MANUAL_IP, "")

        if (!ssid.isNullOrEmpty()) binding.ssidEditText.setText(ssid)
        if (!pass.isNullOrEmpty()) binding.passwordEditText.setText(pass)
        if (!url.isNullOrEmpty()) binding.serverUrlEditText.setText(url)

        if (!manualIp.isNullOrEmpty()) {
            binding.manualIpEditText.setText(manualIp)
            lastEspIp = manualIp
        }
    }

    private fun saveAlias(mac: String, alias: String) {
        val sharedPref = getPreferences(Context.MODE_PRIVATE) ?: return
        with(sharedPref.edit()) {
            if (alias.isEmpty()) remove(PREF_ALIAS_PREFIX + mac)
            else putString(PREF_ALIAS_PREFIX + mac, alias)
            apply()
        }
    }

    private fun getAlias(mac: String): String? {
        val sharedPref = getPreferences(Context.MODE_PRIVATE) ?: return null
        return sharedPref.getString(PREF_ALIAS_PREFIX + mac, null)
    }

    private fun showResetConfirmationDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Сброс ключей")
            .setMessage("Это удалит все сопряжения с другими устройствами на ESP. Вам придется заново провести процедуру сопряжения. Продолжить?")
            .setPositiveButton("Сбросить") { _, _ ->
                resetEspKeys()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun resetEspKeys() {
        val ip = lastEspIp ?: return showError("ESP не подключена или IP не найден")
        Thread {
            try {
                val url = URL("http://$ip/reset_pairing")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 3000

                if (connection.responseCode == 200) {
                    runOnUiThread {
                        showSuccess("Ключи сброшены. ESP перезагружается...")
                        clearAllLocalData()
                    }
                } else {
                    runOnUiThread { showError("Ошибка сброса: ${connection.responseCode}") }
                }
            } catch (e: Exception) {
                runOnUiThread { showError("Ошибка соединения с ESP") }
            }
        }.start()
    }

    private fun clearAllLocalData() {
        contactsAdapter.setContacts(emptyList())
        uinToMacMap.clear()
        chatAdapter.clear()
        activeContact = null
        isChatOpen = false

        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        val allEntries = sharedPref.all
        for ((key, _) in allEntries) {
            if (key.startsWith("chat_history_") || key.startsWith(PREF_ALIAS_PREFIX)) {
                editor.remove(key)
            }
        }
        editor.apply()

        updateUIState()
    }

    private fun saveMessageToHistory(mac: String, message: String) {
        val history = loadChatHistory(mac)
        history.add(message)
        saveHistoryList(mac, history)
    }

    private fun loadChatHistory(mac: String): ArrayList<String> {
        val sharedPref = getPreferences(Context.MODE_PRIVATE) ?: return ArrayList()
        val jsonString = sharedPref.getString("chat_history_$mac", null) ?: return ArrayList()

        val list = ArrayList<String>()
        try {
            val jsonArray = org.json.JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
        } catch (e: Exception) {}
        return list
    }

    private fun saveHistoryList(mac: String, list: ArrayList<String>) {
        val sharedPref = getPreferences(Context.MODE_PRIVATE) ?: return
        val jsonArray = org.json.JSONArray(list)
        sharedPref.edit().putString("chat_history_$mac", jsonArray.toString()).apply()
    }

    private fun clearChatHistory(mac: String) {
        saveHistoryList(mac, ArrayList())
        chatAdapter.clear()
        showSuccess("Чат очищен")
    }

    private fun setupProfile() {
        val sharedPref = getPreferences(Context.MODE_PRIVATE) ?: return
        var userId = sharedPref.getString(PREF_USER_ID, null)

        if (userId == null) {
            userId = "Подключитесь к ESP"
        }

        val formattedId = userId.chunked(3).joinToString("-")
        binding.userIdTextView.text = formattedId

        val isTerminal = sharedPref.getBoolean(PREF_IS_TERMINAL_THEME, false)
        binding.themeSwitchButton.text = if (isTerminal) "Вернуть обычный режим" else "Включить режим Терминала"
        if (isTerminal) binding.copyIdButton.setIconResource(R.drawable.ic_shield)

        binding.copyIdButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("GardianT ID", userId)
            clipboard.setPrimaryClip(clip)
            showSuccess("ID скопирован в буфер обмена")
        }
    }

    private fun showError(message: String) {
        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.error))
            .setTextColor(ContextCompat.getColor(this, R.color.onError))
            .setAction("OK") { }

        if (binding.bottomNavCard.visibility == View.VISIBLE) {
            snackbar.anchorView = binding.bottomNavCard
        }
        snackbar.show()
    }

    private fun showSuccess(message: String) {
        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.success))
            .setTextColor(ContextCompat.getColor(this, R.color.onSuccess))

        if (binding.bottomNavCard.visibility == View.VISIBLE) {
            snackbar.anchorView = binding.bottomNavCard
        }
        snackbar.show()
    }

    private fun showInfo(message: String) {
        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.primary))
            .setTextColor(ContextCompat.getColor(this, R.color.onPrimary))

        if (binding.bottomNavCard.visibility == View.VISIBLE) {
            snackbar.anchorView = binding.bottomNavCard
        }
        snackbar.show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "GardianT Messages"
            val descriptionText = "Уведомления о новых сообщениях"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type != null) {
            (if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            })?.let { uri ->
                pendingUploadUri = uri
                currentTab = Tab.CHATS
                binding.bottomNavigation.selectedItemId = R.id.nav_chats
                showInfo("Выберите контакт для отправки файла")
            }
        }
    }

    private fun showConfirmUploadDialog(contact: Contact, uri: android.net.Uri) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Отправка файла")
            .setMessage("Отправить файл контакту ${contact.name}?")
            .setPositiveButton("Отправить") { _, _ ->
                activeContact = contact
                uploadFileProcess(uri)
                pendingUploadUri = null
            }
            .setNegativeButton("Отмена") { _, _ ->
                pendingUploadUri = null
            }
            .show()
    }

    private fun playNotificationSound() {
        try {
            val mediaPlayer = MediaPlayer.create(this, R.raw.notification)
            mediaPlayer.setOnCompletionListener { it.release() }
            mediaPlayer.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play sound", e)
        }
    }

    private fun showSystemNotification(title: String, message: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notify(System.currentTimeMillis().toInt(), builder.build())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollRunnable?.let { pollHandler.removeCallbacks(it) }
        webServer?.stop()
    }

    private fun sendMessageToEsp(espIp: String, targetMac: String, message: String) {
        Thread {
            try {
                val encodedMsg = URLEncoder.encode(message, "UTF-8")
                val urlStr = "http://$espIp/send?to=$targetMac&msg=$encodedMsg"

                val url = URL(urlStr)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    runOnUiThread {
                        showSuccess("Сообщение отправлено на шифрование")
                    }
                } else {
                    runOnUiThread {
                        showError("Ошибка отправки на ESP: $responseCode")
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error sending to ESP", e)
                runOnUiThread {
                    showError("ESP недоступна. Проверьте подключение.")
                }
            }
        }.start()
    }

    private fun parseFileMessage(msg: String): FileItem {
        val parts = msg.split(": ", limit = 2)
        val sender = parts[0]
        val content = parts.getOrElse(1) { "" }

        if (content.startsWith("FILE|")) {
            val fileParts = content.split("|")
            return FileItem(
                id = fileParts.getOrElse(1) { "" },
                name = fileParts.getOrElse(2) { "Неизвестный файл" },
                sender = sender
            )
        }
        return FileItem("", content, sender)
    }

    private fun showDeleteFileDialog(file: FileItem) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Удалить из списка?")
            .setMessage("Файл \"${file.name}\" будет удален из истории чата.")
            .setPositiveButton("Удалить") { _, _ ->
                deleteFileFromHistory(file)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteFileFromHistory(file: FileItem, specificContact: Contact? = null) {
        val contact = specificContact ?: activeContact ?: return
        val history = loadChatHistory(contact.mac)
        val iterator = history.iterator()
        while (iterator.hasNext()) {
            val msg = iterator.next()
            if (msg.contains("FILE|${file.id}|")) {
                iterator.remove()
            }
        }
        saveHistoryList(contact.mac, history)

        if (isChatOpen && currentTab == Tab.TRUNK && activeContact?.mac == contact.mac) {
            openTrunk(contact)
        }
        if (specificContact == null) showSuccess("Файл удален из истории")
    }

    private fun uploadFileProcess(uri: android.net.Uri) {
        val contact = activeContact ?: return
        val fileKeyHex = contact.fileKey ?: return

        isUploadCancelled = false
        val filter = IntentFilter(ACTION_CANCEL_UPLOAD)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(uploadCancelReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(uploadCancelReceiver, filter)
        }

        showLoading(true)
        showInfo("Шифрование и загрузка...")

        val notificationManager = NotificationManagerCompat.from(this)
        val notificationId = (System.currentTimeMillis() % 10000).toInt()

        val cancelIntent = Intent(ACTION_CANCEL_UPLOAD)
        val cancelPendingIntent = PendingIntent.getBroadcast(this, 0, cancelIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Загрузка файла")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отмена", cancelPendingIntent)
            .setOngoing(true)
            .setProgress(0, 0, true)

        Thread {
            try {
                var fileName = "file_${System.currentTimeMillis()}"
                if (isUploadCancelled) throw Exception("Отменено пользователем")

                builder.setContentText("Подготовка: $fileName")
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) notificationManager.notify(notificationId, builder.build())

                var fileSize = 0L
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                        if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                    }
                }

                val limit = 10 * 1024 * 1024 // 10 MB
                if (fileSize > limit) {
                    throw Exception("Файл слишком большой ($fileSize байт). Лимит 10 МБ.")
                }

                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()

                if (bytes == null) throw Exception("Не удалось прочитать файл")

                if (isUploadCancelled) throw Exception("Отменено пользователем")
                builder.setContentText("Шифрование...")
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) notificationManager.notify(notificationId, builder.build())

                val finalData = CryptoUtils.encryptAES(bytes, fileKeyHex)

                val serverUrl = getPreferences(Context.MODE_PRIVATE).getString(PREF_URL, "") ?: ""
                val uploadUrl = if (serverUrl.endsWith("/")) "${serverUrl}api/files/upload" else "$serverUrl/api/files/upload"

                val boundary = "*****" + System.currentTimeMillis() + "*****"
                val connection = URL(uploadUrl).openConnection() as HttpURLConnection
                connection.doOutput = true
                connection.doInput = true
                connection.useCaches = false
                connection.requestMethod = "POST"
                connection.setRequestProperty("Connection", "Keep-Alive")
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                val espIp = lastEspIp ?: throw Exception("ESP не подключена")
                val tokenUrl = URL("http://$espIp/token")
                val tokenConnection = tokenUrl.openConnection() as HttpURLConnection
                tokenConnection.connectTimeout = 2000
                val token = if (tokenConnection.responseCode == 200) {
                    tokenConnection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    throw Exception("Не удалось получить токен от ESP (Код ${tokenConnection.responseCode})")
                }
                connection.setRequestProperty("Authorization", "Bearer $token")

                if (isUploadCancelled) throw Exception("Отменено пользователем")
                builder.setContentText("Отправка...")
                builder.setProgress(100, 0, false)
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) notificationManager.notify(notificationId, builder.build())

                val outputStream = connection.outputStream
                val writer = java.io.PrintWriter(java.io.OutputStreamWriter(outputStream, "UTF-8"), true)

                writer.append("--$boundary").append("\r\n")
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"").append("\r\n")
                writer.append("Content-Type: application/octet-stream").append("\r\n")
                writer.append("\r\n").flush()

                var offset = 0
                val totalSize = finalData.size
                val bufferSize = 8192
                var lastUpdate = 0L
                while (offset < totalSize) {
                    if (isUploadCancelled) throw Exception("Отменено пользователем")

                    val bytesToWrite = Math.min(bufferSize, totalSize - offset)
                    outputStream.write(finalData, offset, bytesToWrite)
                    offset += bytesToWrite
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 500) {
                        val progress = (offset * 100 / totalSize).toInt()
                        builder.setProgress(100, progress, false)
                        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) notificationManager.notify(notificationId, builder.build())
                        lastUpdate = now
                    }
                }
                outputStream.flush()

                writer.append("\r\n").flush()
                writer.append("--$boundary--").append("\r\n").flush()
                writer.close()

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val fileId = json.getString("file_id")

                    val msgText = "FILE|$fileId|$fileName"
                    sendMessage(msgText)

                    runOnUiThread {
                        showSuccess("Файл загружен!")
                        filesAdapter.addFile(FileItem(fileId, fileName, "Вы"))
                        showLoading(false)
                    }

                    builder.setContentText("Загрузка завершена")
                        .setProgress(0, 0, false)
                        .setOngoing(false)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) notificationManager.notify(notificationId, builder.build())

                } else {
                    throw Exception("Ошибка сервера: ${connection.responseCode}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Upload failed", e)
                runOnUiThread {
                    showError("Ошибка загрузки: ${e.message}")
                    showLoading(false)
                }

                builder.setContentText("Ошибка: ${e.message}")
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) notificationManager.notify(notificationId, builder.build())
            } finally {
                try {
                    unregisterReceiver(uploadCancelReceiver)
                } catch (e: Exception) {
                }
            }
        }.start()
    }

    private fun downloadAndDecryptFile(file: FileItem) {
        val contact = activeContact
        if (contact == null || contact.fileKey.isNullOrEmpty()) {
            showError("Нет ключа шифрования для этого контакта")
            return
        }

        showInfo("Скачивание: ${file.name}...")
        showLoading(true)

        Thread {
            try {
                val espIp = lastEspIp ?: throw Exception("ESP не подключена")
                val tokenUrl = URL("http://$espIp/token")
                val tokenConnection = tokenUrl.openConnection() as HttpURLConnection
                tokenConnection.connectTimeout = 2000
                val token = if (tokenConnection.responseCode == 200) {
                    tokenConnection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    throw Exception("Не удалось получить токен")
                }

                val serverUrl = getPreferences(Context.MODE_PRIVATE).getString(PREF_URL, "") ?: ""
                val downloadUrlStr = if (serverUrl.endsWith("/")) "${serverUrl}api/files/${file.id}" else "$serverUrl/api/files/${file.id}"

                val url = URL(downloadUrlStr)
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("Authorization", "Bearer $token")

                if (connection.responseCode != 200) {
                    throw Exception("Ошибка сервера: ${connection.responseCode}")
                }

                val encryptedData = connection.inputStream.readBytes()
                if (encryptedData.isEmpty()) throw Exception("Файл пуст или удален с сервера")
                if (encryptedData.size < 16) throw Exception("Файл поврежден")

                val decryptedData = CryptoUtils.decryptAES(encryptedData, contact.fileKey)

                saveToDownloads(file.name, decryptedData)

                runOnUiThread {
                    showSuccess("Файл сохранен в Загрузки: ${file.name}")
                    deleteFileFromHistory(file, contact)
                    showLoading(false)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                runOnUiThread {
                    showError("Ошибка: ${e.message}")
                    showLoading(false)
                }
            }
        }.start()
    }

    private fun saveToDownloads(fileName: String, data: ByteArray) {
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        if (uri != null) {
            resolver.openOutputStream(uri)?.use { it.write(data) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
        } else {
            throw Exception("Не удалось создать файл")
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val SERVER_PORT = 8080
        private const val PREF_SSID = "pref_ssid"
        private const val PREF_PASS = "pref_pass"
        private const val PREF_URL = "pref_url"
        private const val PREF_USER_ID = "pref_user_id"
        private const val PREF_MANUAL_IP = "pref_manual_ip"
        private const val PREF_ALIAS_PREFIX = "alias_"
        private const val PREF_IS_TERMINAL_THEME = "pref_is_terminal_theme"
        private const val PREF_PIN = "pref_pin"
        private const val PREF_PIN_ENABLED = "pref_pin_enabled"
        private const val CHANNEL_ID = "gardiant_channel"
        private const val ACTION_CANCEL_UPLOAD = "ru.mirzadach.guardts.ACTION_CANCEL_UPLOAD"
    }
}