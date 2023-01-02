package io.github.nightdavisao.discordgalaxypresence

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MFACodeDialogFragment : DialogFragment() {
    private var callback: (suspend (String) -> Unit)? = null

    fun setOkCallback(okCallback: suspend (String) -> Unit) {
        callback = okCallback
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            // Use the Builder class for convenient dialog construction
            val builder = AlertDialog.Builder(it)
            val editText = EditText(this.context)
            editText.inputType = InputType.TYPE_CLASS_NUMBER
            builder.setMessage("MFA code")
                .setView(editText)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    lifecycleScope.launch {
                        callback?.invoke(editText.text.toString())
                    }
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    // User cancelled the dialog
                }

            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}
