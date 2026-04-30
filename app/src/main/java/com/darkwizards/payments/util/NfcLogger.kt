package com.darkwizards.payments.util

import android.util.Log
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * In-app NFC diagnostic logger.
 * Keeps the last 50 log lines in memory so they can be displayed
 * in the TapScreen debug overlay without needing ADB.
 */
object NfcLogger {

    private const val MAX_LINES = 50
    private val _lines = ArrayDeque<String>(MAX_LINES)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        append("D/$tag: $msg")
    }

    fun e(tag: String, msg: String) {
        Log.e(tag, msg)
        append("E/$tag: $msg")
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        append("I/$tag: $msg")
    }

    private fun append(line: String) {
        val ts = LocalTime.now().format(timeFmt)
        synchronized(_lines) {
            if (_lines.size >= MAX_LINES) _lines.removeFirst()
            _lines.addLast("[$ts] $line")
        }
    }

    fun getLines(): List<String> = synchronized(_lines) { _lines.toList() }

    fun clear() = synchronized(_lines) { _lines.clear() }
}
