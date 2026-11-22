package com.inkvol.app.data.sync

import android.net.Uri
import android.util.Base64
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** WebDAV 操作失败：statusCode=HTTP 状态码（0=非 HTTP 失败，如 DNS/SSL/超时），供错误分类与退避。 */
class DavException(message: String, val statusCode: Int = 0) : IOException(message)

/** PROPFIND 单条目（name 已 URL 解码；self 目录条目由调用方过滤）。 */
data class DavEntry(
    val name: String,
    val isDirectory: Boolean,
    val lastModified: Long,
    val contentLength: Long,
)

/**
 * 最小 WebDAV 客户端（M4.5）：PROPFIND / GET / PUT / MKCOL / DELETE + Basic Auth。
 * 基于 HttpURLConnection，工程零新依赖纪律；PROPFIND/MKCOL 用自定义 method（Android 支持）。
 * 全部方法为阻塞 IO，必须在 IO 协程调用。
 *
 * 路径约定：serverUrl 归一为以 / 结尾；basePath 以 / 开头结尾；
 * 子路径如 "devices/" 或 "books/<fingerprint>.epub"，逐段 URL 编码（兼容中文目录名）。
 */
class WebDavClient(
    serverUrl: String,
    private val basePath: String,
    account: String,
    password: String,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 30_000,
) {

    private val serverUrl = serverUrl.trim().let { if (it.endsWith("/")) it else "$it/" }
    private val authHeader = "Basic " + Base64.encodeToString(
        "$account:$password".toByteArray(Charsets.UTF_8),
        Base64.NO_WRAP,
    )

    private val propfindBody = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:propfind xmlns:d="DAV:"><d:prop>
        <d:displayname/><d:getlastmodified/><d:getcontentlength/><d:resourcetype/>
        </d:prop></d:propfind>
    """.trimIndent().toByteArray(Charsets.UTF_8)

    /** 列目录（Depth:1）。远端目录不存在（404）返回空表——首次启用尚未 MKCOL 属正常。 */
    fun list(subPath: String): List<DavEntry> {
        val conn = connect("PROPFIND", subPath, propfindBody, "application/xml; charset=utf-8")
        return try {
            conn.setRequestProperty("Depth", "1")
            when (val code = conn.responseCode) {
                HTTP_MULTI_STATUS -> parseMultistatus(conn.inputStream)
                404 -> emptyList()
                else -> throw DavException("PROPFIND $subPath -> HTTP $code ${errBody(conn)}", code)
            }
        } catch (e: DavException) {
            throw e
        } catch (e: IOException) {
            throw DavException("PROPFIND $subPath 失败: ${e.message}", 0)
        } finally {
            conn.disconnect()
        }
    }

    /** 读文件。404 抛 DavException(404) 由调用方区分「远端没有」。 */
    fun download(subPath: String): ByteArray {
        val conn = connect("GET", subPath, null, null)
        return try {
            when (val code = conn.responseCode) {
                200 -> conn.inputStream.use { it.readBytes() }
                else -> throw DavException("GET $subPath -> HTTP $code ${errBody(conn)}", code)
            }
        } catch (e: DavException) {
            throw e
        } catch (e: IOException) {
            throw DavException("GET $subPath 失败: ${e.message}", 0)
        } finally {
            conn.disconnect()
        }
    }

    /** 整文件上传（WebDAV PUT 原子写；doc/书籍文件均远小于坚果云 500MB 单文件限制）。 */
    fun upload(subPath: String, bytes: ByteArray) {
        val conn = connect("PUT", subPath, bytes, "application/octet-stream")
        try {
            when (val code = conn.responseCode) {
                200, 201, 204, 207 -> Unit
                else -> throw DavException("PUT $subPath -> HTTP $code ${errBody(conn)}", code)
            }
        } catch (e: DavException) {
            throw e
        } catch (e: IOException) {
            throw DavException("PUT $subPath 失败: ${e.message}", 0)
        } finally {
            conn.disconnect()
        }
    }

    /** 建目录：201=已创建，405/301=已存在（集合类操作常见），其余视为失败。 */
    fun makeCollection(subPath: String) {
        val conn = connect("MKCOL", subPath, null, null)
        try {
            when (val code = conn.responseCode) {
                201, 405, 301 -> Unit
                else -> throw DavException("MKCOL $subPath -> HTTP $code ${errBody(conn)}", code)
            }
        } catch (e: DavException) {
            throw e
        } catch (e: IOException) {
            throw DavException("MKCOL $subPath 失败: ${e.message}", 0)
        } finally {
            conn.disconnect()
        }
    }

    /** 删除远端文件/目录：204/200 成功，404 视为已删除（幂等）。 */
    fun delete(subPath: String) {
        val conn = connect("DELETE", subPath, null, null)
        try {
            when (val code = conn.responseCode) {
                200, 204, 404 -> Unit
                else -> throw DavException("DELETE $subPath -> HTTP $code ${errBody(conn)}", code)
            }
        } catch (e: DavException) {
            throw e
        } catch (e: IOException) {
            throw DavException("DELETE $subPath 失败: ${e.message}", 0)
        } finally {
            conn.disconnect()
        }
    }

    // ---------- 内部 ----------

    private fun connect(
        method: String,
        subPath: String,
        body: ByteArray?,
        contentType: String?,
    ): HttpURLConnection {
        val conn = URL(serverUrl + encodedPath(subPath)).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.setRequestProperty("Authorization", authHeader)
        conn.setRequestProperty("User-Agent", "Inkvol/$SYNC_FORMAT_VERSION")
        if (body != null) {
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(body.size)
            if (contentType != null) conn.setRequestProperty("Content-Type", contentType)
        }
        return conn
    }

    /** 逐段 URL 编码（每段独立 encode，保留 / 结构；中文目录名兼容）。 */
    private fun encodedPath(subPath: String): String {
        val joined = basePath.trimEnd('/') + "/" + subPath.trimStart('/')
        val segments = joined.split('/').filter { it.isNotEmpty() }
        return "/" + segments.joinToString("/") { Uri.encode(it) }
    }

    private fun errBody(conn: HttpURLConnection): String = runCatching {
        conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(300)
    }.getOrNull() ?: ""

    private fun parseMultistatus(stream: InputStream): List<DavEntry> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(stream, null)
        val entries = ArrayList<DavEntry>()
        var href: String? = null
        var displayName: String? = null
        var lastModified = 0L
        var contentLength = -1L
        var isDir = false
        var inProp = false
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                    "response" -> {
                        href = null
                        displayName = null
                        lastModified = 0
                        contentLength = -1
                        isDir = false
                        inProp = false
                    }
                    "prop" -> inProp = true
                    "href" -> if (!inProp && href == null) href = parser.nextText()
                    "displayname" -> if (inProp) displayName = parser.nextText()
                    "getlastmodified" -> if (inProp) lastModified = parseHttpDate(parser.nextText())
                    "getcontentlength" -> if (inProp) {
                        contentLength = parser.nextText().toLongOrNull() ?: -1L
                    }
                    "collection" -> if (inProp) isDir = true
                }
                XmlPullParser.END_TAG -> if (parser.name.substringAfter(':') == "response") {
                    val name = displayName?.takeIf { it.isNotEmpty() }
                        ?: href?.let { h ->
                            Uri.decode(h.trimEnd('/').substringAfterLast('/'))
                        }
                        ?: continue
                    entries += DavEntry(name, isDir, lastModified, contentLength)
                }
                XmlPullParser.END_DOCUMENT -> return entries
            }
        }
    }

    private fun parseHttpDate(value: String): Long {
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz" to TimeZone.getTimeZone("GMT"),
            "yyyy-MM-dd'T'HH:mm:ss'Z'" to TimeZone.getTimeZone("UTC"),
        )
        formats.forEach { (pattern, zone) ->
            runCatching {
                val fmt = SimpleDateFormat(pattern, Locale.US)
                fmt.timeZone = zone
                return fmt.parse(value)?.time ?: 0L
            }
        }
        return 0L
    }

    private companion object {
        const val HTTP_MULTI_STATUS = 207
    }
}
