package com.mickstarify.zooforzotero.search

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan

object SnippetFormatter {
    /** "[label]…words before **match** words after…" with the match in bold. */
    fun format(snippet: TextSnippet, label: String): CharSequence =
        SpannableStringBuilder().apply {
            append(label)
            append(snippet.before)
            val start = length
            append(snippet.match)
            setSpan(StyleSpan(Typeface.BOLD), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(snippet.after)
        }
}
