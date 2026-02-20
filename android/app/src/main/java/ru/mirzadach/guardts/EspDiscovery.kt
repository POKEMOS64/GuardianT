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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

object EspDiscovery {
    private const val TAG = "EspDiscovery"
    private const val DISCOVERY_PORT = 4211

    fun discover(onFound: (String) -> Unit, onFinished: () -> Unit) {
        Thread {
            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                socket.soTimeout = 500

                val sendData = "DISCOVER_GARDIANT".toByteArray()

                // 1. Broadcast
                try {
                    val broadcastPacket = DatagramPacket(
                        sendData, sendData.size,
                        InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT
                    )
                    socket.send(broadcastPacket)
                } catch (e: Exception) {
                    Log.e(TAG, "Broadcast failed", e)
                }

                // 2. Unicast Flood (сканирование подсети)
                val localIps = getLocalIpAddresses()
                for (localIp in localIps) {
                    val prefix = localIp.substring(0, localIp.lastIndexOf('.') + 1)
                    for (i in 1..254) {
                        val targetIp = prefix + i
                        if (targetIp == localIp) continue
                        try {
                            val packet = DatagramPacket(
                                sendData, sendData.size,
                                InetAddress.getByName(targetIp), DISCOVERY_PORT
                            )
                            socket.send(packet)
                        } catch (e: Exception) {}
                        if (i % 50 == 0) Thread.sleep(5)
                    }
                }

                // 3. Слушаем ответы
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
                                onFound(ip)
                                socket.close()
                                return@Thread
                            }
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        // Continue
                    }
                }
                socket.close()
                onFinished()

            } catch (e: Exception) {
                Log.e(TAG, "Discovery failed", e)
                onFinished()
            }
        }.start()
    }

    private fun getLocalIpAddresses(): List<String> {
        val ips = mutableListOf<String>()
        try {
            val en = NetworkInterface.getNetworkInterfaces()
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
}
