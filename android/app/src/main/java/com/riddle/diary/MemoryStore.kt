package com.riddle.diary

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 日记的记忆：每一页完成后存一条 JSON（时间、转写、Tom 的回答），
 * 放在应用私有目录 memories/ 下，保留最近 MAX 条。对应原版的
 * /home/root/riddle-data/memories。
 */
class MemoryStore(context: Context) {

    data class Entry(val id: Long, val time: Long, val transcript: String, val reply: String)

    private val dir = File(context.filesDir, "memories").apply { mkdirs() }

    companion object {
        private const val MAX = 400
        private const val HISTORY_TURNS = 3      // 注入正文的最近轮数
        private const val GIST_CHARS = 60        // 目录里每条转写的截断长度
    }

    fun add(time: Long, transcript: String, reply: String): Entry? {
        if (transcript.isBlank()) return null
        var id = System.currentTimeMillis()
        val f = File(dir, "$id.json")
        if (!f.createNewFile()) id += 1   // 同毫秒碰撞极小，防御一下
        f.writeText(
            JSONObject()
                .put("time", time)
                .put("transcript", transcript)
                .put("reply", reply)
                .toString()
        )
        prune()
        return Entry(id, time, transcript, reply)
    }

    /** 最近 n 轮对话，旧的在前。 */
    fun recentTurns(): List<Pair<String, String>> =
        entries().takeLast(HISTORY_TURNS).map { it.transcript to it.reply }

    /** 所有记忆条目，旧的在前。 */
    fun all(): List<Entry> = entries()

    /** 撕下这一页。 */
    fun delete(id: Long) {
        File(dir, "$id.json").delete()
    }

    /** 烧掉整本日记。 */
    fun clearAll() {
        dir.listFiles { f -> f.name.endsWith(".json") }?.forEach { it.delete() }
    }

    private fun entries(): List<Entry> =
        dir.listFiles { f -> f.name.endsWith(".json") }
            ?.sortedBy { it.nameWithoutExtension.toLongOrNull() ?: 0L }
            ?.mapNotNull { f ->
                runCatching {
                    val o = JSONObject(f.readText())
                    Entry(
                        f.nameWithoutExtension.toLong(),
                        o.getLong("time"),
                        o.getString("transcript"),
                        o.getString("reply"),
                    )
                }.getOrNull()
            }
            ?: emptyList()

    private fun prune() {
        val files = dir.listFiles { f -> f.name.endsWith(".json") } ?: return
        if (files.size <= MAX) return
        files.sortedBy { it.nameWithoutExtension.toLongOrNull() ?: Long.MAX_VALUE }
            .take(files.size - MAX)
            .forEach { it.delete() }
    }
}
