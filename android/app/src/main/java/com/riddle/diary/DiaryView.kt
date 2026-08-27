package com.riddle.diary

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * 日记本页面。对应原版 main.rs + ink.rs + script.rs：
 *
 *   笔尖落下 ── 书写 ── 停笔 2.8s ── 「吸墨」（笔画渐隐）
 *     ── 整页转 PNG 发给视觉模型 ── Tom 的回答以簪花小楷
 *     手写体逐字浮现 ── 触碰任意处翻过这一页。
 *
 * 只响应触控笔（识别到笔后拒绝手指，防手掌误触）；笔杆按键或
 * TOOL_TYPE_ERASER 为橡皮。
 */
class DiaryView(context: Context) : View(context) {

    private enum class State { WRITING, DRINKING, THINKING, WRITING_REPLY }

    private class Stroke {
        val path = Path()
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
    }

    /** 已排好版、带浮现时刻的回复字符。 */
    private class Glyph(
        val ch: Char,
        val x: Float,
        val baselineY: Float,
        val rotationDeg: Float,
        val appearAtMs: Long,
    )

    private var state = State.WRITING

    // ---- 墨水层 ----
    private val strokes = ArrayList<Stroke>()
    private var currentStroke: Stroke? = null
    private var inkBitmap: Bitmap? = null
    private var inkCanvas: Canvas? = null
    private var drinkAlpha = 1f

