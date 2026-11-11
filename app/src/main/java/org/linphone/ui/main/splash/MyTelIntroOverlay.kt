/*
 * MyTel branded intro overlay (owner requirement 2026-11-11, design verdict
 * from the GPT-6.1-sol leader 2026-11-11, corrections same day).
 *
 * Shown once per process over MainActivity before any first-run destination
 * — no dedicated splash Activity (leader decision #2). The emblem uses the
 * actual badge image (leader correction: brand accuracy over vector
 * substitution), fully visible from the first frame so it matches the system
 * splash screen. Single coordinated ~1.2 s sequence:
 *  - 0-500 ms   a luminous highlight travels once around the ring
 *  - 150-650 ms the badge settles 0.96 -> 1.02 -> 1.0 with a <=12% halo
 *  - 300-800 ms "MytellPhone" wordmark (single TextView, soft white) fades up
 *  - 650-1000 ms the 64dp x 2dp orange underline reveals
 *  - 1000-1200 ms crossfade into the app beneath
 *
 * Tap anywhere skips immediately; with system animators disabled the static
 * brand is shown briefly instead of the decorative wait (leader decision #3).
 */
package org.linphone.ui.main.splash

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import org.linphone.R
import org.linphone.core.tools.Log

class MyTelIntroOverlay(context: Context) : FrameLayout(context) {

    companion object {
        private const val TAG = "[MyTel Intro Overlay]"

        private const val NAVY = 0xFF001622.toInt()
        private const val SOFT_WHITE = 0xFFE8F1FC.toInt()
        private const val ORANGE = 0xFFFFAB2E.toInt()

        private const val TOTAL_MS = 1200L
        private const val T_HIGHLIGHT_START = 0f
        private const val T_HIGHLIGHT_END = 500f
        private const val T_SETTLE_START = 150f
        private const val T_SETTLE_END = 650f
        private const val T_WORDMARK_START = 300f
        private const val T_WORDMARK_END = 800f
        private const val T_UNDERLINE_START = 650f
        private const val T_UNDERLINE_END = 1000f
        private const val T_EXIT_START = 1000f
        private const val T_EXIT_END = 1200f

        private const val EXIT_FADE_MS = 200L
        private const val EMBLEM_SIZE_DP = 128
    }

    private val decelerate = DecelerateInterpolator()

    private val emblemContainer: FrameLayout
    private val halo: HaloView
    private val badge: AppCompatImageView
    private val sweep: RingSweepView
    private val wordmark: AppCompatTextView
    private val underline: View

    private var masterAnimator: ValueAnimator? = null
    private var finished = false

    /** Invoked once the overlay can be removed from its parent. */
    var onFinished: (() -> Unit)? = null

