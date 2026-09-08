package com.GreenPill.GreenDoctor

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class ServerConfig(val ip: String, val port: Int, val token: String)

object ApiClient {

    private const val TAG = "GREEN_DOCTOR_NET"
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Lit soit le JSON du QR Code {"ip":"...", "port":8080, "token":"..."},
     * soit une config enregistrée.
     */
    fun parseConfig(target: String, defaultToken: String = ""): ServerConfig? {
        Log.d(TAG, "[PARSE] Entrée brute : '$target'")
        val cleanTarget = target.trim()

        // 1. Cas où le QR Code renvoie du JSON
        if (cleanTarget.startsWith("{") && cleanTarget.endsWith("}")) {
            return try {
                val json = JSONObject(cleanTarget)
                val ip = json.getString("ip")
                val port = json.optInt("port", 8080)
                val token = json.optString("token", defaultToken)
                ServerConfig(ip, port, token)
            } catch (e: Exception) {
                Log.e(TAG, "[PARSE] Erreur parsing JSON QR Code", e)
                null
            }
        }

        // 2. Cas fallback : IP:PORT brute
        val cleanIp = cleanTarget.replace("http://", "").replace("https://", "").trim()
        val parts = cleanIp.split(":")
        if (parts.isEmpty() || parts[0].isEmpty()) return null

        val ip = parts[0]
        val port = if (parts.size > 1) parts[1].toIntOrNull() ?: 8080 else 8080
        return ServerConfig(ip, port, defaultToken)
    }

    /**
     * Effectue un GET /ping sur l'API Express HTTP
     */
    fun pingServer(targetAddress: String, timeoutMs: Int = 3000, callback: (Boolean, String) -> Unit) {
        thread {
            val config = parseConfig(targetAddress)
            if (config == null) {
                mainHandler.post { callback(false, "Format QR Code invalide") }
                return@thread
            }

            var conn: HttpURLConnection? = null
            try {
                val url = URL("http://${config.ip}:${config.port}/ping")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                }

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    mainHandler.post { callback(true, "Serveur en ligne !") }
                } else {
                    mainHandler.post { callback(false, "Code erreur HTTP : $responseCode") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "[PING] Échec connexion", e)
                mainHandler.post { callback(false, "Impossible de joindre ${config.ip}:${config.port}") }
            } finally {
                conn?.disconnect()
            }
        }
    }

    /**
     * Effectue un POST /chat sur l'API Express HTTP avec le jeton Bearer
     */
    fun sendMessage(
        targetAddress: String,
        userMessage: String,
        base64Image: String = "",
        savedToken: String = "",
        callback: (String) -> Unit
    ) {
        thread {
            val config = parseConfig(targetAddress, savedToken)
            if (config == null) {
                mainHandler.post { callback("Erreur : Configuration serveur invalide.") }
                return@thread
            }

            var conn: HttpURLConnection? = null
            try {
                val url = URL("http://${config.ip}:${config.port}/chat")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    // Injection dynamique du jeton récupéré du QR Code ou des Préférences
                    setRequestProperty("Authorization", "Bearer ${config.token}")
                    connectTimeout = 5000
                    readTimeout = 45000 // Délais laissé pour le traitement LLM
                    doOutput = true
                }

                val jsonPayload = JSONObject().apply {
                    put("question", userMessage)
                    put("base64_image", base64Image)
                }

                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(jsonPayload.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                val stream = if (responseCode == 200) conn.inputStream else conn.errorStream
                val responseRaw = BufferedReader(InputStreamReader(stream, "UTF-8")).use { it.readText() }

                if (responseCode == 200) {
                    val jsonResponse = JSONObject(responseRaw)
                    val aiReply = jsonResponse.optString("response", "Réponse vide.")
                    mainHandler.post { callback(aiReply) }
                } else {
                    mainHandler.post { callback("Erreur HTTP $responseCode : $responseRaw") }
                }

            } catch (e: Exception) {
                Log.e(TAG, "[SEND] Erreur HTTP", e)
                mainHandler.post { callback("Erreur réseau : ${e.localizedMessage}") }
            } finally {
                conn?.disconnect()
            }
        }
    }
}