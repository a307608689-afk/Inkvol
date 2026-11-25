package com.inkvol.app.data.sync

import android.net.Uri
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** WebDAV 操作失败：statusCode=HTTP 状态码（0=非 HTTP 失败，如 DNS/SSL/超时），供错误分类与退避。 */
class DavException(message: String, val statusCode: Int = 0, cause: Throwable? = null) :
    IOException(message, cause)

/** PROPFIND 单条目（name 已 URL 解码；self 目录条目由调用方过滤）。 */
data class DavEntry(
    val name: String,
    val isDirectory: Boolean,
    val lastModified: Long,
    val contentLength: Long,
)

/**
 * 最小 WebDAV 客户端（M4.5）：PROPFIND / GET / PUT / MKCOL / DELETE + Basic Auth。
 *
 * 为什么用 OkHttp 而不是 HttpURLConnection：Android 的 HttpURLConnection.setRequestMethod
 * 只允许 OPTIONS/GET/HEAD/POST/PUT/DELETE/TRACE/PATCH，PROPFIND 与 MKCOL 会直接抛
 * ProtocolException（"Expected one of [...] but was PROPFIND"），请求根本发不出去。
 *
 * 重定向刻意关闭自动跟随：OkHttp 对 301/302 会把非 GET 方法静默改成 GET，PROPFIND 变 GET
 * 只会拿到目录 HTML/405；这里只对 301/307/308 按原方法重试（最多 2 跳），Authorization 一并保留。
 *
 * 全部方法为阻塞 IO，必须在 IO 协程调用。
 * 路径约定：serverUrl 归一为不带尾斜杠；basePath 以 / 开头结尾；
 * 子路径如 "" / "devices/" / "books/<fingerprint>.epub"，逐段 URL 编码（兼容中文目录名）。
 */
