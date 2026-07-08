package com.athletebridge.android.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.athletebridge.android.ui.theme.LogoBlue
import com.athletebridge.android.ui.theme.LogoGreen

@Composable
fun AvatarView(
    url: String?,
    name: String,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
    phoneVerified: Boolean = false
) {
    val badgeSize = (size.value * 0.32f).coerceAtLeast(14f).dp

    Box(modifier = modifier.size(size)) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = "$name avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
            )
        } else {
            // Fallback: show initials or icon
            val initials = name.split(" ")
                .take(2)
                .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                .joinToString("")

            if (initials.isNotEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                        .background(LogoBlue.copy(alpha = 0.15f))
                ) {
                    Text(
                        text = initials,
                        color = LogoBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value * 0.4f).sp
                    )
                }
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "No photo",
                        modifier = Modifier.size(size * 0.6f),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Verified badge overlay at bottom-right
        if (phoneVerified) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(badgeSize)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(LogoGreen)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Phone verified",
                    tint = Color.White,
                    modifier = Modifier.size(badgeSize * 0.65f)
                )
            }
        }
    }
}
