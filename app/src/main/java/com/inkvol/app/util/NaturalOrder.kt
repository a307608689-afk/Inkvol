package com.inkvol.app.util

/**
 * 自然排序：page_2 排在 page_10 前面。
 * 漫画页名常见 `001.jpg` / `第2话_10.png`，字典序会让 10 跑到 2 前面。
 */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var endA = i
                while (endA < a.length && a[endA].isDigit()) endA++
                var endB = j
                while (endB < b.length && b[endB].isDigit()) endB++
                val numA = a.substring(i, endA).trimStart('0')
                val numB = b.substring(j, endB).trimStart('0')
                val byLength = numA.length.compareTo(numB.length)
                if (byLength != 0) return byLength
                val byValue = numA.compareTo(numB)
                if (byValue != 0) return byValue
                i = endA
                j = endB
            } else {
                if (ca != cb) return ca.lowercaseChar().compareTo(cb.lowercaseChar())
                i++
                j++
            }
        }
        return (a.length - i).compareTo(b.length - j)
    }
}
