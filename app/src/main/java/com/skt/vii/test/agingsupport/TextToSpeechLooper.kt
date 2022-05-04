package com.skt.vii.test.agingvoice

import android.content.Context
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import org.intellij.lang.annotations.Language
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit

class TextToSpeechLooper (val context: Context) {

    val TAG = "TextToSpeechLooper"

    val messsages = arrayOf(
        "What time is it now?",
        //"What is the Weather in Seoul?",
        //"Who is the Barack Obama?",
        "Play music.",
        //"Stop music."
    )

    val COUNTDOWN_TICK = 1000L

    val COUNTDOWN_DELAY_WAKEWORD = 2500L
    val COUNTDOWN_DELAY_COMMAND = 2500L
    val COUNTDOWN_DELAY_IDLE = 15000L

    val sdf = SimpleDateFormat("yy/MM/dd HH:mm:ss")

    var timeTestStarted: Long = 0
    var timeTestRestarted: Long = 0
    var timeTestStopped: Long = 0

    fun getSummaryTimeInfo(): String {
        var sb = StringBuilder()
        sb.append("- start: ${sdf.format(timeTestStarted)}\n")
        sb.append("- elapsed(st): ${getDurationTime(timeTestStarted)}\n")
        if (timeTestRestarted > 0) {
            sb.append("- restart: ${sdf.format(timeTestRestarted)}\n")
            sb.append("- elapsed(rest): ${getDurationTime(timeTestRestarted)}\n")
        }
        if (timeTestStopped > 0) {
            sb.append("- stop: ${sdf.format(timeTestStopped)}\n")
        }
        return sb.toString()
    }

    fun getDurationTime(timeFrom: Long): String {
        val duration = (System.currentTimeMillis() - timeFrom) / (1000 * 60)
        return "$duration mins"
    }

    var commandIndex = 0
    var commandExectued = 0
    fun getSummaryMessage(): String {
        var sb = StringBuilder()
        sb.append("-test.command.set\n")
        var index = 0
        for (item in messsages) {
            sb.append("[${index++}] $item\n")
        }
        return sb.toString()
    }

    fun getCurrentCommand() : String {
        return messsages[commandIndex]
    }

    var uiHandler = Handler(Looper.getMainLooper())

    interface UpdateCallback {
        fun onUpdate(message: String, countdown: Int = -1)
    }

    var updateListener : UpdateCallback? = null

    fun setOnUpdateCallback(listener: UpdateCallback){
        updateListener = listener
    }

    fun notifyCallback(message: String, countdown: Int = -1) {
        updateListener?.onUpdate(message, countdown)
    }




    val strWakeword: String = "ALEXA"

    var ttsCommand:TextToSpeech? = null

    fun start() {

        if (timeTestStarted == 0L) {
            timeTestStarted = System.currentTimeMillis()
        } else {
            timeTestRestarted = System.currentTimeMillis()
        }

        notifyCallback("started")

        if (ttsCommand == null) {
            ttsCommand = TextToSpeech(context.applicationContext, object : TextToSpeech.OnInitListener {
                override fun onInit(status: Int) {
                    Log.d(TAG, "ttsCommand.onInit() status:$status")

                    notifyCallback("TTS.inited(status=$status)")

                    postStart()
                }
            })
        }
    }

    fun stop() {
        Log.d(TAG, "stop()")

        commandHandler.removeMessages(0)
        commandHandler.removeMessages(1)

        ttsCommand?.stop()
        ttsCommand?.shutdown()
        ttsCommand = null

        timeTestStopped = System.currentTimeMillis()

        notifyCallback("stopped")
    }

    private fun postStart() {

        ttsCommand?.setLanguage(Locale.US)

        var voiceList = ttsCommand?.voices?.iterator()
        voiceList?.let{
            for (item in voiceList) {
                if (item.locale.country == Locale.US.country && item.locale.language == Locale.ENGLISH.language && item.quality >= Voice.QUALITY_HIGH) {
                    Log.d(TAG, "voice: $item")
                }
            }
        }

        ttsCommand?.setOnUtteranceProgressListener(object : UtteranceProgressListener(){
            override fun onStart(status: String?) {
                Log.d(TAG, "onStart($status)")
            }

            override fun onDone(status: String?) {
                Log.d(TAG, "onDone($status)")

                if (status == strWakeword) {
                    // wakeword -> command
                    commandHandler.sendEmptyMessage(0)
                } else {
                    // command -> wakeword
                    notifyCallback("TTS.onDone: $status")
                    countDownTimerIdle.start()
                }
            }

            override fun onError(status: String?) {
                Log.d(TAG, "onError($status)")

                notifyCallback("TTS.onError:$status")
            }
        })
        //
        startWakeword()
    }

    var commandHandler = CommandHandler(this)

    class CommandHandler(val controller: TextToSpeechLooper) : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                0 -> {
                    controller.startCommand()
                }
                1 -> {
                    controller.startWakeword()
                }
            }
        }
    }

    private fun setCommandExecuted() {
        Log.d(TAG, "setCommandExecuted(${commandIndex})")
        commandIndex++
        commandExectued++
    }

    private fun startWakeword() {
        countDownTimerWakeword.start()

    }

    private fun startCommand() {
        countDownTimerCommand.start()
    }

    // status: idle -> wakeword
    var countDownTimerWakeword: CountDownTimer = object: CountDownTimer(COUNTDOWN_DELAY_WAKEWORD,COUNTDOWN_TICK) {
        override fun onTick(progess: Long) {
            notifyCallback("", (progess/1000).toInt())
        }
        override fun onFinish() {
            uiHandler.post {
                Log.d(TAG, "startWakeword() exec now")
                val params = HashMap<String, String>()
                params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = strWakeword
                var result = ttsCommand?.speak(strWakeword, TextToSpeech.QUEUE_FLUSH, params)
            }
        }
    }
    // status: wakeword -> command
    var countDownTimerCommand: CountDownTimer = object: CountDownTimer(COUNTDOWN_DELAY_COMMAND,COUNTDOWN_TICK) {
        override fun onTick(progess: Long) {
            notifyCallback("", (progess/1000).toInt())
        }
        override fun onFinish() {
            uiHandler.post{
                if (messsages.size <= commandIndex) {
                    commandIndex = 0
                }
                Log.d(TAG, "startCommand($commandIndex) exec now")

                val command = getCurrentCommand()

                val params = HashMap<String, String>()
                params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = command
                var result = ttsCommand?.speak(command, TextToSpeech.QUEUE_FLUSH, params)
                setCommandExecuted()
            }
        }
    }
    // status: command -> idle
    var countDownTimerIdle: CountDownTimer = object: CountDownTimer(COUNTDOWN_DELAY_IDLE,COUNTDOWN_TICK) {
        override fun onTick(progess: Long) {
            notifyCallback("", (progess/1000).toInt())
        }
        override fun onFinish() {
            commandHandler.sendEmptyMessage(1)
        }
    }
}