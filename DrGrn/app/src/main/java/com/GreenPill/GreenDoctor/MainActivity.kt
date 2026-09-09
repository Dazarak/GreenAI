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

class MainActivity : AppCompatActivity() {
    private var selectedBase64Image: String = ""

    private lateinit var imagePreview: ImageView
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
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        val outputStream = ByteArrayOutputStream()
        // Compression en JPEG à 80% pour éviter un payload trop lourd
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()

        return "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Affichage de la prévisualisation visuelle
            imagePreview.setImageURI(it)
            previewContainer.visibility = View.VISIBLE

            // Encodage Base64
            selectedBase64Image = convertUriToBase64(it)
        }
    }

    private fun clearSelectedImage() {
        selectedBase64Image = ""
        imagePreview.setImageURI(null)
        previewContainer.visibility = View.GONE
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
                messageEditText.isEnabled = false
                sendButton.isEnabled = false
                userResponseTextView.text = userText
                aiResponseTextView.text = "L'IA réfléchit..."

                ApiClient.sendMessage(
                    targetAddress = scannedUrl,
                    userMessage = userText,
                    base64Image = selectedBase64Image
                ) { response ->
                    aiResponseTextView.text = response
                    messageEditText.text.clear()

                    // Nettoyage de la prévisualisation après envoi
                    clearSelectedImage()

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
                aiResponseTextView.text = response
                messageEditText.text.clear()
                messageEditText.isEnabled = true
                sendButton.isEnabled = true
                btnRefreshContext.isEnabled = true
            }
        }
    }
}