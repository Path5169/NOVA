package com.nova.app.feature.shield.dns

/**
 * Hand-rolled, deliberately minimal DNS message helpers — just enough to read the
 * question name out of a query and synthesize a blocked (NXDOMAIN) response. No
 * external DNS library dependency, matching NOVA's "no unnecessary dependencies" rule.
 *
 * Only plain UDP DNS (RFC 1035) is handled. DNS-over-TLS/HTTPS traffic from apps that
 * do their own resolution bypasses this entirely — a real limitation, not hidden.
 */
object DnsMessage {

    /** Reads the QNAME of the first question in a raw DNS message, or null if malformed/empty. */
    fun readQuestionName(packet: ByteArray, length: Int = packet.size): String? {
        if (length < 12) return null // header is 12 bytes
        val qdCount = ((packet[4].toInt() and 0xFF) shl 8) or (packet[5].toInt() and 0xFF)
        if (qdCount < 1) return null

        var offset = 12
        val labels = StringBuilder()
        while (offset < length) {
            val len = packet[offset].toInt() and 0xFF
            if (len == 0) {
                offset += 1
                break
            }
            // Compression pointers shouldn't appear in the question section of a query;
            // bail out rather than risk an infinite loop on a malformed packet.
            if (len and 0xC0 == 0xC0) return null
            offset += 1
            if (offset + len > length) return null
            if (labels.isNotEmpty()) labels.append('.')
            for (i in 0 until len) {
                labels.append(packet[offset + i].toInt().toChar())
            }
            offset += len
        }
        return if (labels.isEmpty()) null else labels.toString().lowercase()
    }

    /**
     * Builds a synthetic response to [query] with RCODE=3 (NXDOMAIN) — the standard,
     * honest way to say "this name does not resolve," rather than a fabricated IP.
     * Echoes the original ID and question section so resolvers accept it as a real reply.
     */
    fun buildNxDomainResponse(query: ByteArray, length: Int = query.size): ByteArray {
        // Find end of question section (name + QTYPE(2) + QCLASS(2)) to know how much to copy.
        var offset = 12
        while (offset < length) {
            val len = query[offset].toInt() and 0xFF
            if (len == 0) { offset += 1; break }
            if (len and 0xC0 == 0xC0) { offset += 2; break }
            offset += 1 + len
        }
        val questionEnd = (offset + 4).coerceAtMost(length) // + QTYPE/QCLASS
        val response = ByteArray(questionEnd)
        System.arraycopy(query, 0, response, 0, questionEnd)

        // ID (bytes 0-1) copied as-is.
        // Flags: QR=1, Opcode=0, AA=0, TC=0, RD copied from query, RA=1, Z=0, RCODE=3 (NXDOMAIN)
        val queryRd = query[2].toInt() and 0x01
        response[2] = (0x80 or queryRd).toByte()          // QR=1, RD echoed
        response[3] = (0x80 or 0x03).toByte()              // RA=1, RCODE=3 (NXDOMAIN)
        // QDCOUNT stays as in query (bytes 4-5), ANCOUNT/NSCOUNT/ARCOUNT = 0
        response[6] = 0; response[7] = 0
        response[8] = 0; response[9] = 0
        response[10] = 0; response[11] = 0
        return response
    }
}
