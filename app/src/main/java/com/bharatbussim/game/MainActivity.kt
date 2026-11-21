package com.bharatbussim.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bharatbussim.game.ui.BusDashboardHudOverlay
import com.bharatbussim.game.ui.FuelStationRefuelOverlay
import com.bharatbussim.game.ui.Thrissur3DRoadViewport
import com.bharatbussim.game.ui.theme.BharatBusSimTheme
import com.bharatbussim.game.ui.theme.PremiumFonts
import com.bharatbussim.game.viewmodel.SimUiState
import com.bharatbussim.game.viewmodel.ThrissurSimViewModel

class MainActivity : ComponentActivity() {

    private val simViewModel: ThrissurSimViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PremiumFonts.init(applicationContext)
        simViewModel.loadRoadsIfNeeded(applicationContext)

        setContent {
            BharatBusSimTheme {
                val uiStateState = simViewModel.uiState.collectAsState()
                val stateProvider: () -> SimUiState = remember(uiStateState) {
                    { uiStateState.value }
                }
                val glStateProvider: () -> SimUiState = remember(simViewModel) {
                    { simViewModel.uiState.value }
                }

                LaunchedEffect(Unit) {
                    var lastNanos = withFrameNanos { it }
                    while (true) {
                        val now = withFrameNanos { it }
                        val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.06f)
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
                        stateProvider = stateProvider,
                        glStateProvider = glStateProvider,
                        onOrbitDrag = { dYaw, dPitch -> simViewModel.orbitCamera(dYaw, dPitch) },
                        onWorldReady = { simViewModel.markWorldGpuReady() }
                    )
                    val isRefuelCutscene by remember {
                        derivedStateOf { stateProvider().refuel.isCutsceneActive }
                    }
                    AnimatedVisibility(
                        visible = !isRefuelCutscene,
                        exit = fadeOut(animationSpec = tween(durationMillis = 220))
                    ) {
                        BusDashboardHudOverlay(
                            stateProvider = stateProvider,
                            viewModel = simViewModel
                        )
                    }
                    FuelStationRefuelOverlay(
                        stateProvider = stateProvider,
                        viewModel = simViewModel
                    )
                    WorldLoadingOverlay(stateProvider = stateProvider)
                }
            }
        }
    }
}

@Composable
private fun WorldLoadingOverlay(
    stateProvider: () -> SimUiState
) {
    val isWorldReady by remember { derivedStateOf { stateProvider().isWorldReady } }
    val loadingStageLabel by remember { derivedStateOf { stateProvider().loadingStageLabel } }

    AnimatedVisibility(
        visible = !isWorldReady,
        exit = fadeOut(animationSpec = tween(durationMillis = 360)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E1722)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF172536),
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(38.dp),
                        color = Color(0xFFFFC107),
                        strokeWidth = 3.5.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Bharat Bus Simulator • Thrissur",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = loadingStageLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB5C7D9)
                    )
                }
            }
        }
    }
}
