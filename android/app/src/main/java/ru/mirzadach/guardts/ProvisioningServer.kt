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

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ProvisioningServer(
    port: Int,
    private val ssid: String,
    private val pass: String,
    private val url: String,
    private val onDeviceConnected: (String) -> Unit
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "ProvisioningServer"
    }

    override fun serve(session: IHTTPSession): Response {
        // Захватываем IP адрес подключившегося устройства
        val remoteIp = session.headers["remote-addr"] ?: session.remoteIpAddress
        if (remoteIp != null) {
            onDeviceConnected(remoteIp)
        }

        return when {
            session.method == Method.GET && session.uri.equals("/config", ignoreCase = true) -> {
                handleConfigRequest()
            }
            session.method == Method.GET && session.uri.equals("/health", ignoreCase = true) -> {
                handleHealthRequest()
            }
            session.method == Method.POST && session.uri.startsWith("/api/auth/device") -> {
                handleOfflineAuthRequest()
            }
            // --- Новые методы для Прокси-режима ---
            session.method == Method.POST && session.uri.equals("/proxy/auth", ignoreCase = true) -> {
                proxyRequestToVds(session, "/api/auth/device", "POST")
            }
            session.method == Method.POST && session.uri.equals("/proxy/send", ignoreCase = true) -> {
                proxyRequestToVds(session, "/api/chat/send", "POST")
            }
            session.method == Method.GET && session.uri.equals("/proxy/poll", ignoreCase = true) -> {
                proxyRequestToVds(session, "/api/chat/poll", "GET")
            }
            else -> {
                newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
            }
        }
    }

    private fun handleConfigRequest(): Response {
        val jsonResponse = JSONObject().apply {
            put("wifi_ssid", ssid)
            put("wifi_pass", pass)
            put("server_url", url)
            put("timestamp", System.currentTimeMillis())
        }

        return newFixedLengthResponse(Response.Status.OK, "application/json", jsonResponse.toString(2)).also {
            it.addHeader("Access-Control-Allow-Origin", "*")
        }
    }

    private fun handleHealthRequest(): Response {
        return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"status\":\"running\",\"timestamp\":${System.currentTimeMillis()}}")
    }

    private fun handleOfflineAuthRequest(): Response {
        val jsonResponse = JSONObject().apply {
            put("access_token", "offline_mode_token_12345")
            put("token_type", "bearer")
            put("expires_in", 3600)
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", jsonResponse.toString())
    }

    private fun proxyRequestToVds(session: IHTTPSession, endpoint: String, method: String): Response {
        try {
            val files = HashMap<String, String>()
            if (method == "POST") {
                session.parseBody(files)
            }
            val postData = files["postData"]

            val targetUrl = if (url.endsWith("/")) "${url.dropLast(1)}$endpoint" else "$url$endpoint"
            val finalUrl = if (!session.queryParameterString.isNullOrEmpty()) {
                "$targetUrl?${session.queryParameterString}"
            } else {
                targetUrl
            }

            val connection = URL(finalUrl).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.doInput = true

            val authHeader = session.headers["authorization"]
            if (authHeader != null) connection.setRequestProperty("Authorization", authHeader)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", "GardianT-Proxy/1.0")

            if (method == "POST" && postData != null) {
                connection.doOutput = true
                connection.outputStream.write(postData.toByteArray())
            }

            val responseCode = connection.responseCode
            val responseBody = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            val status = if (responseCode == 200) Response.Status.OK else Response.Status.INTERNAL_ERROR
            return newFixedLengthResponse(status, "application/json", responseBody)

        } catch (e: Exception) {
            Log.e(TAG, "Proxy error", e)
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Proxy Error: ${e.message}")
        }
    }
}
