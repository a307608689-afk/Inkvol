package com.inkvol.app.data.import

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * ComicInfo.xml（Anansi 规范）最小解析：只取书架需要的字段。
 * M2 再补 Count/Summary/Year/LanguageISO/AgeRating 等。
 */
object ComicInfoParser {

    data class Meta(
        val title: String? = null,
        val series: String? = null,
        val number: String? = null,
        val volume: String? = null,
        val writer: String? = null,
        val tags: String? = null,
    )

    fun parseZip(zip: ZipFile): Meta? {
        val entry = zip.entries().asSequence()
            .firstOrNull { it.name.substringAfterLast('/').equals("ComicInfo.xml", ignoreCase = true) }
            ?: return null
        return runCatching {
            zip.getInputStream(entry).use { parse(it) }
        }.getOrNull()
    }

    fun parse(input: InputStream): Meta {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        var event = parser.eventType
        var title: String? = null
        var series: String? = null
        var number: String? = null
        var volume: String? = null
        var writer: String? = null
        var tags: String? = null

        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "Title" -> title = parser.nextText().trim().ifBlank { null }
                    "Series" -> series = parser.nextText().trim().ifBlank { null }
                    "Number" -> number = parser.nextText().trim().ifBlank { null }
                    "Volume" -> volume = parser.nextText().trim().ifBlank { null }
                    "Writer" -> writer = parser.nextText().trim().ifBlank { null }
                    "Genre", "Tags" -> {
                        val value = parser.nextText().trim().ifBlank { null }
                        tags = listOfNotNull(tags, value).joinToString(", ").ifBlank { null }
                    }

                    else -> Unit
                }
            }
            event = parser.next()
        }
        return Meta(title, series, number, volume, writer, tags)
    }
}
