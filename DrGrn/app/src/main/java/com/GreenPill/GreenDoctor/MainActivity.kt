package com.GreenPill.GreenDoctor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.scale
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {
    private var selectedBase64Image: String = ""

    private lateinit var imagePreview: ImageView
    private lateinit var previewContainer: FrameLayout
    private lateinit var buttonRemoveImage: ImageButton
    private lateinit var imageButton: ImageButton
    private lateinit var qrCodeTextView: TextView
    private lateinit var btnRefreshContext: ImageButton
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var recyclerView: RecyclerView
    private val messageHistory = mutableListOf<Message>()
    private lateinit var adapter: MessageAdapter

    private fun convertUriToBase64(uri: Uri): String {
        val inputStream = contentResolver.openInputStream(uri)

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream?.close()

        val maxDimension = 1024
        var inSampleSize = 1
        if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
                inSampleSize *= 2
            }
        }

        val decodeStream = contentResolver.openInputStream(uri)
        val scaledBitmap = BitmapFactory.decodeStream(decodeStream, null, BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        })
        decodeStream?.close()

        if (scaledBitmap == null) return ""

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()
        scaledBitmap.recycle()

        return "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedBase64Image = convertUriToBase64(it)

            if (selectedBase64Image.isNotEmpty()) {
                val bitmap = base64ToBitmap(selectedBase64Image)
                imagePreview.setImageBitmap(bitmap)
                previewContainer.visibility = View.VISIBLE
            }
        }
    }

    private fun clearSelectedImage() {
        selectedBase64Image = ""
        imagePreview.setImageURI(null)
        previewContainer.visibility = View.GONE
    }

    private fun base64ToBitmap(base64String: String): Bitmap? {
        return try {
            val cleanBase64 = base64String.substringAfter(",")
            val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun compressBase64ForStorage(base64Source: String): String {
        if (base64Source.isEmpty()) return ""

        val cleanBase64 = base64Source.substringAfter(",")
        val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
        val originalBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size) ?: return ""

        val maxDimension = 300
        val width = originalBitmap.width
        val height = originalBitmap.height

        val resizedBitmap = if (width > maxDimension || height > maxDimension) {
            val ratio = width.toFloat() / height.toFloat()
            val (targetWidth, targetHeight) = if (width > height) {
                maxDimension to (maxDimension / ratio).toInt()
            } else {
                (maxDimension * ratio).toInt() to maxDimension
            }

            originalBitmap.scale(targetWidth, targetHeight, filter = true)
        } else {
            originalBitmap
        }

        val outputStream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)

        if (resizedBitmap != originalBitmap) {
            originalBitmap.recycle()
        }

        return "data:image/jpeg;base64," + Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialisation des vues existantes dans activity_main.xml
        qrCodeTextView = findViewById(R.id.qrCodeTextView)
        messageEditText = findViewById(R.id.messageEditText)
        sendButton = findViewById(R.id.sendButton)
        btnRefreshContext = findViewById(R.id.buttonRefresh)
        imageButton = findViewById(R.id.imageButton)
        imagePreview = findViewById(R.id.imagePreview)
        previewContainer = findViewById(R.id.previewContainer)
        buttonRemoveImage = findViewById(R.id.buttonRemoveImage)
        recyclerView = findViewById(R.id.recyclerViewMessages)

        // Configuration du RecyclerView
        adapter = MessageAdapter(messageHistory)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Récupération de la valeur du QR Code
        val scannedUrl = intent.getStringExtra("EXTRA_QR_RESULT")
        if (scannedUrl != null) {
            qrCodeTextView.text = "DrGrn"
        }

        imageButton.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }

        buttonRemoveImage.setOnClickListener {
            clearSelectedImage()
        }

        sendButton.setOnClickListener {
            val userText = messageEditText.text.toString().trim()

            if (scannedUrl.isNullOrEmpty()) {
                Toast.makeText(this, "Erreur : Aucun lien scanné !", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (userText.isNotEmpty() || selectedBase64Image.isNotEmpty()) {
                // 1. Message de l'utilisateur
                val userMsg = Message(
                    content = userText,
                    imageBase64 = if (selectedBase64Image.isNotEmpty()) compressBase64ForStorage(selectedBase64Image) else null,
                    isUser = true
                )
                messageHistory.add(userMsg)
                adapter.notifyItemInserted(messageHistory.size - 1)
                recyclerView.scrollToPosition(messageHistory.size - 1)

                // 2. Verrouillage de la saisie pendant l'envoi
                messageEditText.isEnabled = false
                sendButton.isEnabled = false
                imageButton.isEnabled = false

                val imageToSend = selectedBase64Image
                clearSelectedImage()

                // 3. Appel API
                ApiClient.sendMessage(
                    targetAddress = scannedUrl,
                    userMessage = userText,
                    base64Image = imageToSend
                ) { response ->
                    val aiMsg = Message(
                        content = response,
                        imageBase64 = null,
                        isUser = false
                    )
                    messageHistory.add(aiMsg)
                    adapter.notifyItemInserted(messageHistory.size - 1)
                    recyclerView.scrollToPosition(messageHistory.size - 1)

                    messageEditText.text.clear()
                    messageEditText.isEnabled = true
                    sendButton.isEnabled = true
                    imageButton.isEnabled = true
                }
            }
        }

        // Action du bouton de réinitialisation
        btnRefreshContext.setOnClickListener {
            if (scannedUrl.isNullOrEmpty()) {
                Toast.makeText(this, "Erreur : Aucun lien scanné !", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            messageEditText.isEnabled = false
            sendButton.isEnabled = false
            imageButton.isEnabled = false
            btnRefreshContext.isEnabled = false

            // Message utilisateur pour le reset
            val resetUserMsg = Message(content = "/reset", isUser = true)
            messageHistory.add(resetUserMsg)
            adapter.notifyItemInserted(messageHistory.size - 1)
            recyclerView.scrollToPosition(messageHistory.size - 1)

            ApiClient.sendMessage(scannedUrl, "/reset") { response ->
                val resetAiMsg = Message(
                    content = response,
                    imageBase64 = null,
                    isUser = false
                )
                messageHistory.add(resetAiMsg)
                adapter.notifyItemInserted(messageHistory.size - 1)
                recyclerView.scrollToPosition(messageHistory.size - 1)

                messageEditText.text.clear()
                messageEditText.isEnabled = true
                sendButton.isEnabled = true
                imageButton.isEnabled = true
                btnRefreshContext.isEnabled = true
            }
        }
    }
}