package com.bharatbussim.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.bharatbussim.game.ui.BusDashboardHudOverlay
import com.bharatbussim.game.ui.Thrissur3DRoadViewport
import com.bharatbussim.game.ui.theme.BharatBusSimTheme
import com.bharatbussim.game.viewmodel.ThrissurSimViewModel

class MainActivity : ComponentActivity() {

    private val simViewModel: ThrissurSimViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        simViewModel.loadRoadsIfNeeded(applicationContext)

        setContent {
            BharatBusSimTheme {
                val uiStateState = simViewModel.uiState.collectAsState()

                LaunchedEffect(Unit) {
                    var lastNanos = withFrameNanos { it }
                    while (true) {
                        val now = withFrameNanos { it }
                        val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.08f)
                        lastNanos = now
                        if (dt > 0.036f) {
                            val halfDt = dt * 0.5f
                            simViewModel.stepPhysics(halfDt)
                            simViewModel.stepPhysics(halfDt)
                        } else {
                            simViewModel.stepPhysics(dt)
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Thrissur3DRoadViewport(
                        state = uiStateState.value,
                        onOrbitDrag = { dYaw, dPitch -> simViewModel.orbitCamera(dYaw, dPitch) }
                    )
                    BusDashboardHudOverlay(
                        stateProvider = { uiStateState.value },
                        viewModel = simViewModel
                    )
                }
            }
        }
    }
}
