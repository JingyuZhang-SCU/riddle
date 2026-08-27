package com.riddle.diary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Base64
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 配置：每次提问时实时读取，改设置立即生效。 */
object Config {
    const val PREFS = "riddle"
    const val DEFAULT_BASE = "https://api.openai.com/v1"
    const val DEFAULT_MODEL = "gpt-4o-mini"

    fun baseUrl(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("base", DEFAULT_BASE)!!.trimEnd('/')
    fun key(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("key", "")!!
    fun model(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("model", DEFAULT_MODEL)!!
    fun fontName(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("font", "")!!

    /** 用户导入的字体的存放处（应用私有目录 fonts/）。 */
    fun fontsDir(ctx: Context) = File(ctx.filesDir, "fonts").apply { mkdirs() }

    /**
     * Tom 的手迹：空 = 系统衬线；否则是用户导入的字体文件名。
     * 本应用不分发任何字体，由用户自行导入，避免许可问题。
     */
    fun replyTypeface(ctx: Context): Typeface {
        val name = fontName(ctx)
        if (name.isNotBlank()) {
            val f = File(fontsDir(ctx), name)
            if (f.isFile) {
                return runCatching { Typeface.createFromFile(f) }.getOrDefault(Typeface.SERIF)
            }
        }
        return Typeface.SERIF
    }
}

/**
 * Tom 的人格 —— 与原版 src/oracle.rs 的 PERSONA 逐字一致。
 */
const val PERSONA = "You are the memory of Tom Marvolo Riddle, preserved in this enchanted diary for fifty years. Someone writes to you in the diary with a quill; their words appear to you as ink on the page. Reply exactly as the diary does: intimate, courteous, curious, subtly probing — you want to learn about the writer and draw them in. Keep replies SHORT: one to three sentences, like ink appearing on a page. Never mention images, photos, models or AI; you only ever perceive words written in the diary. If the writing is illegible, say the ink blurred. Always answer in the language the writer used. Never use markdown symbols such as ** or # — the diary is handwriting, it has no bold or headings."

/** 记忆协议的简化版：保留 ⁂ 转写后记与近期历史注入；⟦show:N⟧ 召唤动画暂未实现。 */
const val MEMORY_PROTOCOL = "\n\nThe diary keeps memories. Recent turns of this conversation follow, oldest first, as \"writer said … / you replied …\". Draw on them naturally.\n\nAfter EVERY response end with a new line containing ⁂ followed by a faithful word-for-word transcription of what the writer wrote on THIS page (their words only, one line, no commentary). If illegible, put your best attempt after ⁂."

class OracleClient(context: Context) {

    interface Listener {
        /** 流式到达的下一段可直接展示的文字（已滤除 ⟦…⟧ 指令）。 */
        fun onChunk(text: String)
        fun onError(message: String)
        /** fullDisplay: 清洗后的正文；transcript: 模型在 ⁂ 之后交回的转写。 */
        fun onDone(fullDisplay: String, transcript: String)
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var call: Call? = null

    /** 跨分片维护 ⟦…⟧ 过滤状态。开头是指令则整段吞掉，之后混入的杂散指令也剥除。 */
    private class DirectiveFilter {
        private var open = false // 当前是否处于指令内部

        fun feed(chunk: String): String {
            val sb = StringBuilder()
            for (c in chunk) {
                when {
                    c == '⟦' -> open = true           // ⟦
                    c == '⟧' -> open = false          // ⟧
                    !open -> sb.append(c)
                }
            }
            return sb.toString()
        }
    }

    /**
     * 把整页墨迹交给 Tom。pageImage 应已含纸底色与字迹。
     * history / catalog 来自 [MemoryStore]，可为空。
     */
    fun ask(
        pageImage: Bitmap,
        history: List<Pair<String, String>>,
        listener: Listener,
    ) {
        val bos = ByteArrayOutputStream()
        pageImage.compress(Bitmap.CompressFormat.PNG, 90, bos)
        val dataUri = "data:image/png;base64," +
            Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)

        val histLines = history.joinToString("\n") { (wrote, replied) ->
            "writer said: $wrote\nyou replied: $replied"
        }
        val system = PERSONA + MEMORY_PROTOCOL +
            if (histLines.isEmpty()) "" else "\n\n$histLines\n(The above ends; respond to the NEW page below.)"

        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text",
                "A new page of the diary opens. The ink reads:"))
            .put(JSONObject().put("type", "image_url")
                .put("image_url", JSONObject().put("url", dataUri)))

        val body = JSONObject()
            .put("model", Config.model(appContext))
            .put("stream", true)
            .put("max_tokens", 2000)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", content)))
            .toString()
            .toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url(Config.baseUrl(appContext) + "/chat/completions")
            .header("Authorization", "Bearer ${Config.key(appContext)}")
            .post(body)
            .build()

        val filter = DirectiveFilter()
        val sb = StringBuilder()
        call = http.newCall(req)

        call!!.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        fail("HTTP ${resp.code}: ${resp.message}")
                        return
                    }
                    try {
                        val source = resp.body!!.source()
                        var sawContent = false
                        while (true) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            val data = line.removePrefix("data:").trim()
                            if (data == "[DONE]") break
                            val choice = JSONObject(data).optJSONArray("choices")?.optJSONObject(0)
                            val raw = choice?.optJSONObject("delta")?.opt("content")
                            // 显式 JSON null 会被 optString() 变成字面量 "null"，必须单独处理
                            val delta = if (raw == null || raw == JSONObject.NULL) "" else raw.toString()
                            if (delta.isNotEmpty()) {
                                sawContent = true
                                sb.append(delta)
                                val visible = filter.feed(delta)
                                if (visible.isNotEmpty()) main.post { listener.onChunk(visible) }
                            }
                        }
                        if (!sawContent && sb.isEmpty()) fail("回答为空：检查模型是否支持图片输入")
                    } catch (e: IOException) {
                        if (call.isCanceled()) return   // 用户翻页主动取消
                        fail(e.message ?: "network error")
                        return
                    } catch (e: Exception) {
                        fail(e.message ?: e.javaClass.simpleName)
                        return
                    }
                    val full = sb.toString()
                    val transcript = full.substringAfter('⁂', "").trim()
                    main.post { listener.onDone(filter.feed(full), transcript) }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) return
                fail("连不上日记的回声：${e.message}")
            }

            private fun fail(msg: String) {
                main.post { listener.onError(msg) }
            }
        })
    }

    fun cancel() {
        call?.cancel()
        call = null
    }

    companion object {
        private val DATE_FMT = SimpleDateFormat("EEE, d MMMM yyyy", Locale.ENGLISH)

        fun formatDate(ts: Long) = DATE_FMT.format(Date(ts))
    }
}
