package com.glucarb.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import com.glucarb.R

private val CardColor = Color(0xFFEEF1F7)
private val CardText = Color(0xFF12141A)
private val CardSubtext = Color(0xFF3A3F4B)
private val CardAccent = Color(0xFF1F8A50)
private val CardMuted = Color(0xFF6B7280)

/** A one-time hint that sits in the layout and goes away for good on "Got it". */
@Composable
fun TipCard(text: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardColor)
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 2.dp),
    ) {
        Text(text, color = CardText, fontSize = 13.sp, lineHeight = 18.sp)
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.action_got_it), color = CardAccent, fontWeight = FontWeight.Bold)
        }
    }
}

data class TourStep(@StringRes val title: Int, @StringRes val body: Int)

val TOUR_STEPS = listOf(
    TourStep(R.string.tour_catalog_title, R.string.tour_catalog_body),
    TourStep(R.string.tour_photo_title, R.string.tour_photo_body),
    TourStep(R.string.tour_history_title, R.string.tour_history_body),
)

/**
 * Dims the screen except for [target] and shows one coach card. Taps outside the card
 * are swallowed so the tour cannot be half-used by accident; Skip is always there.
 *
 * [target] is in root coordinates. The card sits above a target in the lower half of the
 * screen and below one in the upper half.
 */
@Composable
fun TourOverlay(
    step: Int,
    target: Rect?,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val content = TOUR_STEPS[step]
    val last = step == TOUR_STEPS.lastIndex
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = with(density) { maxHeight.toPx() }
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            drawRect(Color.Black.copy(alpha = 0.78f))
            if (target != null) {
                val pad = 8.dp.toPx()
                val topLeft = Offset(target.left - pad, target.top - pad)
                val size = Size(target.width + 2 * pad, target.height + 2 * pad)
                val corner = CornerRadius(20.dp.toPx())
                drawRoundRect(Color.Black, topLeft, size, corner, blendMode = BlendMode.Clear)
                drawRoundRect(
                    Color(0xFF58D38C), topLeft, size, corner,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        val inLowerHalf = target == null || target.center.y > heightPx / 2
        val cardModifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .let { base ->
                if (target == null) {
                    base.align(Alignment.BottomCenter).padding(bottom = 120.dp)
                } else if (inLowerHalf) {
                    base.align(Alignment.BottomCenter)
                        .padding(bottom = with(density) { (heightPx - target.top).toDp() } + 20.dp)
                } else {
                    base.align(Alignment.TopCenter)
                        .padding(top = with(density) { target.bottom.toDp() } + 20.dp)
                }
            }
        Column(
            cardModifier
                .clip(RoundedCornerShape(18.dp))
                .background(CardColor)
                .padding(18.dp),
        ) {
            Text(stringResource(content.title), color = CardText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(content.body),
                color = CardSubtext,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) {
                Row(Modifier.weight(1f)) {
                    TOUR_STEPS.indices.forEach { i ->
                        Box(
                            Modifier
                                .padding(end = 6.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (i == step) CardAccent else Color(0xFFC5CAD3)),
                        )
                    }
                }
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.action_skip), color = CardMuted, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onNext) {
                    Text(stringResource(if (last) R.string.action_got_it else R.string.action_next), color = CardAccent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
