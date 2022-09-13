package me.shiki.djipanodemo

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex
import com.secneo.sdk.Helper

/**
 *
 * @author shiki
 * @date 2022/9/8
 *
 */
class App : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        Helper.install(this)
    }
}