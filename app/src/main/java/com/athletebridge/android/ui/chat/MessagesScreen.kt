package com.athletebridge.android.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.ui.shared.AvatarView
import com.athletebridge.android.ui.theme.LogoBlue
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    onChatSelected: (String) -> Unit = {},
    onNewMessage: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.chatsState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.observeChats()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Messages") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewMessage) {
                Icon(Icons.Default.Create, contentDescription = "New Message")
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.chats.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No conversations yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Start a conversation from a coach's profile", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(uiState.chats, key = { it.id }) { chat ->
                    val currentUid = viewModel.currentUid ?: ""
                    val otherUid = chat.participantIds.firstOrNull { it != currentUid } ?: ""
                    val otherName = chat.participantNames[otherUid] ?: "User"
                    val isUnread = chat.unreadCount > 0

                    ListItem(
                        modifier = Modifier.clickable { onChatSelected(chat.id) },
                        leadingContent = {
                            AvatarView(url = null, name = otherName, size = 48.dp)
                        },
                        headlineContent = {
                            Text(
                                otherName,
                                fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold
                            )
                        },
                        supportingContent = {
                            chat.lastMessage?.let { msg ->
                                Text(
                                    msg,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isUnread) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        trailingContent = {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                chat.lastMessageTime?.let { time ->
                                    Text(
                                        formatChatTime(time),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isUnread) LogoBlue
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                if (isUnread) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(LogoBlue)
                                            .align(Alignment.End)
                                    )
                                }
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

private fun formatChatTime(date: Date): String {
    val now = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { time = date }
    return if (now.get(Calendar.DATE) == msgCal.get(Calendar.DATE) &&
        now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
    } else {
        SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
    }
}
