package com.onenotes.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import com.onenotes.app.BuildConfig
import java.io.File

// ------------------------------------------------------------------ ui utils

fun Context.toast(text: CharSequence, long: Boolean = false) {
    Toast.makeText(this, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

fun Context.toast(@StringRes resId: Int, long: Boolean = false) {
    Toast.makeText(this, resId, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

@ColorInt
fun Context.colorAttr(@AttrRes attr: Int): Int {
    val value = TypedValue()
    theme.resolveAttribute(attr, value, true)
    return value.data
}

fun View.gone() {
    visibility = View.GONE
}

fun View.visible() {
    visibility = View.VISIBLE
}

fun View.setVisible(value: Boolean) {
    visibility = if (value) View.VISIBLE else View.GONE
}

fun View.showKeyboard() {
    requestFocus()
    context.inputMethodManager()?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
}

fun View.hideKeyboard() {
    context.inputMethodManager()?.hideSoftInputFromWindow(windowToken, 0)
}

/** Reads an array of colours (used by the palettes of the app). */
fun Context.colorArray(arrayRes: Int): IntArray {
    val typed = resources.obtainTypedArray(arrayRes)
    val colors = IntArray(typed.length()) { typed.getColor(it, 0) }
    typed.recycle()
    return colors
}

fun Context.stringArray(arrayRes: Int): Array<String> =
    resources.getStringArray(arrayRes)

// ----------------------------------------------------------------- clipboard

fun Context.inputMethodManager(): InputMethodManager? =
    getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

fun Context.clipboardManager(): ClipboardManager? =
    getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

fun Context.copyToClipboard(label: String, text: String) {
    val manager = clipboardManager() ?: return
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun Context.clipboardText(): CharSequence? {
    val manager = clipboardManager() ?: return null
    val clip = manager.primaryClip ?: return null
    return if (clip.itemCount > 0) clip.getItemAt(0).coerceToText(this) else null
}

// ------------------------------------------------------------------- sharing

/** content:// uri of a file stored in the private storage of the app. */
fun Context.contentUriFor(file: File): Uri =
    FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)

fun Context.shareText(title: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
    }
    runCatching {
        startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
    }
}

fun Context.shareFile(file: File, mime: String, title: String) {
    val uri = runCatching { contentUriFor(file) }.getOrNull() ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
    }
}

fun Context.openFile(file: File, mime: String) {
    val uri = runCatching { contentUriFor(file) }.getOrNull() ?: return
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { startActivity(intent) }
        .onFailure { toast(getString(R.string.msg_error_generic)) }
}

fun Context.openLink(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
        .onFailure { toast(getString(R.string.msg_error_no_browser)) }
}

val appVersionName: String get() = BuildConfig.VERSION_NAME
