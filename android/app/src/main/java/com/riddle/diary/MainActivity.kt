package com.riddle.diary

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * 全屏日记。右上角一枚半透明齿轮，是本子里唯一露在外面的机关。
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val diary = DiaryView(this)

        fun ghostButton(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            textSize = 22f
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setTextColor(getColor(R.color.faded_ink))
            setOnClickListener { onClick() }
        }

        // 右上角两枚半透明按钮：往事（☰）与封印（⚙）
        val corner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(ghostButton("☰") {
                startActivity(android.content.Intent(this@MainActivity, MemoriesActivity::class.java))
            })
            addView(ghostButton("⚙") {
                startActivity(android.content.Intent(this@MainActivity, SettingsActivity::class.java))
            })
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            marginEnd = (12 * resources.displayMetrics.density).toInt()
            topMargin = (10 * resources.displayMetrics.density).toInt()
        }

        val root = FrameLayout(this).apply {
            addView(diary, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(corner, params)
        }
        setContentView(root)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    private fun hideSystemUi() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }
}
