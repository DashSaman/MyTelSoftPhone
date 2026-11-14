package org.linphone.ui.main.widget

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Account
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.GlobalState
import org.linphone.core.RegistrationState
import org.linphone.utils.MyTelConnectionStatus

/** Core access and listener ownership stay on the SDK thread; only rendering uses the UI thread. */
class MyTelConnectionChip
    @JvmOverloads
    constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    private val dot = View(context)
    private val label = AppCompatTextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 12f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    @Volatile private var attached = false
    private var listenerRegistered = false // Accessed only on the core thread.
    private var renderedStatus: MyTelConnectionStatus? = null
    private val refresh = object : Runnable {
        override fun run() {
            if (!attached) return
            if (coreContext.isReady()) {
                coreContext.postOnCoreThread { core ->
                    if (!attached) return@postOnCoreThread
                    if (!listenerRegistered) {
                        core.addListener(coreListener)
                        listenerRegistered = true
                    }
                    publish(core)
                }
            }
            // Also covers delayed core startup and account changes before listener registration.
            postDelayed(this, 3000)
        }
    }
    private val coreListener = object : CoreListenerStub() {
        override fun onNetworkReachable(core: Core, reachable: Boolean) = publish(core)

        override fun onDefaultAccountChanged(core: Core, account: Account?) = publish(core)

        override fun onAccountRemoved(core: Core, account: Account) = publish(core)

        override fun onGlobalStateChanged(core: Core, state: GlobalState?, message: String) = publish(core)

        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState?,
            message: String
        ) = publish(core)
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val dp = resources.displayMetrics.density
        dot.layoutParams = LayoutParams((6 * dp).toInt(), (6 * dp).toInt()).apply {
            marginEnd = (6 * dp).toInt()
        }
        setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
        minimumHeight = (24 * dp).toInt()
        addView(dot)
        addView(label)
        applyState(MyTelConnectionStatus.Connecting)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        removeCallbacks(refresh)
        refresh.run()
    }

    override fun onDetachedFromWindow() {
        attached = false
        removeCallbacks(refresh)
        if (coreContext.isReady()) {
            coreContext.postOnCoreThread { core ->
                if (listenerRegistered) {
                    core.removeListener(coreListener)
                    listenerRegistered = false
                }
            }
        }
        super.onDetachedFromWindow()
    }

    private fun publish(core: Core) {
        val status = MyTelConnectionStatus.resolve(core.isNetworkReachable, core.defaultAccount?.state)
        post { if (attached) applyState(status) }
    }

    private fun applyState(status: MyTelConnectionStatus) {
        if (renderedStatus == status) return
        renderedStatus = status
        val (textRes, color) = when (status) {
            MyTelConnectionStatus.Online -> R.string.mytel_connection_connected to "#15803D"
            MyTelConnectionStatus.Connecting -> R.string.mytel_connection_connecting to "#1D4ED8"
            MyTelConnectionStatus.Offline -> R.string.mytel_connection_disconnected to "#B91C1C"
            MyTelConnectionStatus.NoAccount -> R.string.mytel_connection_no_account to "#475569"
        }
        label.setText(textRes)
        contentDescription = context.getString(textRes)
        background = GradientDrawable().apply {
            cornerRadius = 12f * resources.displayMetrics.density
            setColor(Color.parseColor(color))
        }
        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
    }
}
