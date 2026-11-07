/*
 * Copyright (c) 2010-2026 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.utils

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.linphone.core.TransportType
import org.linphone.core.tools.Log

/**
 * Zoiper-like SIP server auto-detection.
 *
 * Probes a host on a set of ports with SIP over TLS, TCP and UDP. A port is
 * only reported as usable when the server answers with a real SIP response
 * to our OPTIONS probe (an open TCP port alone is not proof of SIP).
 * UDP probes that time out are reported as inconclusive, not as failures.
 * TLS certificate validation is left enabled on purpose.
 */
class SipTransportScanner {
    companion object {
        private const val TAG = "[Sip Transport Scanner]"
        private const val CONNECT_TIMEOUT_MS = 2500
        private const val RESPONSE_TIMEOUT_MS = 2500
        private const val USER_AGENT = "Mytell"

        data class Result(
            val transport: TransportType,
            val port: Int,
            val sipReply: String?,
            val tlsCertificateError: Boolean,
            val elapsedMs: Long,
            val error: String?
        ) {
            val isUsable: Boolean
                get() = sipReply != null
        }

        fun rank(results: List<Result>): List<Result> {
            val transportRank = mapOf(
                TransportType.Tls to 0,
                TransportType.Tcp to 1,
                TransportType.Udp to 2
            )
            return results.sortedWith(
                compareBy(
                    { if (it.isUsable) 0 else 1 },
                    { transportRank[it.transport] ?: 3 },
                    { it.port }
                )
            )
        }
    }

    suspend fun scan(host: String, ports: Set<Int>): List<Result> = coroutineScope {
        val probes = mutableListOf<kotlinx.coroutines.Deferred<Result>>()
        for (port in ports) {
            probes += async { probe(host, port, TransportType.Tls) }
            probes += async { probe(host, port, TransportType.Tcp) }
            probes += async { probe(host, port, TransportType.Udp) }
        }
        rank(probes.awaitAll())
    }

