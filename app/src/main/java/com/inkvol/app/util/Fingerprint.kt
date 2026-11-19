package com.inkvol.app.util

import java.io.InputStream
import java.security.MessageDigest

/**
 * 内容指纹：size + 头/中/尾各 64KB 的 SHA-256。
 *
 * 与路径无关，文件移动、改名、SAF 重新授权都不影响；
 * 对「文件夹即一本」的漫画，用排序后的 (文件名, 大小) 列表做指纹。
 */
object Fingerprint {

    private const val SAMPLE = 64 * 1024

    fun ofArchive(size: Long, open: () -> InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("archive:v1:$size:".toByteArray())
        open().use { input ->
            sample(input, 0L, SAMPLE, digest)
            if (size > SAMPLE * 2) {
                sample(input, size / 2 - SAMPLE / 2, SAMPLE, digest)
            }
            if (size > SAMPLE) {
                sample(input, (size - SAMPLE).coerceAtLeast(0), SAMPLE, digest)
            }
        }
        return digest.digest().toHex()
    }

    fun ofFolder(entries: List<Pair<String, Long>>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("folder:v1:${entries.size}:".toByteArray())
        entries.sortedBy { it.first }.forEach { (name, size) ->
            digest.update(name.toByteArray())
            digest.update(':'.code.toByte())
            digest.update(size.toString().toByteArray())
            digest.update('\n'.code.toByte())
        }
        return digest.digest().toHex()
    }

    fun ofBytes(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun sample(input: InputStream, start: Long, length: Int, digest: MessageDigest) {
        skipFully(input, start)
        val buffer = ByteArray(8 * 1024)
        var remaining = length
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (read <= 0) break
            digest.update(buffer, 0, read)
            remaining -= read
        }
    }

    private fun skipFully(input: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else if (input.read() >= 0) {
                remaining -= 1
            } else {
                break
            }
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }
}
