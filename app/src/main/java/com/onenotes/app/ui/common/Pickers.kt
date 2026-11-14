package com.onenotes.app.ui.common

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.onenotes.app.R
import java.util.Calendar

/**
 * Small dialogs shared by the editor and by the notes list.
 *
 * The pickers are the ones of the platform: on a Galaxy device they are rendered by One UI
 * itself, so they look native without shipping an extra dependency.
 */
object Pickers {

    /** Asks for a date and then for a time, and returns the result as epoch millis. */
    fun dateTime(context: Context, initial: Long?, onPicked: (Long) -> Unit) {
        val calendar = Calendar.getInstance()
        if (initial != null && initial > System.currentTimeMillis()) {
            calendar.timeInMillis = initial
        } else {
            calendar.add(Calendar.HOUR_OF_DAY, 1)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
        }

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        DatePickerDialog(context, { _, pickedYear, pickedMonth, pickedDay ->
            TimePickerDialog(context, { _, pickedHour, pickedMinute ->
                val result = Calendar.getInstance().apply {
                    set(pickedYear, pickedMonth, pickedDay, pickedHour, pickedMinute, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onPicked(result.timeInMillis)
            }, hour, minute, android.text.format.DateFormat.is24HourFormat(context)).show()
        }, year, month, day).show()
    }

    /** Asks for the url of a link. */
    fun link(context: Context, initial: String, onDone: (String) -> Unit) {
        val input = EditText(context).apply {
            setText(initial)
            setHint(R.string.editor_link_hint)
            setSingleLine()
            val pad = (24 * context.resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 3, pad, pad / 3)
            setTextColor(ContextCompat.getColor(context, R.color.onenotes_text_primary))
            setHintTextColor(ContextCompat.getColor(context, R.color.onenotes_text_hint))
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(input)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.editor_link_dialog_title)
            .setView(container)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save) { _, _ ->
                var url = input.text?.toString()?.trim().orEmpty()
                if (url.isNotEmpty() && !url.contains("://")) url = "https://$url"
                onDone(url)
            }
            .show()
    }
}

/**
 * Fills a horizontal row with the round colour swatches used by the editor and by the drawing
 * canvas (the same shape Samsung Notes uses).
 */
fun LinearLayout.showPalette(
    colors: IntArray,
    selected: Int,
    onPick: (Int) -> Unit
) {
    removeAllViews()
    val size = (32 * resources.displayMetrics.density).toInt()
    val margin = (6 * resources.displayMetrics.density).toInt()
    colors.forEach { color ->
        val swatch = android.view.View(context).apply {
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                if (color == selected) {
                    setStroke((2 * resources.displayMetrics.density).toInt(),
                        ContextCompat.getColor(context, R.color.onenotes_primary))
                } else if (color == 0) {
                    setStroke(1, ContextCompat.getColor(context, R.color.onenotes_divider))
                }
            }
            background = shape
            contentDescription = context.getString(R.string.draw_color)
            setOnClickListener { onPick(color) }
        }
        val params = LinearLayout.LayoutParams(size, size).apply {
            gravity = Gravity.CENTER_VERTICAL
            marginStart = margin
            marginEnd = margin
        }
        addView(swatch, params)
    }
}
