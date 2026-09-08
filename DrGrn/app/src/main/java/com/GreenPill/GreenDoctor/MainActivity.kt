package com.GreenPill.GreenDoctor

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var qrCodeTextView: TextView
    private lateinit var aiResponseTextView: TextView
    private lateinit var userResponseTextView: TextView
    private lateinit var btnRefreshContext: ImageButton
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: ImageButton

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

        // Récupération de la valeur du QR Code
        val scannedUrl = intent.getStringExtra("EXTRA_QR_RESULT")
        if (scannedUrl != null) {
            qrCodeTextView.text = "Lien scanné : $scannedUrl"
        }

        // Action du bouton d'envoi
        sendButton.setOnClickListener {
            val userText = messageEditText.text.toString().trim()

            if (scannedUrl.isNullOrEmpty()) {
                aiResponseTextView.text = "Erreur : Aucun lien scanné !"
                return@setOnClickListener
            }

            if (userText.isNotEmpty()) {
                messageEditText.isEnabled = false
                sendButton.isEnabled = false
                userResponseTextView.text = userText
                aiResponseTextView.text = "L'IA réfléchit..."

                // On passe l'URL scannée + le message du champ texte
                ApiClient.sendMessage(scannedUrl, userText) { response ->
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
                aiResponseTextView.text = response
                messageEditText.text.clear()
                messageEditText.isEnabled = true
                sendButton.isEnabled = true
                btnRefreshContext.isEnabled = true
            }
        }
    }
}