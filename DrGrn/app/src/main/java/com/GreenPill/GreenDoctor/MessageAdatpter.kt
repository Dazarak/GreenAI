package com.GreenPill.GreenDoctor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class MessageAdapter(private val pairs: List<MessagePair>) :
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // User
        val msgUser: LinearLayout = view.findViewById(R.id.MessageUser)
        val tvUser: TextView = view.findViewById(R.id.UserRequest)
        val imageInMessage: ImageView = view.findViewById(R.id.imageInMessage)
        val btnCopyUser: ImageButton = view.findViewById(R.id.imageButtonCopyUser)

        // IA
        val msgIA: LinearLayout = view.findViewById(R.id.MessageIA)
        val tvAI: TextView = view.findViewById(R.id.AIAnswer)
        val imgLoader: ImageView = view.findViewById(R.id.imageLoading)
        val btnCopyIA: ImageButton = view.findViewById(R.id.imageButtonCopyIA)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message_pair, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val pair = pairs[position]
        val context = holder.itemView.context

        holder.msgUser.visibility = View.VISIBLE
        holder.tvUser.text = pair.userContent

        if (!pair.userImageBase64.isNullOrEmpty()) {
            val bitmap = base64ToBitmap(pair.userImageBase64)
            holder.imageInMessage.setImageBitmap(bitmap)
            holder.imageInMessage.visibility = View.VISIBLE
        } else {
            holder.imageInMessage.visibility = View.GONE
        }

        holder.btnCopyUser.setOnClickListener {
            copyToClipboard(context, pair.userContent)
        }

        // 2. Partie IA
        holder.msgIA.visibility = View.VISIBLE

        if (pair.aiContent == "L'IA réfléchit...") {
            holder.tvAI.visibility = View.GONE
            holder.btnCopyIA.visibility = View.GONE
            holder.imgLoader.visibility = View.VISIBLE

            if (holder.imgLoader.animation == null) {
                val rotateAnim = AnimationUtils.loadAnimation(context, R.anim.rotate_indefinitely)
                holder.imgLoader.startAnimation(rotateAnim)
            }
        } else {
            holder.imgLoader.clearAnimation()
            holder.imgLoader.visibility = View.GONE
            holder.tvAI.visibility = View.VISIBLE
            holder.btnCopyIA.visibility = View.VISIBLE
            holder.tvAI.text = pair.aiContent

            holder.btnCopyIA.setOnClickListener {
                copyToClipboard(context, pair.aiContent)
            }
        }
    }

    override fun getItemCount(): Int = pairs.size

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