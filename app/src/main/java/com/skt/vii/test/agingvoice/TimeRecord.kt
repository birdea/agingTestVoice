package com.skt.vii.test.agingvoice

import java.text.SimpleDateFormat

class TimeRecord {

    var timeStarted: Long = 0
    var timeRestarted: Long = 0
    var timeStopped: Long = 0

    val sdf = SimpleDateFormat("yy/MM/dd HH:mm:ss")

    fun onStarted() {
        if (timeStarted == 0L) {
            timeStarted = System.currentTimeMillis()
        } else {
            timeRestarted = System.currentTimeMillis()
        }
    }

    fun onStopped() {
        timeStopped = System.currentTimeMillis()
    }

    fun getSummaryTimeInfo(): String {
        var sb = StringBuilder()
        sb.append("- start: ${sdf.format(timeStarted)}\n")
        sb.append("- elapsed(st): ${getDurationTime(timeStarted)}\n")
        if (timeRestarted > 0) {
            sb.append("- restart: ${sdf.format(timeRestarted)}\n")
            sb.append("- elapsed(rest): ${getDurationTime(timeRestarted)}\n")
        }
        if (timeStopped > 0) {
            sb.append("- stop: ${sdf.format(timeStopped)}\n")
        }
        return sb.toString()
    }

    fun getDurationTime(timeFrom: Long): String {
        val duration = (System.currentTimeMillis() - timeFrom) / (1000 * 60)
        return "$duration mins"
    }

}