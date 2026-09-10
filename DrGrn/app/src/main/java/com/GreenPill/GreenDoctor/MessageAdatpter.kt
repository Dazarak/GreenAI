package com.GreenPill.GreenDoctor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class MessageAdapter(private val messages: List<Message>) :
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvUser: TextView = view.findViewById(R.id.UserRequest)
        val tvAI: TextView = view.findViewById(R.id.AIAnswer)
        val imageInMessage: ImageView = view.findViewById(R.id.imageInMessage)
        val btnCopyUser: ImageButton = view.findViewById(R.id.imageButtonCopyUser)
        val btnCopyIA: ImageButton = view.findViewById(R.id.imageButtonCopyIA)

        val msgIA: LinearLayout = view.findViewById(R.id.MessageIA)
        val msgUser: LinearLayout = view.findViewById(R.id.MessageUser)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message_pair, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]

        if (message.isUser) {
            // Afficher conteneur Utilisateur, cacher conteneur IA
            holder.msgUser.visibility = View.VISIBLE
            holder.msgIA.visibility = View.GONE

            holder.tvUser.text = message.content

            if (!message.imageBase64.isNullOrEmpty()) {
                val bitmap = base64ToBitmap(message.imageBase64)
                holder.imageInMessage.setImageBitmap(bitmap)
                holder.imageInMessage.visibility = View.VISIBLE
            } else {
                holder.imageInMessage.visibility = View.GONE
            }
        } else {
            // Afficher conteneur IA, cacher conteneur Utilisateur
            holder.msgIA.visibility = View.VISIBLE
            holder.msgUser.visibility = View.GONE

            holder.tvAI.text = message.content
        }

        holder.btnCopyUser.setOnClickListener {
            copyToClipboard(holder.itemView.context, holder.tvUser.text.toString())
        }

        holder.btnCopyIA.setOnClickListener {
            copyToClipboard(holder.itemView.context, holder.tvAI.text.toString())
        }
    }

    override fun getItemCount(): Int = messages.size

    private fun copyToClipboard(context: Context, text: String) {
        if (text.isNotEmpty()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied Text", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Texte copié", Toast.LENGTH_SHORT).show()
        }
    }

    private fun base64ToBitmap(base64String: String): android.graphics.Bitmap? {
        return try {
            val cleanBase64 = base64String.substringAfter(",")
            val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }
}