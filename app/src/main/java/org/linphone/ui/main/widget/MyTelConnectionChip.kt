/*
 * MyTel connection indicator (owner requirement 2026-11-11, revised
 * 2026-11-12: Online/Offline labels, reserved bottom-bar placement).
 *
 * The chip is self-sufficient: it registers its own core listener while
 * attached and computes connected = network reachable AND default account
 * registered, so every instance (one per list fragment's bottom bar)
 * updates live without any per-fragment wiring.
 *
 * Connected: steady green pill reading "Online". Disconnected: red pill
 * reading "Offline" that gently blinks (0.6 alpha floor keeps the text
 * legible; the blink respects the system reduced-motion setting).
 */
package org.linphone.ui.main.widget

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.RegistrationState
import org.linphone.core.tools.Log

class MyTelConnectionChip
    @JvmOverloads
    constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    companion object {
        private const val TAG = "[MyTel Connection Chip]"
        private val GREEN = Color.parseColor("#22C55E")
        private val RED = Color.parseColor("#EF4444")
    }

    private val dot = View(context)

    private val label = AppCompatTextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 12f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    private var blinkAnimator: ObjectAnimator? = null
    private var registered = false
    private var attached = false

    private val coreListener = object : CoreListenerStub() {
        override fun onNetworkReachable(core: Core, reachable: Boolean) {
            post { refreshState() }
        }

        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: org.linphone.core.Account,
            state: RegistrationState?,
            message: String
        ) {
            if (account == core.defaultAccount) {
                post { refreshState() }
            }
        }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val dp = resources.displayMetrics.density
        val dotSize = (6 * dp).toInt()
        dot.layoutParams = LayoutParams(dotSize, dotSize).apply {
            marginEnd = (6 * dp).toInt()
            gravity = Gravity.CENTER_VERTICAL
        }
        val padH = (10 * dp).toInt()
        val padV = (4 * dp).toInt()
        setPadding(padH, padV, padH, padV)
        elevation = 4f
        addView(dot)
        addView(label)
        minimumHeight = (24 * dp).toInt()
        refreshState(initial = true)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        if (!registered) {
            coreContext.core.addListener(coreListener)
            registered = true
            Log.d("$TAG Core listener registered")
        }
        refreshState()
        // Events that happened before attach (e.g. registration reaching Ok
        // while the UI was still opening) are missed by the listener, so
        // re-check the state shortly after attach
        postDelayed({ if (attached) refreshState() }, 1000)
        postDelayed({ if (attached) refreshState() }, 5000)
    }

    override fun onDetachedFromWindow() {
        if (registered) {
            coreContext.core.removeListener(coreListener)
            registered = false
            Log.d("$TAG Core listener removed")
        }
        blinkAnimator?.cancel()
        blinkAnimator = null
        attached = false
        super.onDetachedFromWindow()
    }

    /** connected = network reachable AND default account registered. */
    private fun refreshState(initial: Boolean = false) {
        // Called before the core exists (XML preview / early inflate)
        if (!coreContext.isReady()) {
            applyState(connected = false)
            return
        }
        val core = coreContext.core
        val connected = core.isNetworkReachable &&
            core.defaultAccount?.state == RegistrationState.Ok
        if (initial || attached) {
            applyState(connected)
        }
    }

    private fun applyState(connected: Boolean) {
        val textRes = if (connected) {
            R.string.mytel_connection_connected
        } else {
            R.string.mytel_connection_disconnected
        }
        label.text = context.getString(textRes)
        contentDescription = context.getString(textRes)

        val color = if (connected) GREEN else RED
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f * resources.displayMetrics.density
            setColor(color)
        }
        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }

        blinkAnimator?.cancel()
        blinkAnimator = null
        alpha = 1f
        if (!connected && ValueAnimator.areAnimatorsEnabled()) {
            blinkAnimator = ObjectAnimator.ofFloat(this, View.ALPHA, 1f, 0.6f).apply {
                duration = 600
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }
    }
}
