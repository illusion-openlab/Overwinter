package tech.illusion.overwinter

import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import tech.illusion.overwinter.content.HomePage

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultWindowContainer {
            PicoTheme(colorScheme = tech.illusion.overwinter.content.OverwinterColorScheme) {
                HomePage()
            }
        }
    }
