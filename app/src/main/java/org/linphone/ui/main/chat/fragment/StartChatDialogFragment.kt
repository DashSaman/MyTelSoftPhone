/*
 * Start chat by extension/number (owner requirement 2026-11-12, leader
 * design verdict same day).
 *
 * A keypad-first dialog: no contact list, no address picker. The user
 * types or dials an extension (or a phone number, or an explicit SIP URI),
 * then either starts a chat (primary action, reusing the existing
 * one-to-one basic-chat helper in ConversationsListViewModel) or places
 * a call (secondary action).
 *
 * Address construction is SDK-aware (leader correction): explicit "sip:"
 * URIs go straight to Core.interpretUrl; telephone input runs through the
 * existing MyTel Iranian-number normalizer first (extensions and leading
 * zeros preserved) and is then interpreted by the SDK against the default
 * account, keeping the account's domain/port/transport semantics.
 */
package org.linphone.ui.main.chat.fragment

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.tools.Log
import org.linphone.databinding.DialogStartChatBinding
import org.linphone.utils.MyTelNumberNormalizer

class StartChatDialogFragment : AppCompatDialogFragment() {

    companion object {
        private const val TAG = "[Start Chat Dialog]"

        fun show(parent: ConversationsListFragment) {
            val dialog = StartChatDialogFragment()
            dialog.show(parent.childFragmentManager, "StartChatDialog")
        }
    }

    private var _binding: DialogStartChatBinding? = null
    private val binding get() = _binding!!

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
        _binding = DataBindingUtil.inflate(inflater, R.layout.dialog_start_chat, container, false)
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
        for ((id, digit) in keys) {
            view.findViewById<View>(id).setOnClickListener {
                appendDigit(digit)
            }
        }
        binding.keyBackspace.setOnClickListener {
            val current = binding.numberDisplay.text?.toString().orEmpty()
            if (current.isNotEmpty()) {
                suppressWatcher = true
                binding.numberDisplay.setText(current.dropLast(1))
                binding.numberDisplay.setSelection(binding.numberDisplay.text?.length ?: 0)
                suppressWatcher = false
            }
        }
        binding.numberDisplay.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (!suppressWatcher) {
                    binding.numberDisplay.setSelection(s?.length ?: 0)
                }
            }
        })
        binding.numberDisplay.requestFocus()

        binding.chatButton.setOnClickListener { submit(chat = true) }
        binding.callButton.setOnClickListener { submit(chat = false) }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun appendDigit(digit: String) {
        suppressWatcher = true
        binding.numberDisplay.setText(binding.numberDisplay.text.toString() + digit)
        binding.numberDisplay.setSelection(binding.numberDisplay.text?.length ?: 0)
        suppressWatcher = false
    }

    private fun submit(chat: Boolean) {
        val raw = binding.numberDisplay.text?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) {
            showRedToast(R.string.start_chat_invalid_number_toast)
            return
        }
        val parent = parentFragment as? ConversationsListFragment
        if (parent == null) {
            Log.e("$TAG No parent ConversationsListFragment, cannot start")
            dismissAllowingStateLoss()
            return
        }
        if (!coreContext.isReady() || coreContext.core.defaultAccount == null) {
            showRedToast(R.string.start_chat_no_account_toast)
            return
        }
        val core = coreContext.core
        val address: Address? = if (raw.startsWith("sip:", ignoreCase = true)) {
            // Explicit SIP URI: interpret as-is through the SDK
            core.interpretUrl(raw)
        } else {
            // Telephone/extension input: normalize Iranian formats first
            // (extensions and leading zeros preserved), then let the SDK
            // interpret against the default account
            val normalized = MyTelNumberNormalizer.normalize(raw)
            core.interpretUrl(normalized)
        }
        if (address == null) {
            Log.w("$TAG Could not interpret [$raw] as a valid destination")
            showRedToast(R.string.start_chat_invalid_number_toast)
            return
        }

        Log.i(
            "$TAG ${if (chat) "Chat" else "Call"} requested with [$raw] -> [${address.asStringUriOnly()}]"
        )
        dismissAllowingStateLoss()
        if (chat) {
            // Reuses the existing one-to-one basic-chat helper; the created
            // room event is already observed by the parent and opens the
            // conversation. An existing room for the same identities is
            // reused by the helper itself.
            parent.startOneToOneChatWith(address)
        } else {
            coreContext.postOnCoreThread {
                coreContext.startCall(address)
            }
        }
    }

    private fun showRedToast(stringRes: Int) {
        val context = context ?: return
        android.widget.Toast.makeText(
            context,
            context.getString(stringRes),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}
