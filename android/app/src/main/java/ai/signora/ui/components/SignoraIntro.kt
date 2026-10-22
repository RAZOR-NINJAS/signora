package ai.signora.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Expressive cold-start intro: the mark springs in, the wordmark lifts into place,
 * then the whole scene hands off to the app UI.
 * Tapping anywhere skips it.
 */
@Composable
fun SignoraIntro(onFinished: () -> Unit) {
    val scheme = MaterialTheme.colorScheme

    val markScale = remember { Animatable(0.45f) }
    val markAlpha = remember { Animatable(0f) }
    val markRotate = remember { Animatable(-22f) }
    val glowScale = remember { Animatable(0.6f) }
    val glowAlpha = remember { Animatable(0f) }
    val wordAlpha = remember { Animatable(0f) }
    val wordOffset = remember { Animatable(22f) }
    val tagAlpha = remember { Animatable(0f) }

    var finished by remember { mutableStateOf(false) }

    fun finishNow() {
        if (!finished) {
            finished = true
            onFinished()
        }
    }

    LaunchedEffect(Unit) {
        launch {
            glowAlpha.animateTo(0.55f, tween(420))
            glowScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessVeryLow))
        }
        launch {
            markAlpha.animateTo(1f, tween(180))
            markScale.animateTo(1f, spring(dampingRatio = 0.40f, stiffness = Spring.StiffnessLow))
            markRotate.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
        }
        delay(260)
        launch {
            wordAlpha.animateTo(1f, tween(240))
            wordOffset.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
        }
        delay(180)
        tagAlpha.animateTo(1f, tween(260))
        delay(1150)
        finishNow()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        scheme.primaryContainer.copy(alpha = 0.55f),
                        scheme.background,
                        scheme.background
                    ),
                    radius = 1400f
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { finishNow() },
        contentAlignment = Alignment.Center
    ) {
        // Soft halo behind the mark
        Box(
            modifier = Modifier
                .size(260.dp)
                .graphicsLayer {
                    scaleX = glowScale.value
                    scaleY = glowScale.value
                    alpha = glowAlpha.value
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            scheme.primary.copy(alpha = 0.35f),
                            scheme.tertiary.copy(alpha = 0.12f),
                            scheme.background.copy(alpha = 0f)
                        )
                    ),
                    shape = RoundedCornerShape(130.dp)
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = markScale.value
                        scaleY = markScale.value
                        rotationZ = markRotate.value
                        alpha = markAlpha.value
                    }
                    .shadow(28.dp, RoundedCornerShape(34.dp))
                    .size(108.dp)
                    .clip(RoundedCornerShape(34.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(scheme.primary, scheme.tertiary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "S",
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Black,
                    color = scheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "SIGNORA",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 10.sp,
                color = scheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    alpha = wordAlpha.value
                    translationY = wordOffset.value
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Sign  ·  Speak  ·  Connect",
                style = MaterialTheme.typography.bodyMedium,
                letterSpacing = 2.sp,
                color = scheme.onSurfaceVariant,
                modifier = Modifier
                    .graphicsLayer { alpha = tagAlpha.value }
                    .padding(horizontal = 24.dp)
            )
        }
    }
}
