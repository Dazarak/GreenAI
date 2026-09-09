package com.GreenPill.GreenDoctor

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import java.io.ByteArrayOutputStream
import androidx.core.graphics.scale

class MainActivity : AppCompatActivity() {
    private var selectedBase64Image: String = ""
    private val messageHistory = mutableListOf<Message>()

    private lateinit var imagePreview: ImageView
    private lateinit var imageInMessage: ImageView
    private lateinit var previewContainer: FrameLayout
    private lateinit var buttonRemoveImage: ImageButton
    private lateinit var imageButton: ImageButton
    private lateinit var qrCodeTextView: TextView
    private lateinit var aiResponseTextView: TextView
    private lateinit var userResponseTextView: TextView
    private lateinit var btnRefreshContext: ImageButton
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var imageButtonCopyUser: ImageButton
    private lateinit var imageButtonCopyIA: ImageButton

    private fun convertUriToBase64(uri: Uri): String {
        val inputStream = contentResolver.openInputStream(uri)

        // 1. Lire d'abord uniquement la taille de l'image
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream?.close()

        // 2. Calculer le sous-échantillonnage (inSampleSize)
        val maxDimension = 1024
        var inSampleSize = 1
        if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
                inSampleSize *= 2
            }
        }

        // 3. Charger le Bitmap sous-échantillonné
        val decodeStream = contentResolver.openInputStream(uri)
        val scaledBitmap = BitmapFactory.decodeStream(decodeStream, null, BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        })
        decodeStream?.close()

        if (scaledBitmap == null) return ""

        // 4. Compression JPEG 80%
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
            // Generer la base64 déjà sous-échantillonnée
            selectedBase64Image = convertUriToBase64(it)

            if (selectedBase64Image.isNotEmpty()) {
                // Afficher le preview directement depuis la base64 safe
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

        // 1. Calcul des dimensions
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

            // Utilisation de l'extension KTX à la place de Bitmap.createScaledBitmap
            originalBitmap.scale(targetWidth, targetHeight, filter = true)
        } else {
            originalBitmap
        }

        // 2. Compression JPEG à 50%
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

        // Initialisation des vues
        qrCodeTextView = findViewById(R.id.qrCodeTextView)
        aiResponseTextView = findViewById(R.id.AIAnswer)
        userResponseTextView = findViewById(R.id.UserRequest)
        messageEditText = findViewById(R.id.messageEditText)
        sendButton = findViewById(R.id.sendButton)
        btnRefreshContext = findViewById(R.id.buttonRefresh)
        imageButton = findViewById(R.id.imageButton)
        imageButtonCopyUser = findViewById(R.id.imageButtonCopyUser)
        imageButtonCopyIA = findViewById(R.id.imageButtonCopyIA)
        imagePreview = findViewById(R.id.imagePreview)
        imageInMessage = findViewById(R.id.imageInMessage)
        previewContainer = findViewById(R.id.previewContainer)
        buttonRemoveImage = findViewById(R.id.buttonRemoveImage)

        // Récupération de la valeur du QR Code
        val scannedUrl = intent.getStringExtra("EXTRA_QR_RESULT")
        if (scannedUrl != null) {
            qrCodeTextView.text = "DrGrn"
        }

        imageButton.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }

        imageButtonCopyUser.setOnClickListener {
            messageEditText.setText(userResponseTextView.text)
        }
        imageButtonCopyIA.setOnClickListener {
            messageEditText.setText(aiResponseTextView.text)
        }

        buttonRemoveImage.setOnClickListener {
            clearSelectedImage()
        }

        sendButton.setOnClickListener {
            val userText = messageEditText.text.toString().trim()

            if (scannedUrl.isNullOrEmpty()) {
                aiResponseTextView.text = "Erreur : Aucun lien scanné !"
                return@setOnClickListener
            }

            if (userText.isNotEmpty() || selectedBase64Image.isNotEmpty()) {
                val userMsg = Message(
                    content = userText,
                    imageBase64 = if (selectedBase64Image.isNotEmpty()) compressBase64ForStorage(selectedBase64Image) else null,
                    isUser = true
                )
                messageHistory.add(userMsg)

                messageEditText.isEnabled = false
                sendButton.isEnabled = false
                userResponseTextView.text = userText

                if (userMsg.imageBase64 != null) {
                    val bitmap = base64ToBitmap(userMsg.imageBase64)
                    imageInMessage.setImageBitmap(bitmap)
                    imageInMessage.visibility = View.VISIBLE
                } else {
                    imageInMessage.visibility = View.GONE
                }

                aiResponseTextView.text = "L'IA réfléchit..."

                val imageToSend = selectedBase64Image

                clearSelectedImage()

                ApiClient.sendMessage(
                    targetAddress = scannedUrl,
                    userMessage = userText,
                    base64Image = imageToSend
                ) { response ->
                    val aiMsg = Message(
                        content = response,
                        imageBase64 = null, // L'IA ne renvoie pas d'image
                        isUser = false
                    )
                    messageHistory.add(aiMsg)
                    aiResponseTextView.text = response
                    messageEditText.text.clear()

                    messageEditText.isEnabled = true
                    sendButton.isEnabled = true
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
            btnRefreshContext.isEnabled = false

            userResponseTextView.text = "/reset"
            aiResponseTextView.text = "L'IA reset la conversation..."

            ApiClient.sendMessage(scannedUrl, "/reset") { response ->
                val resetMsg = Message(
                    content = response,
                    imageBase64 = null,
                    isUser = false
                )
                messageHistory.add(resetMsg)

                aiResponseTextView.text = response
                messageEditText.text.clear()
                messageEditText.isEnabled = true
                sendButton.isEnabled = true
                btnRefreshContext.isEnabled = true
            }
        }
    }
}