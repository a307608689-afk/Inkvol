package com.inkvol.app.data.source

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * MOBI（PDB）底层编解码的单一真源：整数读取、图片魔数、PalmDOC LZ77 解压。
 *
 * [com.inkvol.app.data.import.MobiParser]（小说解析）与 [MobiImageIndex] /
 * [MobiImageSource]（漫画页提取）共用——两处各留一份解压器迟早漂移。
 */
object MobiCodec {

    fun u16(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    fun u32(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or (b[off + 3].toInt() and 0xFF)

    /** 图片魔数 → 扩展名；非图片资源记录返回 null。 */
    fun imageExtOf(b: ByteArray): String? {
        if (b.size < 8) return null
        return when {
            b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte() -> "jpg"
            b[0] == 0x89.toByte() && b[1] == 0x50.toByte() &&
                b[2] == 0x4E.toByte() && b[3] == 0x47.toByte() -> "png"
            b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() &&
                b[2] == 'F'.code.toByte() && b[3] == '8'.code.toByte() -> "gif"
            b[0] == 'B'.code.toByte() && b[1] == 'M'.code.toByte() -> "bmp"
            else -> null
        }
    }

    /** PalmDOC LZ77 解压。输出为 textEncoding 编码的字节流。 */
    fun palmDocDecompress(
        data: ByteArray,
        charset: Charset,
        out: ByteArrayOutputStream,
    ) {
        // 可增长的字节缓冲（回引用需要随机读已解压内容）
        var buf = ByteArray(maxOf(64, data.size * 2))
        var size = 0
        fun ensure(extra: Int) {
            if (size + extra > buf.size) {
                buf = buf.copyOf(maxOf(buf.size * 2, size + extra))
            }
        }
        fun writeByte(b: Int) {
            ensure(1)
            buf[size++] = b.toByte()
        }
        fun writeChar(code: Int) {
            val encoded = code.toChar().toString().toByteArray(charset)
            ensure(encoded.size)
            encoded.copyInto(buf, size)
            size += encoded.size
        }

        var i = 0
        while (i < data.size) {
            val b = data[i].toInt() and 0xFF
            when {
                b == 0 -> { writeByte(0); i += 1 }
                b <= 8 -> {
                    val n = minOf(b, data.size - i - 1)
                    ensure(n)
                    for (j in 0 until n) buf[size++] = data[i + 1 + j]
                    i += b + 1
                }
                b <= 0x7F -> { writeByte(b); i += 1 }
                b <= 0xBF -> {
                    // 两字节回引用：distance 11 位、length 3 位（+3）
                    if (i + 1 >= data.size) break
                    val g = (b shl 8) or (data[i + 1].toInt() and 0xFF)
                    i += 2
                    val distance = (g shr 3) and 0x7FF
                    val length = (g and 0x7) + 3
                    ensure(length)
                    var src = size - distance
                    if (src < 0) src = 0
                    repeat(length) { j ->
                        val v = if (src + j < size) buf[src + j].toInt() and 0xFF else 0x20
                        buf[size++] = v.toByte()
                    }
                }
                b == 0xC0 -> {
                    // space + ascii80
                    if (i + 1 >= data.size) break
                    writeByte(0x20)
                    writeByte((data[i + 1].toInt() and 0xFF) xor 0x80)
                    i += 2
                }
                b <= 0xDF -> {
                    // 两字节 unicode（UTF-8 风格 0x80-0x7FF）
                    if (i + 1 >= data.size) break
                    val c = data[i + 1].toInt() and 0xFF
                    writeChar(((b and 0x1F) shl 6) or (c and 0x3F))
                    i += 2
                }
                else -> { writeByte(b); i += 1 }
            }
        }
        out.write(buf, 0, size)
    }
}