class WebDavClient(
    serverUrl: String,
    private val basePath: String,
    account: String,
    password: String,
    connectTimeoutMs: Int = 10_000,
    readTimeoutMs: Int = 30_000,
    allowInsecureTls: Boolean = false,
) {

    private val origin = serverUrl.trim().trimEnd('/')
    private val authHeader = Credentials.basic(account, password, Charsets.UTF_8)

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .writeTimeout(readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .apply {
            if (allowInsecureTls) {
                val trustManager = TrustAllManager()
                val context = SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
                }
                sslSocketFactory(context.socketFactory, trustManager)
                hostnameVerifier { _, _ -> true }
            }
        }
        .build()

    private val xmlMedia = "application/xml; charset=utf-8".toMediaType()
    private val binaryMedia = "application/octet-stream".toMediaType()

    private val propfindBody = """
        <?xml version="1.0" encoding="utf-8"?>
        <d:propfind xmlns:d="DAV:"><d:prop>
        <d:displayname/><d:getlastmodified/><d:getcontentlength/><d:resourcetype/>
        </d:prop></d:propfind>
    """.trimIndent().toByteArray(Charsets.UTF_8)

    /** 列目录（Depth:1）。远端目录不存在（404）返回空表——首次启用尚未建目录属正常。 */
    fun list(subPath: String): List<DavEntry> {
        val body = propfindBody.toRequestBody(xmlMedia)
        return execute("PROPFIND", subPath, body, mapOf("Depth" to "1")) { response ->
            when (response.code) {
                HTTP_MULTI_STATUS -> parseMultistatus(response.body.byteStream())
                404 -> emptyList()
                else -> throw statusError("PROPFIND", subPath, response)
            }
        }
    }

    /** 读文件。404 抛 DavException(404) 由调用方区分「远端没有」。 */
    fun download(subPath: String): ByteArray =
        execute("GET", subPath) { response ->
            if (response.code == 200) {
                response.body.bytes()
            } else {
                throw statusError("GET", subPath, response)
            }
        }

    /** 整文件上传（WebDAV PUT 原子写；doc/书籍文件均远小于坚果云 500MB 单文件限制）。 */
    fun upload(subPath: String, bytes: ByteArray) {
        execute("PUT", subPath, bytes.toRequestBody(binaryMedia)) { response ->
            if (response.code !in OK_CODE_SET) throw statusError("PUT", subPath, response)
        }
    }

    /**
     * 逐级创建同步根与子目录（幂等：405=已存在）。
     * 父集合不存在时单次 MKCOL 会 409，所以按层级一层层建；
     * 兼容用户在配置里填多级目录（/a/b/）。
     */
    fun ensureCollections(vararg subDirs: String) {
        val baseSegments = basePath.trim('/').split('/').filter { it.isNotEmpty() }
        for (i in baseSegments.indices) {
            mkcolAbsolute("/" + baseSegments.take(i + 1).joinToString("/"))
        }
        subDirs.forEach { sub ->
            val segments = sub.trim('/').split('/').filter { it.isNotEmpty() }
            for (i in segments.indices) {
                mkcolAbsolute("/" + (baseSegments + segments.take(i + 1)).joinToString("/"))
            }
        }
    }

    /** 删除远端文件/目录：204/200 成功，404 视为已删除（幂等）。 */
    fun delete(subPath: String) {
        execute("DELETE", subPath) { response ->
            when (response.code) {
                200, 204, 404 -> Unit
                else -> throw statusError("DELETE", subPath, response)
            }
        }
    }

    // ---------- 内部 ----------

    private fun mkcolAbsolute(absPath: String) {
        executeUrl("MKCOL", urlForAbs(absPath), absPath, null) { response ->
            when (response.code) {
                201, 405 -> Unit
                else -> throw statusError("MKCOL", absPath, response)
            }
        }
    }

    private fun <T> execute(
        method: String,
        subPath: String,
        body: RequestBody? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        block: (Response) -> T,
    ): T = executeUrl(method, urlForSub(subPath), subPath, body, extraHeaders, block)

    private fun <T> executeUrl(
        method: String,
        startUrl: String,
        label: String,
        body: RequestBody?,
        extraHeaders: Map<String, String> = emptyMap(),
        block: (Response) -> T,
    ): T {
        var url = startUrl
        var redirects = 0
        while (true) {
            val request = try {
                Request.Builder()
                    .url(url)
                    .header("Authorization", authHeader)
                    .header("User-Agent", "Inkvol/$SYNC_FORMAT_VERSION")
                    .apply { extraHeaders.forEach { (key, value) -> header(key, value) } }
                    .method(method, body)
                    .build()
            } catch (e: IllegalArgumentException) {
                throw DavException("$method $label 请求无效：${e.message ?: ""}", 0, e)
            }
            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                throw DavException("$method $label 失败：${e.message ?: e.javaClass.simpleName}", 0, e)
            } catch (e: RuntimeException) {
                throw DavException("$method $label 失败：${e.message ?: e.javaClass.simpleName}", 0, e)
            }
            val code = response.code
            if ((code == 301 || code == 307 || code == 308) && redirects < MAX_REDIRECTS) {
                val location = response.header("Location")
                response.close()
                if (location.isNullOrBlank()) {
                    throw DavException("$method $label -> HTTP $code（缺少 Location）", code)
                }
                url = resolveUrl(url, location)
                redirects++
                continue
            }
            return response.use(block)
        }
    }

    private fun resolveUrl(base: String, location: String): String {
        val baseUrl = base.toHttpUrlOrNull() ?: throw DavException("无效地址：$base", 0)
        return baseUrl.resolve(location)?.toString()
            ?: throw DavException("无法解析重定向地址：$location", 0)
    }

    /** 子路径 URL（含 basePath）。目录语义（空路径或 / 结尾）保留尾斜杠——WebDAV 集合必须有。 */
    private fun urlForSub(subPath: String): String {
        val raw = origin + encodeSegments(
            basePath.trimEnd('/') + "/" + subPath.trimStart('/'),
            isDirectoryPath(subPath),
        )
        return raw.toHttpUrlOrNull()?.toString()
            ?: throw DavException("服务器地址无效：$origin", 0)
    }

    /** 相对服务器根（已含 basePath）的绝对路径 URL，供逐级 MKCOL 使用。 */
    private fun urlForAbs(absPath: String): String {
        val raw = origin + encodeSegments(absPath, dirLike = true)
        return raw.toHttpUrlOrNull()?.toString()
            ?: throw DavException("服务器地址无效：$origin", 0)
    }

    private fun isDirectoryPath(path: String): Boolean = path.isBlank() || path.endsWith("/")

    /** 逐段 URL 编码（每段独立 encode，保留 / 结构；中文目录名兼容）。 */
    private fun encodeSegments(path: String, dirLike: Boolean): String {
        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return "/"
        val encoded = "/" + segments.joinToString("/") { Uri.encode(it) }
        return if (dirLike) "$encoded/" else encoded
    }

    private fun statusError(method: String, label: String, response: Response): DavException {
        val snippet = runCatching { response.peekBody(300).string() }.getOrDefault("")
            .replace(Regex("\\s+"), " ")
            .trim()
        val suffix = if (snippet.isNotBlank()) " · $snippet" else ""
        return DavException("$method $label -> HTTP ${response.code}$suffix", response.code)
    }

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

    /** 自签证书模式的信任管理器（仅在用户显式开启「允许自签证书」时使用）。 */
    private class TrustAllManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit

        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) = Unit

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private companion object {
        const val HTTP_MULTI_STATUS = 207
        const val MAX_REDIRECTS = 2
        val OK_CODE_SET = setOf(200, 201, 204, 207)
    }
}
