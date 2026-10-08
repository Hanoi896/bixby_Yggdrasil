package com.huginmunin.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huginmunin.app.munin.db.LogEntity
import com.huginmunin.app.ui.theme.*
import com.huginmunin.app.viewmodel.MainViewModel

@Composable
fun AsgardDashboard(
    viewModel: MainViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val recentMemories by viewModel.recentLogs.collectAsState(initial = emptyList())

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(NorseBlack, NorseGray)
                )
            )
            .padding(16.dp)
    ) {
        // Odin's Throne Header
        Text(
            text = "⚡ Asgard Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            color = GoldenPrimary,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "The All-Father watches over all",
            style = MaterialTheme.typography.bodyMedium,
            color = RuneSilver
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // The Six Realms (Quick Actions)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RealmCard(
                icon = Icons.Default.Psychology,
                title = "Hugin",
                subtitle = "Thought",
                color = MysticPurple
            )
            RealmCard(
                icon = Icons.Default.Storage,
                title = "Munin",
                subtitle = "Memory",
                color = IceBlue
            )
            RealmCard(
                icon = Icons.Default.Bolt,
                title = "Gungnir",
                subtitle = "Execute",
                color = FireRed
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Munin's Memory Stream
        Text(
            text = "🦅 Munin's Recent Memories",
            style = MaterialTheme.typography.titleMedium,
            color = IceBlue,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn {
            if (recentMemories.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = NorseGrayLight.copy(alpha = 0.4f)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🦅 Munin is ready. No memories yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = IceBlue
                            )
                        }
                    }
                }
            } else {
                items(recentMemories.take(10)) { memory ->
                    MemoryCard(memory)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
@Composable
fun RealmCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color
) {
    Card(
        modifier = Modifier
            .size(100.dp, 120.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = NorseGrayLight.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = RuneSilver
            )
        }
    }
}

@Composable
fun MemoryCard(memory: LogEntity) {
    val isCrash = memory.tag == "CRASH" || memory.level == "CRITICAL"
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCrash) {
                FireRed.copy(alpha = 0.2f)
            } else {
                NorseGrayLight.copy(alpha = 0.4f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Level indicator
            Box(
                modifier = Modifier
                    .size(if (isCrash) 12.dp else 8.dp)
                    .clip(RoundedCornerShape(if (isCrash) 6.dp else 4.dp))
                    .background(
                        when {
                            isCrash -> FireRed
                            memory.level == "ERROR" -> FireRed
                            memory.level == "WARN" -> FireOrange
                            memory.level == "INFO" -> IceBlue
                            else -> RuneSilver
                        }
                    )
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isCrash) "⚠️ [${memory.tag}]" else "[${memory.tag}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCrash) FireRed else GoldenPrimary,
                        fontWeight = if (isCrash) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = formatTimestamp(memory.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = RuneSilver
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = memory.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCrash) Color.White else RuneSilver,
                    maxLines = if (isCrash) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        else -> "${diff / 86400_000}d ago"
    }
}
