package com.example.data.ai

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * Pure Kotlin, zero-dependency PDF Text Extractor.
 * Extracts text streams from PDF files by locating 'stream ... endstream' blocks,
 * decompressing FlateDecode (ZLIB) streams, and decoding PDF text operators (BT..ET, Tj, TJ).
 * Also falls back to scanning uncompressed text objects and parenthesized strings.
 */
object PdfTextExtractor {

    fun extractText(file: File): String {
        return try {
            file.inputStream().use { extractText(it) }
        } catch (_: Exception) {
            ""
        }
    }

    fun extractText(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { extractText(it) } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun extractText(inputStream: InputStream): String {
        val bytes = inputStream.readBytes()
        if (bytes.isEmpty()) return ""

        val extractedBlocks = mutableListOf<String>()

        // 1. Locate all stream ... endstream chunks
        val streamIndices = findStreamBlocks(bytes)

        for ((start, end) in streamIndices) {
            if (end <= start) continue
            val streamBytes = bytes.copyOfRange(start, end)

            // Try decompressing using Inflater
            val decompressed = tryDecompressFlate(streamBytes) ?: streamBytes
            val textFromStream = parsePdfTextOperators(decompressed)
            if (textFromStream.isNotBlank()) {
                extractedBlocks.add(textFromStream)
            }
        }

        // 2. If stream parsing yielded very little text, scan entire file for parenthesized text strings
        val combined = extractedBlocks.joinToString("\n\n").trim()
        if (combined.length < 50) {
            val fallbackText = fallbackScanStrings(bytes)
            if (fallbackText.length > combined.length) {
                return fallbackText
            }
        }

        return combined
    }

    private fun findStreamBlocks(bytes: ByteArray): List<Pair<Int, Int>> {
        val blocks = mutableListOf<Pair<Int, Int>>()
        val streamTag = "stream".toByteArray(StandardCharsets.US_ASCII)
        val endstreamTag = "endstream".toByteArray(StandardCharsets.US_ASCII)

        var idx = 0
        while (idx < bytes.size - 15) {
            val sPos = indexOfSubArray(bytes, streamTag, idx)
            if (sPos < 0) break

            // Skip past 'stream' and possible \r\n or \n
            var dataStart = sPos + streamTag.size
            if (dataStart < bytes.size && bytes[dataStart] == '\r'.code.toByte()) dataStart++
            if (dataStart < bytes.size && bytes[dataStart] == '\n'.code.toByte()) dataStart++

            val ePos = indexOfSubArray(bytes, endstreamTag, dataStart)
            if (ePos < 0) break

            blocks.add(Pair(dataStart, ePos))
            idx = ePos + endstreamTag.size
        }
        return blocks
    }

    private fun tryDecompressFlate(data: ByteArray): ByteArray? {
        // Try standard Inflater with ZLIB header
        try {
            val inflater = Inflater(false)
            inflater.setInput(data)
            val bos = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (!inflater.finished() && !inflater.needsInput()) {
                val count = inflater.inflate(buf)
                if (count <= 0) break
                bos.write(buf, 0, count)
            }
            inflater.end()
            if (bos.size() > 0) return bos.toByteArray()
        } catch (_: Exception) {}

        // Try raw deflate (no zlib wrapper)
        try {
            val inflater = Inflater(true)
            inflater.setInput(data)
            val bos = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (!inflater.finished() && !inflater.needsInput()) {
                val count = inflater.inflate(buf)
                if (count <= 0) break
                bos.write(buf, 0, count)
            }
            inflater.end()
            if (bos.size() > 0) return bos.toByteArray()
        } catch (_: Exception) {}

        return null
    }

    /**
     * Parses PDF content stream operators:
     * BT (Begin Text) ... ET (End Text)
     * (text) Tj
     * [(text) 100 (more)] TJ
     * ' / " / T* (new lines)
     */
    private fun parsePdfTextOperators(streamBytes: ByteArray): String {
        val streamStr = String(streamBytes, StandardCharsets.ISO_8859_1)
        val sb = StringBuilder()

        // Match BT ... ET blocks
        val btRegex = Regex("BT([\\s\\S]*?)ET")
        val matches = btRegex.findAll(streamStr).toList()

        val textBlocks = if (matches.isNotEmpty()) {
            matches.map { it.groupValues[1] }
        } else {
            listOf(streamStr)
        }

        for (block in textBlocks) {
            val blockSb = StringBuilder()
            var i = 0
            while (i < block.length) {
                val ch = block[i]
                if (ch == '(') {
                    // Extract parenthesized literal string with escape support
                    val str = extractPdfLiteralString(block, i)
                    i += str.second
                    // Check if followed by Tj or ' or "
                    val nextSub = block.substring(i, minOf(i + 10, block.length)).trimStart()
                    blockSb.append(decodePdfString(str.first))
                    if (nextSub.startsWith("T*") || nextSub.startsWith("'") || nextSub.startsWith("\"")) {
                        blockSb.append("\n")
                    } else {
                        blockSb.append(" ")
                    }
                } else if (ch == '[') {
                    // Extract TJ array e.g. [(Hello) -20 (World)] TJ
                    val arr = extractPdfArray(block, i)
                    i += arr.second
                    val textInArray = parseArrayForStrings(arr.first)
                    if (textInArray.isNotBlank()) {
                        blockSb.append(textInArray).append(" ")
                    }
                } else if (ch == '\n' || ch == '\r') {
                    // Check for T* or Td
                    blockSb.append(" ")
                    i++
                } else {
                    i++
                }
            }
            val cleaned = blockSb.toString().trim()
            if (cleaned.length > 2) {
                sb.append(cleaned).append("\n")
            }
        }

        return sb.toString().trim()
    }

    private fun extractPdfLiteralString(s: String, startIdx: Int): Pair<String, Int> {
        val sb = StringBuilder()
        var depth = 1
        var i = startIdx + 1
        var escaped = false

        while (i < s.length && depth > 0) {
            val c = s[i]
            if (escaped) {
                sb.append(c)
                escaped = false
            } else if (c == '\\') {
                sb.append(c)
                escaped = true
            } else if (c == '(') {
                depth++
                sb.append(c)
            } else if (c == ')') {
                depth--
                if (depth > 0) sb.append(c)
            } else {
                sb.append(c)
            }
            i++
        }
        return Pair(sb.toString(), i - startIdx)
    }

    private fun extractPdfArray(s: String, startIdx: Int): Pair<String, Int> {
        var depth = 1
        var i = startIdx + 1
        while (i < s.length && depth > 0) {
            val c = s[i]
            if (c == '[') depth++
            else if (c == ']') depth--
            i++
        }
        val inner = if (i <= s.length) s.substring(startIdx + 1, i - 1) else ""
        return Pair(inner, i - startIdx)
    }

    private fun parseArrayForStrings(arrContent: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < arrContent.length) {
            if (arrContent[i] == '(') {
                val (str, consumed) = extractPdfLiteralString(arrContent, i)
                i += consumed
                sb.append(decodePdfString(str))
            } else {
                i++
            }
        }
        return sb.toString()
    }

