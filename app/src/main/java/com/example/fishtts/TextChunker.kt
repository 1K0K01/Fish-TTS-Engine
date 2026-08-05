package com.example.fishtts

import java.text.BreakIterator
import java.util.Locale

object TextChunker {

    fun split(text: String, locale: Locale, maxChars: Int): List<String> {
        if (text.isBlank()) return emptyList()

        val limit = if (maxChars <= 0) Int.MAX_VALUE else maxChars
        if (limit == Int.MAX_VALUE) return listOf(text)

        val iterator = BreakIterator.getSentenceInstance(locale)
        iterator.setText(text)

        val chunks = mutableListOf<String>()
        val builder = StringBuilder()

        var start = iterator.first()
        var end = iterator.next()

        while (end != BreakIterator.DONE) {
            val sentence = text.substring(start, end).trim()

            if (sentence.isNotEmpty()) {
                if (sentence.length > limit) {
                    if (builder.isNotEmpty()) {
                        chunks.add(builder.toString())
                        builder.clear()
                    }

                    var pos = 0
                    while (pos < sentence.length) {
                        val next = minOf(pos + limit, sentence.length)
                        chunks.add(sentence.substring(pos, next))
                        pos = next
                    }
                } else if (builder.length + sentence.length > limit) {
                    chunks.add(builder.toString())
                    builder.clear()
                    builder.append(sentence)
                } else {
                    builder.append(sentence)
                }
            }

            start = end
            end = iterator.next()
        }

        if (builder.isNotEmpty()) {
            chunks.add(builder.toString())
        }

        return chunks
    }
}
