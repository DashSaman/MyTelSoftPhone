/*
 * Copyright (c) 2010-2026 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.assistant.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.linphone.R
import org.linphone.core.TransportType
import org.linphone.core.tools.Log
import org.linphone.databinding.AssistantAutoDetectFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.GenericFragment
import org.linphone.ui.assistant.viewmodel.AccountLoginViewModel
import org.linphone.utils.SipTransportScanner
import org.linphone.utils.SipTransportScanner.Companion.Result

@UiThread
class AutoDetectFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Auto Detect Fragment]"

        // Must match the transportType index encoding in AccountLoginViewModel.
        private fun transportIndex(type: TransportType): Int = when (type) {
            TransportType.Tcp -> 1
            TransportType.Udp -> 2
            else -> 0
        }

        private fun transportLabel(type: TransportType): String = when (type) {
            TransportType.Tcp -> "SIP / TCP"
            TransportType.Udp -> "SIP / UDP"
            else -> "SIP / TLS"
        }
    }

    private lateinit var binding: AssistantAutoDetectFragmentBinding

    private val viewModel: AccountLoginViewModel by navGraphViewModels(
        R.id.assistant_nav_graph
    )

    private var scanJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssistantAutoDetectFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        binding.setBackClickListener {
            scanJob?.cancel()
            findNavController().popBackStack()
        }

        binding.setScanClickListener {
            startScan()
        }
    }

    override fun onStop() {
        scanJob?.cancel()
        super.onStop()
    }

    private fun hostFromViewModel(): String {
        return viewModel.serverAddress.value.orEmpty().trim()
            .removePrefix("sip:")
            .substringBefore(";")
            .substringBefore(":")
    }

    private fun portsToProbe(customPort: Int?): LinkedHashSet<Int> {
        val ports = linkedSetOf<Int>()
        if (customPort != null && customPort in 1..65535) {
            ports.add(customPort)
        }
        ports.add(5061)
        ports.add(5060)
        return ports
    }

    private fun startScan() {
        val host = hostFromViewModel()
        if (host.isEmpty()) {
            Log.e("$TAG No server address to scan!")
            (requireActivity() as GenericActivity).showRedToast(
                getString(R.string.assistant_auto_detect_no_results),
                R.drawable.warning_circle
            )
            return
        }

        val customPort = viewModel.serverPort.value?.trim()?.toIntOrNull()
        val ports = portsToProbe(customPort)

        binding.scanningStatus.text = getString(R.string.assistant_auto_detect_scanning, host)
        binding.scanningStatus.visibility = View.VISIBLE
        binding.scanProgress.visibility = View.VISIBLE
        binding.resultsContainer.removeAllViews()
        binding.scanButton.isEnabled = false

        scanJob = lifecycleScope.launch {
            try {
                val results = SipTransportScanner().scan(host, ports)

                if (results.none { it.isUsable || it.tlsCertificateError }) {
                    binding.scanningStatus.text =
                        getString(R.string.assistant_auto_detect_no_results)
                    return@launch
                }

                binding.scanningStatus.visibility = View.GONE
                renderResults(host, results)
            } finally {
                // Restore the controls on EVERY exit path, including
                // cancellation (e.g. user left the screen mid-scan). Never
                // touch viewLifecycleOwner here — it throws once the view is
                // destroyed; touching the detached view itself is harmless,
                // and DESTROYED is the lowest state so an isAtLeast check
                // against it would always be true (and restore nothing).
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    try {
                        binding.scanProgress.visibility = View.GONE
                        binding.scanButton.isEnabled = true
                    } catch (ignored: IllegalStateException) {
                        // View already destroyed — nothing left to restore.
                    }
                }
            }
        }
    }

    private fun renderResults(host: String, results: List<Result>) {
        val inflater = layoutInflater
        for (result in results) {
            val row = inflater.inflate(
                R.layout.assistant_auto_detect_result_row,
                binding.resultsContainer,
                false
            )
            val label =
                row.findViewById<androidx.appcompat.widget.AppCompatTextView>(R.id.result_label)
            val detail =
                row.findViewById<androidx.appcompat.widget.AppCompatTextView>(R.id.result_detail)

            label.text = "${transportLabel(result.transport)} — $host:${result.port}"

            val context = requireContext()
            when {
                result.isUsable -> {
                    detail.text = "${result.sipReply} (${result.elapsedMs} ms)"
                    detail.setTextColor(
                        ContextCompat.getColor(context, R.color.green_success_500)
                    )
                    row.setOnClickListener { applyResult(result) }
                }
                result.tlsCertificateError -> {
                    detail.text = getString(R.string.assistant_auto_detect_certificate_error)
                    detail.setTextColor(
                        ContextCompat.getColor(context, R.color.orange_warning_600)
                    )
                }
                result.error?.contains("no UDP reply") == true -> {
                    detail.text = getString(R.string.assistant_auto_detect_udp_inconclusive)
                    detail.setTextColor(
                        ContextCompat.getColor(context, R.color.orange_warning_600)
                    )
                }
                result.error?.contains("no SIP reply") == true ||
                    result.error?.contains("timeout waiting for SIP reply") == true -> {
                    detail.text = getString(R.string.assistant_auto_detect_no_sip_reply)
                    detail.setTextColor(
                        ContextCompat.getColor(context, R.color.orange_warning_600)
                    )
                }
                else -> {
                    detail.text = getString(R.string.assistant_auto_detect_failed)
                    detail.setTextColor(
                        ContextCompat.getColor(context, R.color.red_danger_500)
                    )
                }
            }
            binding.resultsContainer.addView(row)
        }
    }

    private fun applyResult(result: Result) {
        Log.i("$TAG Applying detected settings: ${result.transport} port ${result.port}")
        viewModel.serverPort.value = result.port.toString()
        viewModel.setTransport(transportIndex(result.transport))
        (requireActivity() as GenericActivity).showGreenToast(
            getString(R.string.assistant_auto_detect_applied_toast),
            R.drawable.check
        )
        findNavController().popBackStack()
    }
}