    init {
        setBackgroundColor(NAVY)
        isClickable = true
        isFocusable = true

        halo = HaloView(context)
        badge = AppCompatImageView(context).apply {
            setImageResource(R.drawable.mytel_mark)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }
        sweep = RingSweepView(context)
        emblemContainer = FrameLayout(context).apply {
            addView(halo, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(badge, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(sweep, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        addView(emblemContainer)

        wordmark = AppCompatTextView(context).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textSize = 26f
            letterSpacing = 0.02f
            setTextColor(SOFT_WHITE)
            text = "MytellPhone"
            alpha = 0f
        }
        addView(wordmark)

        underline = View(context).apply {
            setBackgroundColor(ORANGE)
            scaleX = 0f
            pivotX = 0f
        }
        addView(underline)

        setOnClickListener { skip() }

        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.mytel_intro_accessibility_description)
        ViewCompat.replaceAccessibilityAction(
            this,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
            context.getString(R.string.mytel_intro_skip_action_label),
            null
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (measuredWidth == 0 || measuredHeight == 0) return
        val dp = resources.displayMetrics.density

        val emblemSize = (EMBLEM_SIZE_DP * dp).toInt()
        emblemContainer.measure(
            MeasureSpec.makeMeasureSpec(emblemSize, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(emblemSize, MeasureSpec.EXACTLY)
        )
        wordmark.measure(
            MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        underline.measure(
            MeasureSpec.makeMeasureSpec((64 * dp).toInt(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(maxOf(2, (2 * dp).toInt()), MeasureSpec.EXACTLY)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) return
        val dp = resources.displayMetrics.density

        // Emblem stays at the window centre so the first frame matches the
        // system splash screen position (leader decision #1)
        val emblemSize = (EMBLEM_SIZE_DP * dp).toInt()
        val cx = w / 2
        val cy = h / 2
        emblemContainer.layout(
            cx - emblemSize / 2,
            cy - emblemSize / 2,
            cx + emblemSize / 2,
            cy + emblemSize / 2
        )

        val wordmarkTop = cy + emblemSize / 2 + (20 * dp).toInt()
        wordmark.layout(
            cx - wordmark.measuredWidth / 2,
            wordmarkTop,
            cx + wordmark.measuredWidth / 2,
            wordmarkTop + wordmark.measuredHeight
        )

        val underlineTop = wordmarkTop + wordmark.measuredHeight + (10 * dp).toInt()
        underline.layout(
            cx - underline.measuredWidth / 2,
            underlineTop,
            cx + underline.measuredWidth / 2,
            underlineTop + underline.measuredHeight
        )
    }

    fun start() {
        // Adaptive startup budget (leader correction 2026-11-11): the launch
        // target covers startup through usable content, so decoration is
        // shortened when the app itself already consumed the budget
        val processElapsed = android.os.SystemClock.elapsedRealtime() -
            android.os.Process.getStartElapsedRealtime()
        val budgetScale = when {
            processElapsed > 2500L -> 0f
            processElapsed > 1500L -> 0.5f
            else -> 1f
        }
        val animatorsEnabled = ValueAnimator.areAnimatorsEnabled()
        if (!animatorsEnabled || budgetScale == 0f) {
            val budgetExhausted = budgetScale == 0f
            Log.i(
                "$TAG Static brand only (animators enabled=$animatorsEnabled, " +
                    "process elapsed=${processElapsed}ms, budget exhausted=$budgetExhausted)"
            )
            wordmark.alpha = 1f
            underline.scaleX = 1f
            if (budgetExhausted) {
                // Startup budget already consumed: no decorative hold at all
                finishWithFade()
            } else {
                postDelayed({ finishWithFade() }, 400)
            }
            return
        }

        Log.i("$TAG Playing intro with budget scale $budgetScale (elapsed=${processElapsed}ms)")
        val dp = resources.displayMetrics.density
        // Logical time always spans the FULL timeline (every phase, including
        // underline and exit, is reached); only the playback duration scales
        masterAnimator = ValueAnimator.ofFloat(0f, TOTAL_MS.toFloat()).apply {
            duration = (TOTAL_MS * budgetScale).toLong()
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                sweep.progress = phase(t, T_HIGHLIGHT_START, T_HIGHLIGHT_END)
                sweep.invalidate()

                val settleProgress = phase(t, T_SETTLE_START, T_SETTLE_END)
                halo.alphaLevel = settleProgress
                halo.invalidate()
                emblemContainer.scaleX = settleScale(settleProgress)
                emblemContainer.scaleY = settleScale(settleProgress)

                val wordmarkProgress = phase(t, T_WORDMARK_START, T_WORDMARK_END)
                val wordmarkEased = decelerate.getInterpolation(wordmarkProgress)
                wordmark.alpha = wordmarkEased
                wordmark.translationY = (1f - wordmarkEased) * 8 * dp

                underline.scaleX = decelerate.getInterpolation(
                    phase(t, T_UNDERLINE_START, T_UNDERLINE_END)
                )

                alpha = 1f - phase(t, T_EXIT_START, T_EXIT_END)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finish()
                }
            })
            start()
        }
    }

    /** Settle motion 0.96 -> 1.02 -> 1.0 (leader decision #1). */
    private fun settleScale(progress: Float): Float {
        if (progress <= 0f) return 0.96f
        return if (progress < 0.6f) {
            0.96f + 0.06f * (progress / 0.6f)
        } else {
            1.02f - 0.02f * ((progress - 0.6f) / 0.4f)
        }
    }

    /** Immediate skip: crossfade out quickly, then remove. */
    fun skip() {
        Log.i("$TAG Skipping intro animation")
        masterAnimator?.cancel()
        masterAnimator = null
        finishWithFade()
    }

    private fun finishWithFade() {
        animate()
            .alpha(0f)
            .setDuration(EXIT_FADE_MS)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finish()
                }
            })
    }

    private fun finish() {
        if (finished) return
        finished = true
        Log.i("$TAG Intro finished")
        onFinished?.invoke()
    }

    override fun onDetachedFromWindow() {
        masterAnimator?.cancel()
        animate().cancel()
        super.onDetachedFromWindow()
    }

    private fun phase(t: Float, start: Float, end: Float): Float {
        if (t <= start) return 0f
        if (t >= end) return 1f
        return (t - start) / (end - start)
    }

    /** Soft radial glow behind the badge, never above 12% opacity. */
    private class HaloView(context: Context) : View(context) {

        var alphaLevel = 0f

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (alphaLevel <= 0f || width == 0 || height == 0) return
            val haloAlpha = 0.12f * alphaLevel
            val cx = width / 2f
            val cy = height / 2f
            paint.shader = RadialGradient(
                cx, cy, width * 0.9f,
                intArrayOf(
                    (0xFFFFAB2E.toInt() and 0xFFFFFF).toInt() or ((haloAlpha * 255).toInt() shl 24),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, width * 0.9f, paint)
        }
    }

    /**
     * The luminous highlight travelling once around the badge ring. Drawn on
     * top of the badge image, along its ring radius.
     */
    private class RingSweepView(context: Context) : View(context) {

        companion object {
            private const val SOFT_WHITE = 0xFFE8F1FC.toInt()
            // Ring geometry matched to the badge artwork proportions
            private const val RING_RADIUS_RATIO = 0.46f
            private const val RING_STROKE_RATIO = 0.085f
            private const val HIGHLIGHT_SWEEP = 26f
        }

        var progress = 0f

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val oval = android.graphics.RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (progress <= 0f || progress >= 1f || width == 0 || height == 0) return
            val size = minOf(width, height).toFloat()
            val stroke = size * RING_STROKE_RATIO
            val radius = size * RING_RADIUS_RATIO - stroke / 2f
            val cx = width / 2f
            val cy = height / 2f
            oval[cx - radius, cy - radius, cx + radius] = cy + radius
            paint.strokeWidth = stroke
            paint.color = SOFT_WHITE
            paint.alpha = 220
            canvas.drawArc(
                oval,
                -90f + 360f * progress,
                HIGHLIGHT_SWEEP,
                false,
                paint
            )
        }
    }
}
