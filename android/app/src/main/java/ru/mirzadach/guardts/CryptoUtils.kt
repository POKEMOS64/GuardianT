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

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

object CryptoUtils {
    fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    fun encryptAES(data: ByteArray, keyHex: String): ByteArray {
        val keyBytes = hexStringToByteArray(keyHex)
        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)
        val ivSpec = IvParameterSpec(iv)
        val skeySpec = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec, ivSpec)
        val encryptedBytes = cipher.doFinal(data)

        // Возвращаем IV + Зашифрованные данные
        return iv + encryptedBytes
    }

    fun decryptAES(data: ByteArray, keyHex: String): ByteArray {
        if (data.size < 16) throw Exception("Данные повреждены или слишком коротки")
        
        val iv = data.copyOfRange(0, 16)
        val cipherText = data.copyOfRange(16, data.size)
        val keyBytes = hexStringToByteArray(keyHex)
        
        val ivSpec = IvParameterSpec(iv)
        val skeySpec = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivSpec)
        return cipher.doFinal(cipherText)
    }
}
