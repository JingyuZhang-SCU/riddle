package com.riddle.diary

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * 日记的封印：配置任意 OpenAI 兼容接口，以及管理 Tom 的手迹字体。
 * 本应用不分发任何字体；字体由用户从自己的文件导入（.ttf / .otf），
 * 存放在应用私有目录 fonts/ 下。默认使用系统衬线体。
 */
class SettingsActivity : Activity() {

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var fontList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(Config.PREFS, MODE_PRIVATE)

        val pad = (20 * resources.displayMetrics.density).toInt()

        fun label(text: String): TextView = TextView(this).apply {
            this.text = text
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
        }

        val baseInput = EditText(this).apply {
            hint = Config.DEFAULT_BASE
            setText(prefs.getString("base", ""))
            setSingleLine(true)
        }
        val keyInput = EditText(this).apply {
            hint = "sk-…"
            setText(prefs.getString("key", ""))
            setSingleLine(true)
        }
        val modelInput = EditText(this).apply {
            hint = Config.DEFAULT_MODEL + " / glm-4v-flash / qwen-vl-plus …"
            setText(prefs.getString("model", ""))
            setSingleLine(true)
        }
        val save = Button(this).apply {
            text = getString(R.string.save)
            setOnClickListener {
                prefs.edit()
                    .putString("base", baseInput.text.toString().trim())
                    .putString("key", keyInput.text.toString().trim())
                    .putString("model", modelInput.text.toString().trim())
                    .apply()
                Toast.makeText(context, "封印已更新", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        fontList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rebuildFontList()

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            title()
            label(getString(R.string.base_url)); field(baseInput)
            label(getString(R.string.api_key)); field(keyInput)
            label(getString(R.string.model)); field(modelInput)
            label(getString(R.string.font_choice)); addView(fontList)
            spacer(); addView(save)
        })
    }

    // --------------------------------------------------------------- 字体管理

    /** 当前 Tom 手迹的可选项：系统衬线 + 用户导入的每一枚字体。 */
    private fun fontFiles(): List<File> =
        Config.fontsDir(this).listFiles { f ->
            f.isFile && (f.name.endsWith(".ttf", true) || f.name.endsWith(".otf", true))
        }?.sortedBy { it.name.lowercase() } ?: emptyList()

    private fun rebuildFontList() {
        fontList.removeAllViews()
        val current = Config.fontName(this)

        fontList.addView(ChoiceButton(
            name = getString(R.string.font_serif),
            selected = current.isBlank(),
        ).also { b ->
            b.setOnClickListener {
                prefs.edit().putString("font", "").apply()
                rebuildFontList()
            }
        })

        fontFiles().forEach { f ->
            val btn = ChoiceButton(
                name = f.nameWithoutExtension,
                selected = current == f.name,
            ).also { b ->
                b.setOnClickListener {
                    prefs.edit().putString("font", f.name).apply()
                    rebuildFontList()
                }
                b.setOnLongClickListener {
                    confirmFontDelete(f)
                    true
                }
            }
            fontList.addView(btn)
        }

        fontList.addView(Button(this).apply {
            text = getString(R.string.font_import)
            textSize = 14f
            transformationMethod = null
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { pickFont() }
        })
    }

    private fun ChoiceButton(name: String, selected: Boolean) = Button(this).apply {
        text = name
        textSize = 14f
        transformationMethod = null
        setBackgroundColor(if (selected) 0x338A8574.toInt() else Color.TRANSPARENT)
    }

    private fun confirmFontDelete(f: File) {
        AlertDialog.Builder(this)
            .setTitle(R.string.font_delete_title)
            .setMessage(getString(R.string.font_delete_msg, f.nameWithoutExtension))
            .setPositiveButton(R.string.confirm_delete) { _, _ ->
                if (Config.fontName(this) == f.name) {
                    prefs.edit().putString("font", "").apply()
                }
                f.delete()
                rebuildFontList()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun pickFont() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "font/ttf", "font/otf", "font/sfnt", "font/collection",
                "application/x-font-ttf", "application/x-font-otf", "application/octet-stream",
            ))
        }, REQ_FONT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_FONT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        var name = "imported.ttf"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0)?.let { name = it } }
        // 只保留安全字符，并确保扩展名可识别
        name = name.replace(Regex("[^A-Za-z0-9._\\-\\u4e00-\\u9fa5]"), "_")
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext != "ttf" && ext != "otf") name = "$base.ttf"

        try {
            val dest = File(Config.fontsDir(this), name)
            contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            prefs.edit().putString("font", dest.name).apply()
            Toast.makeText(this, getString(R.string.font_imported, dest.nameWithoutExtension), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "字体导入失败", Toast.LENGTH_LONG).show()
        }
        rebuildFontList()
    }

    // ----------------------------------------------------------------- 布局

    private fun LinearLayout.title() {
        addView(TextView(context).apply {
            text = getString(R.string.settings_title)
            textSize = 24f
            typeface = Typeface.SERIF
        })
    }

    private fun LinearLayout.field(edit: EditText) {
        edit.setEms(20)
        addView(edit)
    }

    private fun LinearLayout.spacer() {
        addView(TextView(context).apply { text = " " })
    }

    companion object {
        private const val REQ_FONT = 41
    }
}
