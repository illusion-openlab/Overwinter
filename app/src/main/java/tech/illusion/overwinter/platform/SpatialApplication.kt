package tech.illusion.overwinter.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import tech.illusion.overwinter.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
