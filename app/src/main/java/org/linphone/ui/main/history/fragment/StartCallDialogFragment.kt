/*
 * MyTel lightweight call dialer (owner requirement 2026-11-13).
 *
 * A DialogFragment (like StartChatDialogFragment) with a simple keypad:
 * no AddressSelectionViewModel, no MagicSearch, no bottom-sheet behavior —
 * opens instantly. Type/dial a number and press Call.
 */
package org.linphone.ui.main.history.fragment

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.content.ContextCompat
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.tools.Log
import org.linphone.utils.MyTelNumberNormalizer

class StartCallDialogFragment : AppCompatDialogFragment() {

    companion object {
        private const val TAG = "[Start Call Dialog]"

        fun show(parent: HistoryListFragment) {
            val dialog = StartCallDialogFragment()
            dialog.show(parent.childFragmentManager, "StartCallDialog")
        }
    }

    private var numberDisplay: EditText? = null
    private var suppressWatcher = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawable(
            ContextCompat.getDrawable(requireContext(), R.drawable.shape_dialog_background)
        )
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val dp = resources.displayMetrics.density

        // Build the dialer UI programmatically (fast, no data binding)
        val root = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
        }

        // Title
        root.addView(
            TextView(context).apply {
            text = "New call"
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, (12 * dp).toInt())
        }
        )

        // Number display
        numberDisplay = EditText(context).apply {
            hint = "Extension or number"
            textSize = 24f
            gravity = android.view.Gravity.CENTER
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            maxLines = 1
            setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
            background = ContextCompat.getDrawable(context, R.drawable.mytel_edit_text_background)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}

                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (!suppressWatcher) setSelection(s?.length ?: 0)
                }
            })
        }
        root.addView(numberDisplay)

        // Keypad grid
        val grid = GridLayout(context).apply {
            columnCount = 3
            rowCount = 4
            setPadding(0, (12 * dp).toInt(), 0, (8 * dp).toInt())
        }
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
        for (key in keys) {
            val btn = TextView(context).apply {
                text = key
                textSize = 24f
                gravity = android.view.Gravity.CENTER
                setPadding(0, (14 * dp).toInt(), 0, (14 * dp).toInt())
                background = ContextCompat.getDrawable(context, R.drawable.squircle_transparent_button_background)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = (52 * dp).toInt()
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
                }
                setOnClickListener { appendDigit(key) }
            }
            grid.addView(btn)
        }
        root.addView(grid)

        // Backspace
        val backspace = TextView(context).apply {
            text = "⌫ Delete"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(0, (12 * dp).toInt(), 0, (12 * dp).toInt())
            background = ContextCompat.getDrawable(context, R.drawable.squircle_transparent_button_background)
            setOnClickListener {
                val current = numberDisplay?.text?.toString().orEmpty()
                if (current.isNotEmpty()) {
                    suppressWatcher = true
                    numberDisplay?.setText(current.dropLast(1))
                    numberDisplay?.setSelection(numberDisplay?.text?.length ?: 0)
                    suppressWatcher = false
                }
            }
        }
        root.addView(backspace)

        // Call button
        val callButton = TextView(context).apply {
            text = "Call"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            setPadding((40 * dp).toInt(), (12 * dp).toInt(), (40 * dp).toInt(), (12 * dp).toInt())
            background = ContextCompat.getDrawable(context, R.drawable.mytel_primary_button_background)
            setOnClickListener { placeCall() }
        }
        val buttonRow = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
            setPadding(0, (12 * dp).toInt(), 0, 0)
        }
        buttonRow.addView(callButton)
        root.addView(buttonRow)

        return root
    }

    private fun appendDigit(digit: String) {
        suppressWatcher = true
        numberDisplay?.setText(numberDisplay?.text.toString() + digit)
        numberDisplay?.setSelection(numberDisplay?.text?.length ?: 0)
        suppressWatcher = false
    }

    private fun placeCall() {
        val raw = numberDisplay?.text?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) return

        if (!coreContext.isReady() || coreContext.core.defaultAccount == null) {
            Log.w("$TAG No account configured")
            dismissAllowingStateLoss()
            return
        }

        dismissAllowingStateLoss()

        coreContext.postOnCoreThread { core ->
            val address: Address? = if (raw.startsWith("sip:", ignoreCase = true)) {
                core.interpretUrl(raw)
            } else {
                core.interpretUrl(MyTelNumberNormalizer.normalize(raw))
            }
            if (address != null) {
                Log.i("$TAG Calling [${address.asStringUriOnly()}]")
                coreContext.startCall(address)
            } else {
                Log.w("$TAG Invalid destination [$raw]")
            }
        }
    }
}
