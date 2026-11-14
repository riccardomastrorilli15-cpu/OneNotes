package com.onenotes.app.util

import android.content.Context
import com.onenotes.app.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Date / time / size formatting used across the app. */
object TimeUtils {

    /**
     * "Oggi 14:32", "Ieri 09:10", "12 mar, 18:02" or "12 mar 2024" depending on how old the
     * timestamp is.
     */
    fun relative(context: Context, millis: Long): String {
        if (millis <= 0L) return ""
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis
        val startOfYesterday = startOfToday - DAY_MS
        val startOfYear = with(Calendar.getInstance()) {
            set(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            timeInMillis
        }

        val time = timeFormat(context).format(Date(millis))
        return when {
            millis >= startOfToday -> context.getString(R.string.date_today) + " " + time
            millis >= startOfYesterday -> context.getString(R.string.date_yesterday) + " " + time
            millis >= startOfYear -> SimpleDateFormat("d MMM", locale(context)).format(Date(millis)) +
                ", " + time
            else -> SimpleDateFormat("d MMM yyyy", locale(context)).format(Date(millis))
        }
    }

    fun dateTime(context: Context, millis: Long): String {
        if (millis <= 0L) return ""
        return SimpleDateFormat(
            context.getString(R.string.date_time_full),
            locale(context)
        ).format(Date(millis))
    }

    fun shortDateTime(context: Context, millis: Long): String {
        if (millis <= 0L) return ""
        return SimpleDateFormat(
            context.getString(R.string.date_time_short),
            locale(context)
        ).format(Date(millis))
    }

    /** "1:23" or "1:02:03" for the audio attachments. */
    fun duration(millis: Long): String {
        if (millis <= 0L) return "0:00"
        val totalSeconds = (millis / 1000).toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }

    fun fileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 kB"
        val units = arrayOf("B", "kB", "MB", "GB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }
        return if (index == 0) {
            String.format(Locale.getDefault(), "%.0f %s", value, units[index])
        } else {
            String.format(Locale.getDefault(), "%.1f %s", value, units[index])
        }
    }

    /** Start of the current day, used by the date filters. */
    fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun timeFormat(context: Context): SimpleDateFormat =
        SimpleDateFormat("HH:mm", locale(context))

    private fun locale(context: Context): Locale =
        context.resources.configuration.locales[0] ?: Locale.getDefault()

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
