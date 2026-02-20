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

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)

    companion object {
        const val PREF_SSID = "pref_ssid"
        const val PREF_PASS = "pref_pass"
        const val PREF_URL = "pref_url"
        const val PREF_USER_ID = "pref_user_id"
        const val PREF_MANUAL_IP = "pref_manual_ip"
        const val PREF_ALIAS_PREFIX = "alias_"
        const val PREF_IS_TERMINAL_THEME = "pref_is_terminal_theme"
        const val PREF_PIN = "pref_pin"
        const val PREF_PIN_ENABLED = "pref_pin_enabled"
    }

    fun saveSettings(ssid: String, pass: String, url: String) {
        prefs.edit().putString(PREF_SSID, ssid).putString(PREF_PASS, pass).putString(PREF_URL, url).apply()
    }

    fun getSettings(): Triple<String, String, String> {
        return Triple(
            prefs.getString(PREF_SSID, "") ?: "",
            prefs.getString(PREF_PASS, "") ?: "",
            prefs.getString(PREF_URL, "") ?: ""
        )
    }
    
    fun getServerUrl(): String = prefs.getString(PREF_URL, "") ?: ""

    fun saveManualIp(ip: String) = prefs.edit().putString(PREF_MANUAL_IP, ip).apply()
    fun getManualIp(): String? = prefs.getString(PREF_MANUAL_IP, "")

    fun saveUserId(id: String) = prefs.edit().putString(PREF_USER_ID, id).apply()
    fun getUserId(): String? = prefs.getString(PREF_USER_ID, null)

    fun isTerminalTheme(): Boolean = prefs.getBoolean(PREF_IS_TERMINAL_THEME, false)
    fun setTerminalTheme(isTerminal: Boolean) = prefs.edit().putBoolean(PREF_IS_TERMINAL_THEME, isTerminal).apply()

    fun saveAlias(mac: String, alias: String) {
        if (alias.isEmpty()) prefs.edit().remove(PREF_ALIAS_PREFIX + mac).apply()
        else prefs.edit().putString(PREF_ALIAS_PREFIX + mac, alias).apply()
    }
    fun getAlias(mac: String): String? = prefs.getString(PREF_ALIAS_PREFIX + mac, null)

    fun saveChatHistory(mac: String, messages: List<String>) {
        val jsonArray = JSONArray(messages)
        prefs.edit().putString("chat_history_$mac", jsonArray.toString()).apply()
    }

    fun loadChatHistory(mac: String): ArrayList<String> {
        val jsonString = prefs.getString("chat_history_$mac", null) ?: return ArrayList()
        val list = ArrayList<String>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
        } catch (e: Exception) {}
        return list
    }

    fun clearAll() {
        prefs.edit().clear().commit()
    }
    
    // PIN methods
    fun isPinEnabled() = prefs.getBoolean(PREF_PIN_ENABLED, false)
    fun getPin() = prefs.getString(PREF_PIN, "")
    fun setPin(pin: String) = prefs.edit().putString(PREF_PIN, pin).putBoolean(PREF_PIN_ENABLED, true).apply()
    fun disablePin() = prefs.edit().putBoolean(PREF_PIN_ENABLED, false).apply()
}
