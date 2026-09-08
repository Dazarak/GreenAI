package com.GreenPill.GreenDoctor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class LoginActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnQuickConnect: Button
    private var cameraProvider: ProcessCameraProvider? = null
    @Volatile private var isScanned = false

    companion object {
        private const val PREFS_NAME = "GreenDoctorPrefs"
        private const val KEY_IP = "LAST_SAVED_IP"
        private const val KEY_PORT = "LAST_SAVED_PORT"
        private const val KEY_TOKEN = "LAST_SAVED_TOKEN"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        previewView = findViewById(R.id.previewView)
        btnQuickConnect = findViewById(R.id.btnQuickConnect)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), 101
            )
        }
    }

    override fun onResume() {
        super.onResume()
        isScanned = false
        checkSavedIpAndSetupButton()
    }

    private fun checkSavedIpAndSetupButton() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val ip = prefs.getString(KEY_IP, null)
        val port = prefs.getInt(KEY_PORT, 8080)
        val token = prefs.getString(KEY_TOKEN, null)

        if (!ip.isNullOrEmpty() && !token.isNullOrEmpty()) {
            btnQuickConnect.visibility = View.VISIBLE
            btnQuickConnect.text = "Se connecter à $ip"

            btnQuickConnect.setOnClickListener {
                if (!isScanned) {
                    isScanned = true
                    val jsonPayload = """{"ip":"$ip","port":$port,"token":"$token"}"""
                    testAndConnect(jsonPayload)
                }
            }
        } else {
            btnQuickConnect.visibility = View.GONE
        }
    }

    private fun saveConnectionConfig(config: ServerConfig) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_IP, config.ip)
            .putInt(KEY_PORT, config.port)
            .putString(KEY_TOKEN, config.token)
            .apply()
    }

    private fun testAndConnect(qrContent: String) {
        Toast.makeText(this@LoginActivity, "Vérification de l'API Node.js...", Toast.LENGTH_SHORT).show()

        ApiClient.pingServer(qrContent, timeoutMs = 4000) { isSuccess, message ->
            if (isSuccess) {
                val config = ApiClient.parseConfig(qrContent)
                if (config != null) {
                    saveConnectionConfig(config)
                }

                cameraProvider?.unbindAll()
                Toast.makeText(this@LoginActivity, "Serveur en ligne !", Toast.LENGTH_SHORT).show()

                val intent = Intent(this@LoginActivity, MainActivity::class.java).apply {
                    putExtra("EXTRA_QR_RESULT", qrContent)
                }
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(
                    this@LoginActivity,
                    "Échec : $message\nVérifie ZeroTier et l'API Node.js.",
                    Toast.LENGTH_LONG
                ).show()

                isScanned = false
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                processImageProxy(imageProxy)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null && !isScanned) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            val scanner = BarcodeScanning.getClient()

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        if (rawValue != null && !isScanned) {
                            isScanned = true

                            runOnUiThread {
                                testAndConnect(rawValue)
                            }
                            break
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(this, "Permission caméra requise", Toast.LENGTH_SHORT).show()
        }
    }
}