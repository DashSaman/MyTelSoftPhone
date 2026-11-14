package org.linphone.ui.main.history.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.utils.MyTelNumberNormalizer

/** A small, theme-aware dialer that does not start a contact search just to dial a number. */
class StartCallDialogFragment : AppCompatDialogFragment() {
    companion object {
        fun show(parent: HistoryListFragment) {
            if (parent.childFragmentManager.findFragmentByTag("StartCallDialog") == null) {
                StartCallDialogFragment().show(parent.childFragmentManager, "StartCallDialog")
            }
        }
    }

    private var numberDisplay: EditText? = null
    private var submitting = false
    private var pendingVideo = false
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val microphone = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camera = !pendingVideo || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (microphone && camera) {
            placeCall(pendingVideo)
        } else {
            Toast.makeText(context, if (!microphone) R.string.call_audio_record_permission_not_granted_toast else R.string.call_camera_permission_not_granted_toast, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val density = resources.displayMetrics.density

        fun dp(value: Int) = (value * density).toInt()

        fun themeColor(attr: Int): Int = TypedValue().also { context.theme.resolveAttribute(attr, it, true) }.data

        fun label(textRes: Int, size: Float) = TextView(context).apply {
            setText(textRes)
            textSize = size
            setTextColor(themeColor(R.attr.color_text))
            gravity = Gravity.CENTER
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
            background = ContextCompat.getDrawable(context, R.drawable.shape_dialog_background)
        }
        root.addView(
            label(R.string.mytel_new_call, 21f).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        )
        root.addView(label(R.string.mytel_dialer_caption, 13f).apply { setPadding(0, dp(6), 0, dp(14)) })
        numberDisplay = AppCompatEditText(context).apply {
            id = View.generateViewId()
            setHint(R.string.start_chat_hint)
            setTextColor(themeColor(R.attr.color_text))
            setHintTextColor(themeColor(R.attr.color_hint_text))
            textSize = 25f
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
            showSoftInputOnFocus = false
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = ContextCompat.getDrawable(context, R.drawable.mytel_edit_text_background)
            setText(savedInstanceState?.getString("number").orEmpty())
            setSelection(text?.length ?: 0)
        }
        root.addView(numberDisplay, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        val grid = GridLayout(context).apply {
            columnCount = 3
            rowCount = 4
            setPadding(0, dp(10), 0, 0)
        }
        for (digit in listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")) {
            grid.addView(
                AppCompatButton(context).apply {
                text = digit
                textSize = 23f
                setTextColor(themeColor(R.attr.color_main2_800))
                isAllCaps = false
                minWidth = 0
                minimumWidth = 0
                background = ContextCompat.getDrawable(context, R.drawable.squircle_transparent_button_background)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(55)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
                setOnClickListener { insertDigit(digit) }
                if (digit == "0") setOnLongClickListener { insertDigit("+"); true }
            }
            )
        }
        root.addView(grid)
        root.addView(
            AppCompatButton(context).apply {
            setText(R.string.start_chat_backspace)
            contentDescription = getString(R.string.content_description_erase_last_input)
            isAllCaps = false
            setTextColor(themeColor(R.attr.color_main2_600))
            background = ContextCompat.getDrawable(context, R.drawable.squircle_transparent_button_background)
            setOnClickListener { deleteDigit() }
            setOnLongClickListener { numberDisplay?.text?.clear(); true }
        },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
        )
        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(
            AppCompatButton(context).apply {
            setText(R.string.mytel_video_call)
            isAllCaps = false
            setTextColor(themeColor(R.attr.color_main1_500))
            background = ContextCompat.getDrawable(context, R.drawable.squircle_transparent_button_background)
            setOnClickListener { requestCall(video = true) }
        },
            LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(8) }
        )
        actions.addView(
            AppCompatButton(context).apply {
            setText(R.string.start_chat_call_action)
            isAllCaps = false
            setTextColor(themeColor(R.attr.color_on_main))
            background = ContextCompat.getDrawable(context, R.drawable.mytel_primary_button_background)
            setOnClickListener { requestCall(video = false) }
        },
            LinearLayout.LayoutParams(0, dp(52), 1f)
        )
        root.addView(actions)
        return ScrollView(context).apply { isFillViewport = false; addView(root) }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(minOf((resources.displayMetrics.widthPixels * 0.94f).toInt(), (440 * resources.displayMetrics.density).toInt()), ViewGroup.LayoutParams.WRAP_CONTENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("number", numberDisplay?.text?.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        numberDisplay = null
        super.onDestroyView()
    }

    private fun insertDigit(digit: String) {
        val field = numberDisplay ?: return
        val start = field.selectionStart.coerceAtLeast(0)
        val end = field.selectionEnd.coerceAtLeast(start)
        field.text?.replace(start, end, digit)
    }

    private fun deleteDigit() {
        val field = numberDisplay ?: return
        val start = field.selectionStart.coerceAtLeast(0)
        val end = field.selectionEnd.coerceAtLeast(start)
        if (end > start) field.text?.delete(start, end) else if (start > 0) field.text?.delete(start - 1, start)
    }

    private fun requestCall(video: Boolean) {
        if (submitting) return
        if (numberDisplay?.text.isNullOrBlank()) {
            numberDisplay?.error = getString(R.string.start_chat_invalid_number_toast)
            return
        }
        pendingVideo = video
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (video) permissions.add(Manifest.permission.CAMERA)
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) placeCall(video) else permissionRequest.launch(missing.toTypedArray())
    }

    private fun placeCall(video: Boolean) {
        if (submitting) return
        val destination = MyTelNumberNormalizer.normalize(numberDisplay?.text?.toString().orEmpty().trim())
        val toastContext = context ?: return
        if (!coreContext.isReady()) return
        submitting = true
        coreContext.postOnCoreThread { core ->
            val address = core.interpretUrl(destination)
            val error = when {
                core.defaultAccount == null -> R.string.start_chat_no_account_toast
                address == null -> R.string.start_chat_invalid_number_toast
                !core.isNetworkReachable -> R.string.mytel_connection_disconnected
                else -> null
            }
            coreContext.postOnMainThread {
                submitting = false
                if (error != null) {
                    Toast.makeText(toastContext, error, Toast.LENGTH_LONG).show()
                } else if (isAdded) {
                    dismissAllowingStateLoss()
                    coreContext.postOnCoreThread {
                        if (video) coreContext.startVideoCall(address!!) else coreContext.startAudioCall(address!!)
                    }
                }
            }
        }
    }
}