    private suspend fun probe(
        host: String,
        port: Int,
        transport: TransportType
    ): Result =
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            val result = try {
                when (transport) {
                    TransportType.Tls -> probeTls(host, port)
                    TransportType.Tcp -> probeTcp(host, port)
                    else -> probeUdp(host, port)
                }
            } catch (e: Exception) {
                Log.i("$TAG $host:$port/${transport.name} probe failed: $e")
                Result(transport, port, null, false, 0, e.message ?: e.javaClass.simpleName)
            }
            Result(
                result.transport,
                result.port,
                result.sipReply,
                result.tlsCertificateError,
                System.currentTimeMillis() - startedAt,
                result.error
            )
        }

    private fun probeTls(host: String, port: Int): Result {
        return try {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val socket = factory.createSocket() as SSLSocket
            try {
                socket.soTimeout = RESPONSE_TIMEOUT_MS
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                socket.startHandshake()
                // Endpoint identification is NOT performed automatically by a
                // raw SSLSocket — verify the peer certificate matches the host
                // we dialed, exactly as Android requires.
                if (!javax.net.ssl.HttpsURLConnection
                        .getDefaultHostnameVerifier()
                        .verify(host, socket.session)
                ) {
                    Log.w("$TAG TLS certificate on $host:$port is not valid for this hostname")
                    return Result(
                        TransportType.Tls, port, null, true, 0,
                        "certificate not valid for hostname"
                    )
                }
                val reply = exchangeOptions(socket, host, port)
                Result(TransportType.Tls, port, reply, false, 0, null)
            } finally {
                try {
                    socket.close()
                } catch (_: Exception) {
                }
            }
        } catch (e: SSLHandshakeException) {
            // Server speaks TLS on this port but presented an untrusted
            // certificate: still useful information for the user.
            Log.i("$TAG TLS handshake failed on $host:$port: $e")
            Result(TransportType.Tls, port, null, true, 0, null)
        }
    }

    private fun probeTcp(host: String, port: Int): Result {
        val socket = Socket()
        try {
            socket.soTimeout = RESPONSE_TIMEOUT_MS
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            val reply = exchangeOptions(socket.getOutputStream(), socket.getInputStream(), host, port)
            return Result(TransportType.Tcp, port, reply, false, 0, null)
        } catch (e: SocketTimeoutException) {
            // Port accepted the connection? If connect succeeded but no reply
            // came, we report reachable-without-SIP; otherwise unreachable.
            val connected = socket.isConnected
            return Result(
                TransportType.Tcp, port, null, false, 0,
                if (connected) "timeout waiting for SIP reply" else "connect timed out"
            )
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun probeUdp(host: String, port: Int): Result {
        val socket = DatagramSocket()
        try {
            socket.soTimeout = RESPONSE_TIMEOUT_MS
            val message = buildOptionsMessage(TransportType.Udp, host, port).toByteArray()
            socket.send(
                DatagramPacket(message, message.size, InetSocketAddress(host, port))
            )
            val buffer = ByteArray(2048)
            val packet = DatagramPacket(buffer, buffer.size)
            return try {
                socket.receive(packet)
                val reply = String(packet.data, 0, packet.length, Charsets.ISO_8859_1)
                if (reply.startsWith("SIP/")) {
                    Result(TransportType.Udp, port, firstLine(reply), false, 0, null)
                } else {
                    Result(TransportType.Udp, port, null, false, 0, "non-SIP reply")
                }
            } catch (e: SocketTimeoutException) {
                // UDP is connectionless: silence is inconclusive, not proof
                // that no SIP service exists on this port.
                Result(TransportType.Udp, port, null, false, 0, "no UDP reply (inconclusive)")
            }
        } finally {
            socket.close()
        }
    }

    private fun exchangeOptions(
        socket: SSLSocket,
        host: String,
        port: Int
    ): String? {
        val out = socket.getOutputStream()
        val input = socket.getInputStream()
        out.write(buildOptionsMessage(TransportType.Tls, host, port).toByteArray())
        out.flush()
        return readSipFirstLine(input)
    }

    private fun exchangeOptions(
        out: java.io.OutputStream,
        input: java.io.InputStream,
        host: String,
        port: Int
    ): String? {
        out.write(buildOptionsMessage(TransportType.Tcp, host, port).toByteArray())
        out.flush()
        return readSipFirstLine(input)
    }

    private fun readSipFirstLine(input: java.io.InputStream): String? {
        val buffer = ByteArray(2048)
        var total = 0
        while (total < buffer.size) {
            val read = try {
                input.read(buffer, total, buffer.size - total)
            } catch (e: SocketTimeoutException) {
                break
            }
            if (read < 0) break
            total += read
            val chunk = String(buffer, 0, total, Charsets.ISO_8859_1)
            // A complete SIP response first line is enough to correlate.
            if (chunk.contains("\r\n")) {
                return if (chunk.startsWith("SIP/")) firstLine(chunk) else null
            }
        }
        val text = String(buffer, 0, total, Charsets.ISO_8859_1)
        return if (text.startsWith("SIP/")) firstLine(text) else null
    }

    private fun firstLine(response: String): String =
        response.substringBefore("\r\n").trim().take(64)

    private fun buildOptionsMessage(
        transport: TransportType,
        host: String,
        port: Int
    ): String {
        val branch = "z9hG4bK" + randomToken(10)
        val callId = randomToken(16)
        val tag = randomToken(8)
        val viaTransport = when (transport) {
            TransportType.Tls -> "TLS"
            TransportType.Tcp -> "TCP"
            else -> "UDP"
        }
        return buildString {
            append("OPTIONS sip:$host:$port SIP/2.0\r\n")
            append("Via: SIP/2.0/$viaTransport 10.0.2.15:5060;branch=$branch;rport\r\n")
            append("Max-Forwards: 70\r\n")
            append("From: <sip:probe@mytell.invalid>;tag=$tag\r\n")
            append("To: <sip:probe@$host:$port>\r\n")
            append("Call-ID: $callId\r\n")
            append("CSeq: 1 OPTIONS\r\n")
            append("User-Agent: $USER_AGENT\r\n")
            append("Content-Length: 0\r\n")
            append("\r\n")
        }
    }

    private fun randomToken(length: Int): String {
        val allowed = "0123456789abcdef"
        val random = java.security.SecureRandom()
        val builder = StringBuilder(length)
        repeat(length) {
            builder.append(allowed[random.nextInt(allowed.length)])
        }
        return builder.toString()
    }
}
