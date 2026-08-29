package com.glucarb.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.glucarb.data.entity.FoodItem
import com.glucarb.domain.CarbMath
import java.io.File
import kotlin.math.abs

/** Deterministic tile colour for items without a photo. */
fun initialColor(seed: String): Color {
    val palette = listOf(
        Color(0xFF3D4A63), Color(0xFF4A3D63), Color(0xFF63523D),
        Color(0xFF3D6357), Color(0xFF633D48), Color(0xFF44633D),
    )
    return palette[abs(seed.hashCode()) % palette.size]
}

fun FoodItem.badgeText(): String =
    if (hasPortions) {
        // Carbs per portion, not the portion's weight: the weight was only ever an input
        // used to derive this number, and repeating it costs a scarce line of tile space.
        "${CarbMath.formatCarbs(CarbMath.carbsPerPortion(this) ?: 0.0)} g/$portionName"
    } else {
        "${CarbMath.format(carbsPer100)}/100${unit.label}"
    }

@Composable
fun ItemAvatar(
    photoPath: String?,
    emoji: String?,
    name: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 28,
) {
    val file = photoPath?.let { File(it) }?.takeIf { it.exists() }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (file != null) {
            AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(initialColor(name)),
                contentAlignment = Alignment.Center,
            ) {
                if (!emoji.isNullOrBlank()) {
                    Text(
                        text = emoji,
                        // Emoji sit smaller inside their em box than a capital letter does,
                        // so they need a nudge to fill the tile the same way.
                        fontSize = (fontSize * 1.25f).sp,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = name.trim().take(1).uppercase(),
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

/**
 * A tile carries only the picture and the name. The carb ratio was noise at this size:
 * it is never the thing being chosen between, and the quantity sheet shows it a tap
 * later anyway. It stays in the accessibility label, which costs no space.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoodTile(
    item: FoodItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = "${item.name}, ${item.badgeText()}" },
    ) {
        ItemAvatar(item.photoPath, item.emoji, item.name, Modifier.fillMaxSize())
        Text(
            text = item.name,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
                .padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 5.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoodRow(
    item: FoodItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .semantics { contentDescription = "${item.name}, ${item.badgeText()}" },
    ) {
        ItemAvatar(
            item.photoPath,
            item.emoji,
            item.name,
            Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)),
            fontSize = 18,
        )
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                item.badgeText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("+", color = MaterialTheme.colorScheme.primary, fontSize = 22.sp)
    }
}
