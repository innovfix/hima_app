package com.gmwapp.hima

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

class NoPasteEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    override fun onTextContextMenuItem(id: Int): Boolean {
        // Block paste from all sources (context menu, keyboard suggestions, etc.)
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
            return false
        }
        return super.onTextContextMenuItem(id)
    }

    init {
        // Disable long-press and selection
        isLongClickable = false
        setTextIsSelectable(false)
    }
}