    private fun decodePdfString(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                val next = s[i + 1]
                when (next) {
                    'n' -> { out.append("\n"); i += 2 }
                    'r' -> { out.append("\r"); i += 2 }
                    't' -> { out.append("\t"); i += 2 }
                    'b' -> { out.append("\b"); i += 2 }
                    'f' -> { i += 2 }
                    '(', ')', '\\' -> { out.append(next); i += 2 }
                    in '0'..'7' -> {
                        // Octal sequence \ddd
                        var octalLen = 1
                        while (octalLen < 3 && i + 1 + octalLen < s.length && s[i + 1 + octalLen] in '0'..'7') {
                            octalLen++
                        }
                        val octalStr = s.substring(i + 1, i + 1 + octalLen)
                        val charCode = octalStr.toIntOrNull(8) ?: 32
                        out.append(charCode.toChar())
                        i += 1 + octalLen
                    }
                    else -> { out.append(next); i += 2 }
                }
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    private fun fallbackScanStrings(bytes: ByteArray): String {
        val s = String(bytes, StandardCharsets.ISO_8859_1)
        val regex = Regex("\\(([A-Za-z0-9\\s\\-.,;:_!?'\"/()]{3,100})\\)")
        val found = regex.findAll(s).map { it.groupValues[1] }
            .filter { it.length >= 3 && it.any { c -> c.isLetter() } }
            .distinct()
            .joinToString("\n")
        return found
    }

    private fun indexOfSubArray(source: ByteArray, target: ByteArray, fromIndex: Int): Int {
        if (target.isEmpty() || fromIndex >= source.size) return -1
        outer@ for (i in fromIndex..(source.size - target.size)) {
            for (j in target.indices) {
                if (source[i + j] != target[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
