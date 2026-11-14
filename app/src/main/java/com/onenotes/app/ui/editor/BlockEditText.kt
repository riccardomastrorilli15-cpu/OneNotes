package com.onenotes.app.ui.editor

import android.content.Context
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.appcompat.widget.AppCompatEditText

/**
 * The edit text of one block of the editor.
 *
 * It only adds one thing to the standard widget: the soft keyboards delete text through
 * [InputConnection.deleteSurroundingText] instead of sending a key event, so without this
 * subclass there would be no way to know that the user pressed backspace at the very beginning
 * of a block - which is exactly the gesture that has to merge the block with the previous one.
 */
class BlockEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    /** Return true to consume the backspace. */
    var onBackspaceAtStart: (() -> Boolean)? = null

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(connection, true) {
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0 && afterLength == 0 &&
                    selectionStart == 0 && selectionEnd == 0 &&
                    onBackspaceAtStart?.invoke() == true
                ) {
                    return true
                }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }
        }
    }
}
