package com.seerflix.tv.core.network

import com.seerflix.tv.core.model.DiscoveredServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class JellyfinDiscoveryPayload(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Address") val address: String? = null
)

@Singleton
class JellyfinDiscoveryClient @Inject constructor(
    private val json: Json
) {
    suspend fun discoverServers(timeoutMs: Int = 2_000): List<DiscoveredServer> = withContext(Dispatchers.IO) {
        val results = linkedMapOf<String, DiscoveredServer>()
        val probeBytes = DISCOVERY_MESSAGE.toByteArray(Charsets.UTF_8)

        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 600
                val broadcastAddr = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(probeBytes, probeBytes.size, broadcastAddr, DISCOVERY_PORT)
                socket.send(packet)

                val deadline = System.currentTimeMillis() + timeoutMs
                val buffer = ByteArray(2048)
                while (System.currentTimeMillis() < deadline) {
                    val incoming = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(incoming)
                        val raw = String(incoming.data, 0, incoming.length, Charsets.UTF_8)
                        parseDiscoveryResponse(raw, incoming.address?.hostAddress)?.let { server ->
                            results.putIfAbsent(server.address, server)
                        }
                    } catch (_: SocketTimeoutException) {
                        // Keep polling until deadline
                    }
                }
            }
        }
        results.values.toList()
    }

    fun parseDiscoveryResponse(rawJson: String, fallbackHost: String? = null): DiscoveredServer? {
        val payload = runCatching {
            json.decodeFromString<JellyfinDiscoveryPayload>(rawJson.trim())
        }.getOrNull() ?: return null

        val rawAddress = payload.address?.trim().orEmpty()
        val resolvedAddress = when {
            rawAddress.startsWith("http://") || rawAddress.startsWith("https://") -> rawAddress.trimEnd('/')
            rawAddress.isNotBlank() -> "http://${rawAddress.trimEnd('/')}"
            !fallbackHost.isNullOrBlank() -> "http://$fallbackHost:8096"
            else -> return null
        }

        val name = payload.name?.takeIf { it.isNotBlank() } ?: "Jellyfin Server"
        val id = payload.id?.takeIf { it.isNotBlank() } ?: resolvedAddress
        return DiscoveredServer(
            id = id,
            name = name,
            address = resolvedAddress
        )
    }

    companion object {
        const val DISCOVERY_PORT = 7359
        const val DISCOVERY_MESSAGE = "who is JellyfinServer?"
    }
}
