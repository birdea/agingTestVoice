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
import java.lang.Exception
import java.util.*

class AgingTestManager (val context: Context) {

    val TAG = "TextToSpeechLooper"

    val COUNTDOWN_TICK = 1000L

    val COUNTDOWN_DELAY_WAKEWORD = 2500L
    val COUNTDOWN_DELAY_COMMAND = 2500L
    val COUNTDOWN_DELAY_IDLE = 20000L

    var ttsCommand:TextToSpeech? = null

    val timeTestRecord = TimeRecord()

    var commandIndex = 0
    var commandExectued = 0
    var commandHandler = CommandHandler(this)

    var uiHandler = Handler(Looper.getMainLooper())

    enum class Commands(val message: String, val locale: Locale, val wakeword: String) {

        // for alexa
        E1("Play a music", Locale.ENGLISH, "Alexa"),
        //E2("What time is it now?", Locale.ENGLISH, "Alexa"),
        E3("Play a dance music.", Locale.ENGLISH, "Alexa"),
        //E4("Stop", Locale.ENGLISH, "Alexa"),
        E5("Play a classic music.", Locale.ENGLISH, "Alexa"),
        E6("Play a jazz music.", Locale.ENGLISH, "Alexa"),
        //E7("Set a alarm after 10 seconds.", Locale.ENGLISH, "Alexa"),
        //E8("Stop", Locale.ENGLISH, "Alexa"),

        // for nugu(aria)
        //K1("현재 시간을 알려주세요", Locale.KOREA, "아리야"),
        //K2("현재 날씨를 알려주세요..", Locale.KOREA, "아리야"),
        ;

        companion object {
            fun get(index: Int): Commands{
                return try {
                    values()[index]
                } catch (e: Exception) {
                    e.printStackTrace()
                    E1
                }
            }
        }
    }

    var currentCommand: Commands = Commands.E1
        get() = Commands.get(commandIndex)

    fun getSummaryMessage(): String {
        var sb = StringBuilder()
        sb.append("-test.command.set\n")
        var index = 0
        for (item in Commands.values()) {
            sb.append("[${index++}] $item, ${item.message}\n")
        }
        return sb.toString()
    }

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

    fun start() {

        timeTestRecord.onStarted()

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

        timeTestRecord.onStopped()

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

                if (status == currentCommand.wakeword) {
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


    class CommandHandler(val controller: AgingTestManager) : Handler() {
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

    private fun startWakeword() {
        countDownTimerWakeword.start()

    }

    private fun startCommand() {
        countDownTimerCommand.start()
    }

    private fun setupCommandExecute() {

        commandIndex++
        commandExectued++
        if (Commands.values().size <= commandIndex) {
            commandIndex = 0
        }

        Log.d(TAG, "setupCommandExecute(${commandIndex})")
    }

    // status: idle -> wakeword
    var countDownTimerWakeword: CountDownTimer = object: CountDownTimer(COUNTDOWN_DELAY_WAKEWORD,COUNTDOWN_TICK) {
        override fun onTick(progess: Long) {
            notifyCallback("", (progess/1000).toInt())
        }
        override fun onFinish() {
            uiHandler.post {
                setupCommandExecute()
                Log.d(TAG, "startWakeword($currentCommand) exec now")
                //
                ttsCommand?.setLanguage(currentCommand.locale)
                //
                val params = HashMap<String, String>()
                params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = currentCommand.wakeword
                var result = ttsCommand?.speak(currentCommand.wakeword, TextToSpeech.QUEUE_FLUSH, params)
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
                if (Commands.values().size <= commandIndex) {
                    commandIndex = 0
                }
                Log.d(TAG, "startCommand($commandIndex) exec now")

                val command = currentCommand

                val params = HashMap<String, String>()
                params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = command.message
                var result = ttsCommand?.speak(command.message, TextToSpeech.QUEUE_FLUSH, params)
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