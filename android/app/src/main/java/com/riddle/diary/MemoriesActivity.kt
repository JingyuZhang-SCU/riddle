package com.riddle.diary

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * 往事：翻阅日记的记忆（新页在前）。
 * 点一页展开 Tom 的完整回答；长按撕下（删除）单页；右上角可烧掉整本。
 */
class MemoriesActivity : Activity() {

    private lateinit var store: MemoryStore
    private lateinit var list: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = MemoryStore(this)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Button(this@MemoriesActivity).apply {
                text = getString(R.string.memories_back)
                setOnClickListener { finish() }
            })
            addView(android.view.View(this@MemoriesActivity),
                LinearLayout.LayoutParams(0, 1, 1f))
            addView(Button(this@MemoriesActivity).apply {
                text = getString(R.string.memories_clear)
                setOnClickListener { confirmClear() }
            })
        }

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            addView(TextView(this@MemoriesActivity).apply {
                text = getString(R.string.memories_title)
                textSize = 24f
                typeface = Typeface.SERIF
            })
            addView(header, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(scroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
        rebuild()
    }

    private fun rebuild() {
        list.removeAllViews()
        val entries = store.all().reversed()   // 新写的页在最上面
        if (entries.isEmpty()) {
            list.addView(TextView(this).apply {
                text = getString(R.string.memories_empty)
                textSize = 15f
                setTextColor(getColor(R.color.faded_ink))
                setPadding(0, dp(24), 0, 0)
            })
            return
        }
        entries.forEach { list.addView(pageView(it)) }
    }

    /** 一页往事：日期 + 你的字（转写）+ Tom 的回答。点按展开，长按撕下。 */
    private fun pageView(e: MemoryStore.Entry): android.view.View {
        val replyView = TextView(this).apply {
            text = e.reply
            textSize = 16f
            setTextColor(getColor(R.color.faded_ink))
            typeface = Config.replyTypeface(this@MemoriesActivity)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        var expanded = false
        fun apply() {
            replyView.maxLines = if (expanded) Int.MAX_VALUE else 2
            replyView.ellipsize = if (expanded) null else TextUtils.TruncateAt.END
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), 0, dp(16))
            addView(TextView(this@MemoriesActivity).apply {
                text = OracleClient.formatDate(e.time)
                textSize = 12f
                setTextColor(getColor(R.color.faded_ink))
                typeface = Typeface.SERIF
            })
            addView(TextView(this@MemoriesActivity).apply {
                text = e.transcript
                textSize = 18f
                setTextColor(getColor(R.color.ink))
                setPadding(0, dp(4), 0, dp(4))
            })
            addView(replyView)
            setOnClickListener {
                expanded = !expanded
                apply()
            }
            setOnLongClickListener {
                confirmDelete(e)
                true
            }
        }
    }

    private fun confirmDelete(e: MemoryStore.Entry) {
        AlertDialog.Builder(this)
            .setTitle(R.string.memories_delete_title)
            .setMessage(e.transcript + "\n\n" + getString(R.string.memories_delete_msg))
            .setPositiveButton(R.string.confirm_delete) { _, _ ->
                store.delete(e.id)
                Toast.makeText(this, R.string.memories_torn, Toast.LENGTH_SHORT).show()
                rebuild()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(R.string.memories_clear_title)
            .setMessage(R.string.memories_clear_msg)
            .setPositiveButton(R.string.confirm_delete) { _, _ ->
                store.clearAll()
                rebuild()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
