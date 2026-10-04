package com.aurora.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/** In-app event log (reachable via the Log tab). Newest first, capped at 200. */
object AppLog {

    enum class Dir { SENT, RECV, SYSTEM }

    data class Entry(val id: Long, val timeMillis: Long, val dir: Dir, val text: String)

    private const val CAP = 200
    private val seq = AtomicLong(0)
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun add(dir: Dir, text: String) {
        val entry = Entry(seq.incrementAndGet(), System.currentTimeMillis(), dir, text)
        _entries.value = (listOf(entry) + _entries.value).take(CAP)
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