    private val inkPaint = Paint().apply {
        color = context.getColor(R.color.ink)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    private val layerPaint = Paint().apply { isAntiAlias = true }

    // ---- 回复层 ----
    private val replyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.faded_ink)
        typeface = Config.replyTypeface(context)   // 设置里可随时换
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.faded_ink)
        textSize = 34f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
    }
    private var glyphs = listOf<Glyph>()
    private var replyStart = 0L      // 回复开始书写的时间戳
    private var replyWrittenDone = false

    // ---- 协作对象 ----
    private val memory = MemoryStore(context)
    private val oracle = OracleClient(context)

    private val density = resources.displayMetrics.density
    private val idleDelayMs = 2800L       // 与原版一致的"停笔即吞墨"
    private val drinkDurationMs = 1500L
    private val revealMsPerChar = 42f
    private val eraseRadiusPx = 30f * density

    private val idleRunnable = Runnable { drinkAndAsk() }

    // ---------------------------------------------------------------- input

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 一旦出现过触控笔就拒收手指：防手掌误触。
                val tool = event.getToolType(0)
                if (tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER) {
                    stylusSeen = true
                } else if (stylusSeen) {
                    return false
                }

                onTouchStarts()
                if (event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER || isEraseButton(event)) {
                    eraseAt(event.x, event.y)
                } else {
                    beginStroke(event.x, event.y, event.pressure)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val n = event.historySize
                for (i in 0 until n) {
                    step(event.getHistoricalX(i), event.getHistoricalY(i),
                        event.getHistoricalPressure(i), event)
                }
                step(event.x, event.y, event.pressure, event)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endStroke()
            else -> return false
        }
        return true
    }

    @Volatile
    private var stylusSeen = false

    private fun isEraseButton(e: MotionEvent): Boolean =
        e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0

    private fun onTouchStarts() {
        removeCallbacks(idleRunnable)
        oracle.cancel()
        cancelDrink()
        if (state == State.THINKING || state == State.WRITING_REPLY) {
            clearReply()      // 翻页：旧的回答随新的一笔消失
        }
        state = State.WRITING
    }

    private fun step(x: Float, y: Float, pressure: Float, e: MotionEvent) {
        if (state != State.WRITING) return
        if (e.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER || isEraseButton(e)) {
            eraseAt(x, y)
            return
        }
        addPoint(x, y, pressure)
    }

    private fun beginStroke(x: Float, y: Float, pressure: Float) {
        val s = Stroke()
        s.path.moveTo(x, y)
        currentStroke = s
        strokes.add(s)
        addPoint(x, y, pressure)
    }

    private fun addPoint(x: Float, y: Float, pressure: Float) {
        val s = currentStroke ?: return
        s.path.lineTo(x, y)
        s.xs.add(x)
        s.ys.add(y)
        inkPaint.strokeWidth = strokeFor(pressure)
        inkCanvas?.drawPath(s.path, inkPaint)
        invalidate()
    }

    private fun endStroke() {
        currentStroke = null
        scheduleIdle()
    }

    private fun strokeFor(pressure: Float): Float {
        val base = width.coerceAtLeast(720) * 0.0032f
        return base * (0.55f + pressure.coerceIn(0f, 1f) * 1.15f)
    }

    private fun eraseAt(x: Float, y: Float) {
        val before = strokes.size
        strokes.removeAll { s ->
            s.xs.indices.any { hypot(s.xs[it] - x, s.ys[it] - y) < eraseRadiusPx }
        }
        if (strokes.size != before) rerenderInk()
        scheduleIdle()
    }

    private fun scheduleIdle() {
        removeCallbacks(idleRunnable)
        postDelayed(idleRunnable, idleDelayMs)
    }

    // ------------------------------------------------------------- drinking

    /** 停笔：先留档整页，再让墨迹被纸吸走，然后把整页交给 Tom。 */
    private fun drinkAndAsk() {
        if (state != State.WRITING) return
        val hasInk = strokes.any { it.xs.isNotEmpty() } &&
            strokes.sumOf { it.xs.size } >= 3
        if (!hasInk) return

        val page = renderPageImage()

        state = State.DRINKING
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = drinkDurationMs
            addUpdateListener {
                drinkAlpha = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var canceled = false
                private var fired = false
                override fun onAnimationCancel(animation: Animator) {
                    canceled = true   // 中途被打断（用户继续写）就不提交
                }
                override fun onAnimationEnd(animation: Animator) {
                    if (fired || canceled) return
                    fired = true      // 自然走完恰好触发一次
                    clearStrokesOnly()
                    handToOracle(page)
                }
            })
            start()
        }.also { drinkAnim = it }
    }

    private var drinkAnim: ValueAnimator? = null

    private fun cancelDrink() {
        drinkAnim?.cancel()
        drinkAnim = null
        drinkAlpha = 1f
    }

    /** 摊开在模型面前的那页纸：米色底 + 全部字迹，最长边压到 1024px 控制请求体积。 */
    private fun renderPageImage(): Bitmap {
        val scale = minOf(1f, 1024f / width.coerceAtLeast(height))
        val w = (width * scale).toInt()
        val h = (height * scale).toInt()
        val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bm)
        c.drawColor(context.getColor(R.color.paper))
        c.scale(scale, scale)
        inkPaint.strokeWidth = strokeFor(0.45f)
        strokes.forEach { c.drawPath(it.path, inkPaint) }
        return bm
    }

    private fun clearStrokesOnly() {
        strokes.clear()
        inkBitmap?.eraseColor(Color.TRANSPARENT)
        drinkAlpha = 1f
        invalidate()
    }

    private fun rerenderInk() {
        inkBitmap?.eraseColor(Color.TRANSPARENT)
        val c = inkCanvas ?: return
        inkPaint.strokeWidth = strokeFor(0.45f)
        strokes.forEach { c.drawPath(it.path, inkPaint) }
        invalidate()
    }

    // --------------------------------------------------------------- oracle

    private fun handToOracle(page: Bitmap) {
        state = State.THINKING
        if (Config.key(context).isBlank()) {
            showAsReply("有人在封面里夹了一张字条：写下 API Key，方法是把日记合上，点右上角的齿轮。")
            return
        }
        oracle.ask(page, memory.recentTurns(), object : OracleClient.Listener {
            override fun onChunk(text: String) {
                appendReply(text)
            }

            override fun onError(message: String) {
                if (state == State.THINKING) showAsReply("墨水泛起涟漪：$message")
            }

            override fun onDone(fullDisplay: String, transcript: String) {
                // 用累计全文覆盖（比逐 chunk 拼接更稳），再次过滤指令；
                // 存记忆时去掉 ⁂ 转写后记，只留正文。
                setReply(fullDisplay)
                val replyOnly = fullDisplay.substringBefore('⁂').trim()
                memory.add(System.currentTimeMillis(), transcript, replyOnly)
            }
        })
    }

    // ---------------------------------------------------------- reply write

    private fun appendReply(chunk: String) {
        setReply(currentDisplayRaw + chunk)
    }

    private var currentDisplayRaw = ""

    // 排版笔的位置与浮现时钟——跨数据块持续推进，不因新 chunk 归零
    private var penX = 0f
    private var penY = 0f
    private var nextAppearAt = 0L

    /**
     * 流式更新回复。若 text 是在旧文后面续写（正常情况），只排版新增的字符，
     * 已显示的部分与其浮现时刻保持不变——动画连续，不会从头重播。
     */
    private fun setReply(text: String) {
        if (text.isEmpty()) return
        val isAppend = currentDisplayRaw.isNotEmpty() &&
            text.startsWith(currentDisplayRaw) && glyphs.isNotEmpty()
        val added = if (isAppend) text.substring(currentDisplayRaw.length) else text

        if (!isAppend) {
            // 全新的一页：重置排版与动画时钟
            glyphs = emptyList()
            replyPaint.typeface = Config.replyTypeface(context)
            replyPaint.textSize = fontSize()
            val fm = replyPaint.fontMetrics
            penX = width * 0.10f
            penY = height * 0.16f - fm.ascent
            nextAppearAt = 0L
            replyStart = System.currentTimeMillis()
            replyWrittenDone = false
            state = State.WRITING_REPLY
        }
        if (added.isEmpty()) {
            currentDisplayRaw = text
            return
        }

        replyPaint.textSize = fontSize()
        val fm = replyPaint.fontMetrics
        val lineH = (fm.descent - fm.ascent) * 1.3f
        val marginX = width * 0.10f
        val out = ArrayList<Glyph>(glyphs)
        for (ch in added) {
            if (ch == '\n') {
                penX = marginX
                penY += lineH
                continue
            }
            val adv = replyPaint.measureText(ch.toString())
            if (penX + adv > width - marginX) {
                penX = marginX
                penY += lineH
            }
            out.add(Glyph(ch, penX, penY, jitter(out.size), nextAppearAt))
            penX += adv
            nextAppearAt += revealMsPerChar.toLong()
        }
        glyphs = out
        currentDisplayRaw = text
        postInvalidateOnAnimation()
    }

    private fun showAsReply(text: String) {
        currentDisplayRaw = ""
        setReply(text)
    }

    private fun fontSize() = width * 0.045f

    /** 确定性抖动，让每个字母轻微歪斜，像手写的呼吸感。 */
    private fun jitter(i: Int): Float = ((i * 37 + 11) % 13 - 6) * 0.35f

    private fun clearReply() {
        currentDisplayRaw = ""
        glyphs = emptyList()
        replyWrittenDone = true
        invalidate()
    }

    // ------------------------------------------------------------------ draw

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            inkCanvas = Canvas(bm)
            inkBitmap = bm
            rerenderInk()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 被吸走的墨迹
        layerPaint.alpha = (drinkAlpha * 255).toInt()
        inkBitmap?.let { canvas.drawBitmap(it, 0f, 0f, layerPaint) }

        // Tom 正在写 / 已写下的回答
        when (state) {
            State.THINKING -> drawThinking(canvas)
            State.WRITING_REPLY -> drawReply(canvas, animate = true)
            else -> if (glyphs.isNotEmpty()) drawReply(canvas, animate = false)
        }
    }

    private fun drawThinking(canvas: Canvas) {
        val phase = (System.currentTimeMillis() / 600) % 3
        canvas.drawText(
            "the ink settles" + ".".repeat(phase.toInt() + 1),
            width / 2f, height * 0.16f, statusPaint,
        )
        postInvalidateDelayed(300)
    }

    private fun drawReply(canvas: Canvas, animate: Boolean) {
        val now = System.currentTimeMillis()
        var allShown = true
        replyPaint.textSize = fontSize()   // layoutReply 与此处保持同一字号
        for (g in glyphs) {
            val elapsed = now - replyStart - g.appearAtMs
            val alpha = when {
                !animate -> 255
                elapsed < 0 -> { allShown = false; continue }
                elapsed < 160 -> { allShown = false; (elapsed * 255 / 160).toInt() }
                else -> 255
            }
            replyPaint.alpha = alpha
            canvas.save()
            canvas.rotate(g.rotationDeg, g.x, g.baselineY)
            canvas.drawText(g.ch.toString(), g.x, g.baselineY, replyPaint)
            canvas.restore()
        }
        if (animate && !allShown) {
            replyWrittenDone = false
            postInvalidateOnAnimation()
        } else if (!replyWrittenDone) {
            replyWrittenDone = true
        }
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDetachedFromWindow() {
        removeCallbacks(idleRunnable)
        oracle.cancel()
        super.onDetachedFromWindow()
    }
}
