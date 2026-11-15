package com.seerflix.tv.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

data class CompanionFormResult(
    val jellyfinUrl: String = "",
    val jellyfinLocalUrl: String = "",
    val seerrUrl: String = "",
    val seerrLocalUrl: String = "",
    val trustSelfSigned: Boolean = false,
    val seerrApiKey: String = ""
)

class SetupCompanionServer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    var companionUrl: String? = null
        private set

    fun start(
        currentValues: CompanionFormResult = CompanionFormResult(),
        onSubmitted: (CompanionFormResult) -> Unit
    ): String? {
        stop()
        val lanIp = findLanIpv4Address() ?: return null
        // Bind only to the LAN interface we advertise (not 0.0.0.0, which would also expose the
        // form on VPN/other interfaces).
        val socket = runCatching {
            ServerSocket(0, 8, InetAddress.getByName(lanIp))
        }.getOrNull() ?: return null
        serverSocket = socket
        // Unguessable path segment shown only in the on-screen QR/URL: other devices on the LAN
        // can neither read the pre-filled form (which may contain the Seerr API key) nor push
        // server URLs to the TV.
        val token = generateAccessToken()
        val url = "http://$lanIp:${socket.localPort}/$token/"
        companionUrl = url

        acceptJob = scope.launch {
            while (isActive && !socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                launch {
                    // Never let one bad connection crash the app (uncaught exception in scope).
                    runCatching { handleClient(client, token, currentValues, onSubmitted) }
                    runCatching { client.close() }
                }
            }
        }
        return url
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        companionUrl = null
    }

    private fun handleClient(
        socket: Socket,
        token: String,
        defaults: CompanionFormResult,
        onSubmitted: (CompanionFormResult) -> Unit
    ) {
        socket.use { client ->
            client.soTimeout = 5_000
            val input = BufferedInputStream(client.getInputStream())
            val rawRequest = readHttpRequest(input) ?: return
            val headerEnd = rawRequest.indexOf("\r\n\r\n")
            val headersPart = if (headerEnd >= 0) rawRequest.substring(0, headerEnd) else rawRequest
            val firstLine = headersPart.lineSequence().firstOrNull().orEmpty()
            val parts = firstLine.split(" ")
            val method = parts.getOrNull(0)?.uppercase().orEmpty()
            val path = parts.getOrNull(1).orEmpty()

            if (!isAuthorizedPath(path, token)) {
                writeHttpResponse(client, status = "404 Not Found", html = "<!doctype html><title>Not found</title>")
                return
            }

            val subPath = path.removePrefix("/$token").substringBefore('?')
            if (method == "POST" && subPath.startsWith("/save")) {
                val body = if (headerEnd >= 0) rawRequest.substring(headerEnd + 4) else ""
                val parsed = parseFormBody(body)
                onSubmitted(parsed)
                writeHttpResponse(
                    client,
                    status = "200 OK",
                    html = buildSavedHtml()
                )
            } else {
                writeHttpResponse(
                    client,
                    status = "200 OK",
                    html = buildFormHtml(defaults, token)
                )
            }
        }
    }

    private fun readHttpRequest(input: BufferedInputStream): String? {
        val buf = ByteArrayOutputStream()
        val chunk = ByteArray(2048)
        var headerEnd = -1
        var expectedBodyBytes = 0

        while (true) {
            val read = runCatching { input.read(chunk) }.getOrDefault(-1)
            if (read <= 0) break
            buf.write(chunk, 0, read)
            val current = buf.toString(StandardCharsets.UTF_8.name())
            if (headerEnd < 0) {
                headerEnd = current.indexOf("\r\n\r\n")
                if (headerEnd >= 0) {
                    val headers = current.substring(0, headerEnd)
                    expectedBodyBytes = extractContentLength(headers)
                }
            }
            if (headerEnd >= 0) {
                val receivedBody = current.length - (headerEnd + 4)
                if (receivedBody >= expectedBodyBytes) break
            }
            if (buf.size() > 32_768) break
        }
        val text = buf.toString(StandardCharsets.UTF_8.name())
        return text.takeIf { it.isNotBlank() }
    }

    private fun extractContentLength(headers: String): Int {
        return headers.lineSequence()
            .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?.toIntOrNull()
            ?: 0
    }

    private fun writeHttpResponse(socket: Socket, status: String, html: String) {
        val bodyBytes = html.toByteArray(StandardCharsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 $status\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(StandardCharsets.UTF_8)

        runCatching {
            val out = socket.getOutputStream()
            out.write(headers)
            out.write(bodyBytes)
            out.flush()
        }
    }

    private fun buildFormHtml(defaults: CompanionFormResult, token: String): String = """
        <!doctype html>
        <html lang="en">
        <head>
          <meta name="viewport" content="width=device-width,initial-scale=1"/>
          <title>Lumen TV Setup</title>
          <style>
            body{background:#111;color:#fff;font-family:system-ui,-apple-system,sans-serif;padding:20px;max-width:480px;margin:0 auto;}
            h1{color:#e50914;font-size:24px;margin-bottom:8px;}
            p{color:#aaa;font-size:14px;margin-bottom:20px;}
            label{display:block;font-size:13px;font-weight:600;margin-top:14px;margin-bottom:6px;color:#ddd;}
            input[type=text],input[type=url]{width:100%;box-sizing:border-box;padding:12px;border-radius:8px;border:1px solid #333;background:#1e1e1e;color:#fff;font-size:15px;}
            .check{display:flex;align-items:center;gap:10px;margin-top:16px;}
            button{margin-top:22px;width:100%;padding:14px;border:none;border-radius:8px;background:#e50914;color:#fff;font-weight:700;font-size:16px;cursor:pointer;}
          </style>
        </head>
        <body>
          <h1>Lumen TV Companion</h1>
          <p>Paste your server URLs from your phone to send them straight to your TV.</p>
          <form method="POST" action="/$token/save">
            <label>Jellyfin URL (Remote or Primary)</label>
            <input type="text" inputmode="url" autocapitalize="off" autocorrect="off" spellcheck="false" name="jellyfinUrl" placeholder="https://jf.example.com" value="${escapeHtml(defaults.jellyfinUrl)}"/>
            <label>Jellyfin Local LAN URL (Optional)</label>
            <input type="text" inputmode="url" autocapitalize="off" autocorrect="off" spellcheck="false" name="jellyfinLocalUrl" placeholder="http://192.168.1.50:8096" value="${escapeHtml(defaults.jellyfinLocalUrl)}"/>
            <label>Seerr / Jellyseerr URL (Optional — leave blank for Jellyfin-Only Mode)</label>
            <input type="text" inputmode="url" autocapitalize="off" autocorrect="off" spellcheck="false" name="seerrUrl" placeholder="https://seerr.example.com" value="${escapeHtml(defaults.seerrUrl)}"/>
            <label>Seerr Local LAN URL (Optional)</label>
            <input type="text" inputmode="url" autocapitalize="off" autocorrect="off" spellcheck="false" name="seerrLocalUrl" placeholder="http://192.168.1.50:5055" value="${escapeHtml(defaults.seerrLocalUrl)}"/>
            <label>Seerr API Key (Optional)</label>
            <input type="text" name="seerrApiKey" placeholder="Optional X-Api-Key from Seerr Settings" value="${escapeHtml(defaults.seerrApiKey)}"/>
            <label class="check">
              <input type="checkbox" name="trustSelfSigned" value="true" ${if (defaults.trustSelfSigned) "checked" else ""}/>
              <span>Trust self-signed HTTPS certificates</span>
            </label>
            <button type="submit">Send to TV</button>
          </form>
        </body>
        </html>
    """.trimIndent()

    private fun buildSavedHtml(): String = """
        <!doctype html>
        <html lang="en">
        <head>
          <meta name="viewport" content="width=device-width,initial-scale=1"/>
          <title>Sent to TV</title>
          <style>
            body{background:#111;color:#fff;font-family:system-ui,sans-serif;padding:36px 20px;text-align:center;}
            h1{color:#46d369;}
          </style>
        </head>
        <body>
          <h1>Sent to Lumen TV!</h1>
          <p>Check your TV screen and click Test &amp; Continue.</p>
        </body>
        </html>
    """.trimIndent()

    private fun escapeHtml(value: String): String =
        value.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    companion object {
        private val secureRandom = SecureRandom()

        internal fun generateAccessToken(): String {
            val bytes = ByteArray(12)
            secureRandom.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        internal fun isAuthorizedPath(path: String, token: String): Boolean {
            if (token.isBlank()) return false
            val prefix = "/$token"
            return path == prefix || path.startsWith("$prefix/") || path.startsWith("$prefix?")
        }

        fun parseFormBody(body: String): CompanionFormResult {
            val fields = mutableMapOf<String, String>()
            body.split("&").forEach { pair ->
                if (pair.isBlank()) return@forEach
                val idx = pair.indexOf('=')
                if (idx <= 0) return@forEach
                val key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name())
                val value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name())
                fields[key] = value.trim()
            }
            return CompanionFormResult(
                jellyfinUrl = fields["jellyfinUrl"].orEmpty(),
                jellyfinLocalUrl = fields["jellyfinLocalUrl"].orEmpty(),
                seerrUrl = fields["seerrUrl"].orEmpty(),
                seerrLocalUrl = fields["seerrLocalUrl"].orEmpty(),
                trustSelfSigned = fields["trustSelfSigned"] == "true" || fields["trustSelfSigned"] == "on",
                seerrApiKey = fields["seerrApiKey"].orEmpty()
            )
        }

        fun findLanIpv4Address(): String? {
            return runCatching {
                val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                interfaces.asSequence()
                    .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                    .flatMap { it.inetAddresses.toList().asSequence() }
                    .filterIsInstance<Inet4Address>()
                    .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                    ?.hostAddress
            }.getOrNull()
        }
    }
}
