package com.carbtrack.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.carbtrack.data.entity.FoodItem
import com.carbtrack.domain.CarbMath
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
        "1 $portionName = ${CarbMath.format(portionSize ?: 0.0)}${unit.label}"
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

@Composable
fun FoodTile(item: FoodItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${item.name}, ${item.badgeText()}" },
    ) {
        ItemAvatar(item.photoPath, item.emoji, item.name, Modifier.fillMaxSize())
        Text(
            text = item.badgeText(),
            fontSize = 8.sp,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
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

@Composable
fun FoodRow(item: FoodItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
