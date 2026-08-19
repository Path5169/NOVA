package com.nova.app.feature.shield.dns

import java.net.InetAddress

/** Parsed view of a UDP/IPv4 packet read off the tun interface. IPv6 is not handled — NOVA
 * Shield's tun route only ever carries IPv4 traffic to the fake DNS address it advertises. */
data class ParsedUdpPacket(
    val sourceAddress: InetAddress,
    val destAddress: InetAddress,
    val sourcePort: Int,
    val destPort: Int,
    val payload: ByteArray,
    val payloadLength: Int
)

object Ipv4Udp {

    /** Parses an IPv4 packet, returning the UDP payload if it is IPv4/UDP, else null. */
    fun parseUdp(buf: ByteArray, length: Int): ParsedUdpPacket? {
        if (length < 20) return null
        val versionIhl = buf[0].toInt() and 0xFF
        val version = versionIhl shr 4
        if (version != 4) return null // IPv6 not supported by this minimal parser
        val ihl = (versionIhl and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8) return null
        val protocol = buf[9].toInt() and 0xFF
        if (protocol != 17) return null // not UDP

        val srcBytes = buf.copyOfRange(12, 16)
        val dstBytes = buf.copyOfRange(16, 20)
        val srcAddr = InetAddress.getByAddress(srcBytes)
        val dstAddr = InetAddress.getByAddress(dstBytes)

        val udpOffset = ihl
        val srcPort = ((buf[udpOffset].toInt() and 0xFF) shl 8) or (buf[udpOffset + 1].toInt() and 0xFF)
        val dstPort = ((buf[udpOffset + 2].toInt() and 0xFF) shl 8) or (buf[udpOffset + 3].toInt() and 0xFF)
        val udpLength = ((buf[udpOffset + 4].toInt() and 0xFF) shl 8) or (buf[udpOffset + 5].toInt() and 0xFF)
        val payloadOffset = udpOffset + 8
        val payloadLength = (udpLength - 8).coerceAtLeast(0).coerceAtMost(length - payloadOffset)
        if (payloadLength <= 0) return null

        return ParsedUdpPacket(
            sourceAddress = srcAddr,
            destAddress = dstAddr,
            sourcePort = srcPort,
            destPort = dstPort,
            payload = buf,
            payloadLength = payloadLength
        ).let {
            // Copy just the payload out so callers don't hold a reference into the shared read buffer.
            it.copy(payload = buf.copyOfRange(payloadOffset, payloadOffset + payloadLength))
        }
    }

    /**
     * Builds a raw IPv4/UDP packet carrying [payload], addressed FROM [fromAddress]:[fromPort]
     * TO [toAddress]:[toPort]. Used to hand a DNS response back to the requesting app through
     * the tun interface, appearing to come from the address the app originally queried.
     */
    fun buildUdp(
        fromAddress: InetAddress,
        fromPort: Int,
        toAddress: InetAddress,
        toPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLength = 8 + payload.size
        val totalLength = 20 + udpLength
        val packet = ByteArray(totalLength)

        // --- IPv4 header ---
        packet[0] = (0x45).toByte() // version 4, IHL 5 (20 bytes, no options)
        packet[1] = 0
        packet[2] = ((totalLength shr 8) and 0xFF).toByte()
        packet[3] = (totalLength and 0xFF).toByte()
        packet[4] = 0; packet[5] = 0 // identification
        packet[6] = 0x40.toByte(); packet[7] = 0 // flags: don't fragment
        packet[8] = 64 // TTL
        packet[9] = 17 // protocol: UDP
        packet[10] = 0; packet[11] = 0 // header checksum, filled below
        System.arraycopy(fromAddress.address, 0, packet, 12, 4)
        System.arraycopy(toAddress.address, 0, packet, 16, 4)

        val ipChecksum = checksum(packet, 0, 20)
        packet[10] = ((ipChecksum shr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        // --- UDP header ---
        val udpOffset = 20
        packet[udpOffset] = ((fromPort shr 8) and 0xFF).toByte()
        packet[udpOffset + 1] = (fromPort and 0xFF).toByte()
        packet[udpOffset + 2] = ((toPort shr 8) and 0xFF).toByte()
        packet[udpOffset + 3] = (toPort and 0xFF).toByte()
        packet[udpOffset + 4] = ((udpLength shr 8) and 0xFF).toByte()
        packet[udpOffset + 5] = (udpLength and 0xFF).toByte()
        packet[udpOffset + 6] = 0; packet[udpOffset + 7] = 0 // checksum optional over IPv4, left as 0
        System.arraycopy(payload, 0, packet, udpOffset + 8, payload.size)

        return packet
    }

    private fun checksum(buf: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length - 1) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (length % 2 == 1) {
            sum += (buf[offset + length - 1].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }
}
