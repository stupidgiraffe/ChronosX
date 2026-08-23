package dev.chronosx

import android.app.Application

class ChronosXApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
