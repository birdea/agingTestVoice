package com.skt.vii.test.agingvoice

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    val TAG = "MainActivity"

    var ttsLooper = AgingTestManager(this)

    var tv_test_summary: TextView? = null
    var tv_current_index: TextView? = null
    var tv_current_command:TextView? = null
    var tv_time_info:TextView? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        Log.d(TAG, "onCreate()")

        findView()
        init()
    }

    fun findView () {
        tv_test_summary = findViewById (R.id.tv_test_summary)
        tv_current_index = findViewById (R.id.tv_current_index)
        tv_current_command = findViewById (R.id.tv_current_command)
        tv_time_info = findViewById (R.id.tv_time_info)
    }

    fun init() {
        tv_test_summary?.setText(ttsLooper.getSummaryMessage())

        ttsLooper.setOnUpdateCallback(object: AgingTestManager.UpdateCallback{
            override fun onUpdate(message: String, countdown: Int) {
                var cur = ttsLooper.commandIndex
                var total = ttsLooper.commandExectued
                var timeInfo = ttsLooper.timeTestRecord.getSummaryTimeInfo()
                runOnUiThread {
                    tv_current_index?.setText("-test.index (cur/total): $cur/$total")
                    if (!message.isNullOrEmpty()) {
                        tv_current_command?.setText("-test.command.message\n$message")
                    }
                    if (countdown > -1) {
                        timeInfo += "\n-countdown: $countdown"
                    }
                    tv_time_info?.setText("-test.time.info\n$timeInfo")
                }
            }
        })
        ttsLooper.start()
    }

    fun onClick(view: View) {
        when(view.id) {
            R.id.btn_start -> {
                ttsLooper.start()
            }
            R.id.btn_stop -> {
                ttsLooper.stop()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy()")
        ttsLooper.stop()
    }
}