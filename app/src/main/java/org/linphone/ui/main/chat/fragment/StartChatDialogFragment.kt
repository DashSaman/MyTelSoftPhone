package org.linphone.ui.main.chat.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.content.ContextCompat
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.databinding.DialogStartChatBinding
import org.linphone.utils.MyTelNumberNormalizer

class StartChatDialogFragment : AppCompatDialogFragment() {
    companion object {
        fun show(parent: ConversationsListFragment) {
            if (parent.childFragmentManager.findFragmentByTag("StartChatDialog") == null) {
                StartChatDialogFragment().show(parent.childFragmentManager, "StartChatDialog")
            }
        }
    }

    private var _binding: DialogStartChatBinding? = null
    private val binding get() = _binding!!
    private var submitting = false
    private val microphonePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) submit(chat = false) else Toast.makeText(context, R.string.call_audio_record_permission_not_granted_toast, Toast.LENGTH_LONG).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogStartChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val keys = mapOf(
            R.id.key_1 to "1", R.id.key_2 to "2", R.id.key_3 to "3",
            R.id.key_4 to "4", R.id.key_5 to "5", R.id.key_6 to "6",
            R.id.key_7 to "7", R.id.key_8 to "8", R.id.key_9 to "9",
            R.id.key_star to "*", R.id.key_0 to "0", R.id.key_hash to "#"
        )
        for ((id, digit) in keys) view.findViewById<View>(id).setOnClickListener { insertDigit(digit) }
        binding.key0.setOnLongClickListener { insertDigit("+"); true }
        binding.keyBackspace.setOnClickListener {
            val field = binding.numberDisplay
            val start = field.selectionStart.coerceAtLeast(0)
            val end = field.selectionEnd.coerceAtLeast(start)
            if (end > start) field.text?.delete(start, end) else if (start > 0) field.text?.delete(start - 1, start)
        }
        binding.keyBackspace.setOnLongClickListener { binding.numberDisplay.text?.clear(); true }
        binding.numberDisplay.showSoftInputOnFocus = false
        binding.numberDisplay.setText(savedInstanceState?.getString("number").orEmpty())
        binding.numberDisplay.setSelection(binding.numberDisplay.text?.length ?: 0)
        binding.chatButton.setOnClickListener { submit(chat = true) }
        binding.callButton.setOnClickListener { submit(chat = false) }
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
        outState.putString("number", _binding?.numberDisplay?.text?.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun insertDigit(digit: String) {
        val field = _binding?.numberDisplay ?: return
        val start = field.selectionStart.coerceAtLeast(0)
        val end = field.selectionEnd.coerceAtLeast(start)
        field.text?.replace(start, end, digit)
    }

    private fun submit(chat: Boolean) {
        if (submitting) return
        val destination = MyTelNumberNormalizer.normalize(binding.numberDisplay.text?.toString().orEmpty().trim())
        if (destination.isEmpty()) {
            binding.numberDisplay.error = getString(R.string.start_chat_invalid_number_toast)
            return
        }
        val parent = parentFragment as? ConversationsListFragment ?: return
        val toastContext = context ?: return
        if (!chat && ContextCompat.checkSelfPermission(toastContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (!coreContext.isReady()) return
        submitting = true
        coreContext.postOnCoreThread { core ->
            val address = core.interpretUrl(destination)
            val error = when {
                core.defaultAccount == null -> R.string.start_chat_no_account_toast
                address == null -> R.string.start_chat_invalid_number_toast
                else -> null
            }
            coreContext.postOnMainThread {
                submitting = false
                if (error != null) {
                    Toast.makeText(toastContext, error, Toast.LENGTH_LONG).show()
                } else if (isAdded && parent.isAdded) {
                    dismissAllowingStateLoss()
                    if (chat) {
                        parent.startOneToOneChatWith(address!!)
                    } else {
                        coreContext.postOnCoreThread {
                        coreContext.startAudioCall(address!!)
                    }
                    }
                }
            }
        }
    }
}
