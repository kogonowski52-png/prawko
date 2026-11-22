package pl.prawko.app

import android.app.Application
import pl.prawko.app.data.AppContainer

class PrawkoApplication : Application() {
    val container by lazy { AppContainer(applicationContext) }
}
