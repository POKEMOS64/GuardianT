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

import java.io.Serializable

data class Contact(
    val name: String,
    val mac: String,
    val lastMessage: String,
    val timestamp: String,
    val fileKey: String? // Ключ для шифрования файлов
) : Serializable

data class FileItem(val id: String, val name: String, val sender: String) : Serializable