/*
 * Owner requirement 2026-11-11: an unmistakable connection indicator.
 * Connected: green pill with the Persian word "وصل". Disconnected: red pill
 * with "قطع" that gently blinks so the state is recognizable at a glance
 * from across the room.
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
import org.linphone.R

class MyTelConnectionChip
    @JvmOverloads
    constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    companion object {
        private val GREEN = Color.parseColor("#22C55E")
        private val RED = Color.parseColor("#EF4444")
        private const val CORNER_RADIUS_DP = 20f
    }

    private val dot = View(context)

    private val label = AppCompatTextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    private var blinkAnimator: ObjectAnimator? = null
    private var connected = false

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val dp = resources.displayMetrics.density
        val dotSize = (8 * dp).toInt()
        dot.layoutParams = LayoutParams(dotSize, dotSize).apply {
            marginEnd = (8 * dp).toInt()
            gravity = Gravity.CENTER_VERTICAL
        }
        val pad = (12 * dp).toInt()
        setPadding(pad, (6 * dp).toInt(), pad, (6 * dp).toInt())
        elevation = 8f
        addView(dot)
        addView(label)
        setState(connected = false)
    }

    fun setState(connected: Boolean) {
        if (this.connected == connected && label.text.isNotEmpty()) return
        this.connected = connected
        val color = if (connected) GREEN else RED
        val textRes = if (connected) {
            R.string.mytel_connection_connected
        } else {
            R.string.mytel_connection_disconnected
        }
        label.text = context.getString(textRes)
        contentDescription = context.getString(textRes)

        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = CORNER_RADIUS_DP * resources.displayMetrics.density
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
            // Blinking makes the failure state obvious at a glance; the
            // floor keeps the text legible and system reduced-motion
            // settings disable the blink entirely (leader correction)
            blinkAnimator = ObjectAnimator.ofFloat(this, View.ALPHA, 1f, 0.6f).apply {
                duration = 600
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }
    }

    override fun onDetachedFromWindow() {
        blinkAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
