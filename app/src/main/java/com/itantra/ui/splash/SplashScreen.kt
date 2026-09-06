package com.itantra.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.data.repository.SessionRepository
import com.itantra.ui.theme.DarkBackground
import com.itantra.ui.theme.SafetyOrange
import com.itantra.ui.theme.TextPrimary
import com.itantra.ui.theme.TextSecondary

/**
 * Static splash screen displayed during AI model loading.
 *
 * This screen is deliberately NON-ANIMATED (no Compose infinite transitions,
 * no Lottie, no animated gradients) to keep Android's HWUI renderer dormant.
 * When HWUI is dormant, ONNX Runtime can safely create its internal C++ thread
 * pool without corrupting the shared pthread mutexes, avoiding the SIGABRT crash.
 *
 * The progress indicator uses a determinate LinearProgressIndicator which does
 * NOT trigger continuous recomposition/HWUI frame rendering — it only redraws
 * when the progress value actually changes (twice: 0→0.5 and 0.5→1.0).
 */
@Composable
fun SplashScreen(
    sessionRepository: SessionRepository,
    onModelsReady: () -> Unit
) {
    var statusText by remember { mutableStateOf("Preparing AI engines...") }
    var progress by remember { mutableFloatStateOf(0f) }
    var isComplete by remember { mutableStateOf(false) }

    // Initialize models on composition — runs once, on the main thread
    LaunchedEffect(Unit) {
        try {
            statusText = "Loading speech recognition model..."
            progress = 0.15f
            sessionRepository.sttEngine.initialize()
            progress = 0.5f

            statusText = "Loading text-to-speech models..."
            sessionRepository.ttsEngine.initialize()
            progress = 1.0f

            statusText = "Ready!"
            isComplete = true
        } catch (e: Exception) {
            statusText = "Model loading failed: ${e.message}"
            // Still proceed — engines fall back to mock mode
            isComplete = true
        }
    }

    // Navigate once loading is complete
    LaunchedEffect(isComplete) {
        if (isComplete) {
            onModelsReady()
        }
    }

    // --- UI: Deliberately static, no Compose animations ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 48.dp)
        ) {
            // App icon placeholder — static, no animation
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = "iTantra",
                tint = SafetyOrange,
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "iTantra",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Emergency Mesh Radio",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Determinate progress bar — only redraws on value change, not continuously
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = SafetyOrange,
                trackColor = SafetyOrange.copy(alpha = 0.15f),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = statusText,
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
