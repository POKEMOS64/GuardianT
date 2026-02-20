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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {
    private val messages = ArrayList<String>()

    fun clear() {
        messages.clear()
        notifyDataSetChanged()
    }

    fun addMessage(msg: String) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val textView = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(16, 16, 16, 16)
            textSize = 16f
        }
        return ChatViewHolder(textView)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        (holder.itemView as TextView).text = messages[position]
    }

    override fun getItemCount() = messages.size

    inner class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view)
}

class ContactAdapter(
    private val onClick: (Contact) -> Unit,
    private val onLongClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {
    private var contacts = listOf<Contact>()

    fun setContacts(newContacts: List<Contact>) {
        contacts = newContacts
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = contacts[position]
        holder.bind(contact)
    }

    override fun getItemCount() = contacts.size

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameView: TextView = itemView.findViewById(R.id.contactName)
        private val messageView: TextView = itemView.findViewById(R.id.contactLastMessage)
        private val timeView: TextView = itemView.findViewById(R.id.contactTime)

        fun bind(contact: Contact) {
            nameView.text = contact.name
            messageView.text = contact.lastMessage
            timeView.text = contact.timestamp
            itemView.setOnClickListener { onClick(contact) }
            itemView.setOnLongClickListener {
                onLongClick(contact)
                true
            }
        }
    }
}

class FilesAdapter(
    private val onFileClick: (FileItem) -> Unit,
    private val onFileLongClick: (FileItem) -> Unit
) : RecyclerView.Adapter<FilesAdapter.FileViewHolder>() {
    private var files = ArrayList<FileItem>()

    fun setFiles(newFiles: List<FileItem>) {
        files = ArrayList(newFiles)
        notifyDataSetChanged()
    }

    fun addFile(file: FileItem) {
        files.add(0, file) // Добавляем в начало
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val textView = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(32, 32, 32, 32)
            textSize = 18f
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_device, 0, 0, 0)
            compoundDrawablePadding = 16
        }
        return FileViewHolder(textView)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        (holder.itemView as TextView).text = file.name

        holder.itemView.setOnClickListener {
            onFileClick(file)
        }
        holder.itemView.setOnLongClickListener {
            onFileLongClick(file)
            true
        }
    }

    override fun getItemCount() = files.size

    inner class FileViewHolder(view: View) : RecyclerView.ViewHolder(view)
}
