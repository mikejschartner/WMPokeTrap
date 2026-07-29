package com.whiskeymike.wmpoketrap

import android.app.Application
import com.whiskeymike.wmpoketrap.bot.BotEngine

class WmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        BotEngine.get(this)
    }
}
