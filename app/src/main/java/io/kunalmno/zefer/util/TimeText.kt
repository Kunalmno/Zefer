package io.kunalmno.zefer.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "in 3 days", "in 5 hrs", "in <1 hr" */
fun inText(seconds: Long): String = when {
    seconds < 3_600 -> "in <1 hr"
    seconds < 86_400 -> "in ${seconds / 3_600} hrs"
    seconds < 2 * 86_400 -> "in 1 day"
    else -> "in ${seconds / 86_400} days"
}

/** "19 NOV" for (baseMillis + plusSeconds). */
fun dateLabel(baseMillis: Long, plusSeconds: Long): String =
    SimpleDateFormat("d MMM", Locale.getDefault())
        .format(Date(baseMillis + plusSeconds * 1000))
        .uppercase(Locale.getDefault())
