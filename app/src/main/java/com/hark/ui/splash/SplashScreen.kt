package com.hark.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hark.ui.components.HarkMark
import com.hark.ui.theme.Hark
import com.hark.ui.theme.HarkType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val c = Hark.colors
    val curveProgress = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // 1. Trace the Hilbert curve filling from start to end (slightly slowed for elegance)
        launch {
            curveProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 1500,
                    easing = CubicBezierEasing(0.25f, 0.0f, 0.2f, 1.0f),
                ),
            )
        }

        // 2. Fade in the wordmark smoothly as the curve nears completion
        delay(900)
        launch {
            titleAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 500,
                    easing = FastOutSlowInEasing,
                ),
            )
        }

        // 3. Short pleasant hold on the completed mark
        delay(850)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.paper)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                onFinished()
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            HarkMark(
                modifier = Modifier.size(116.dp),
                progress = curveProgress.value,
                color = c.ink,
            )

            Spacer(Modifier.height(26.dp))

            Text(
                text = "Hark",
                style = HarkType.title.copy(fontSize = 32.sp),
                color = c.ink,
                modifier = Modifier.graphicsLayer {
                    alpha = titleAlpha.value
                },
            )
        }
    }
}
