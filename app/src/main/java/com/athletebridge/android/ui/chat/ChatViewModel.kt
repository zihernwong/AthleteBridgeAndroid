package com.athletebridge.android.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.ChatItem
import com.athletebridge.android.data.model.ChatMessage
import com.athletebridge.android.data.repository.AuthRepository
import com.athletebridge.android.data.repository.ChatRepository
import com.athletebridge.android.data.repository.CoachRepository
import com.athletebridge.android.data.repository.ClientRepository
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class ChatsListUiState(
    val chats: List<ChatItem> = emptyList(),
    val isLoading: Boolean = true
)

data class ChatScreenUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = true,
    val otherUserName: String = ""
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val coachRepository: CoachRepository,
    private val clientRepository: ClientRepository
) : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()
    val currentUid: String? get() = authRepository.currentUser?.uid

    private val _chatsState = MutableStateFlow(ChatsListUiState())
    val chatsState: StateFlow<ChatsListUiState> = _chatsState.asStateFlow()

    private val _chatScreenState = MutableStateFlow(ChatScreenUiState())
    val chatScreenState: StateFlow<ChatScreenUiState> = _chatScreenState.asStateFlow()

    fun loadChats() {
        val uid = currentUid ?: return
        viewModelScope.launch {
            _chatsState.value = _chatsState.value.copy(isLoading = true)
            try {
                // Use 3 parallel queries (coachRef, clientRef, participants) matching iOS
                val chats = chatRepository.fetchChatsForUser(uid)

                // Resolve names for all chat participants
                val resolvedChats = chats.map { chat ->
                    val otherUid = chat.participantIds.firstOrNull { it != uid } ?: ""
                    val name = resolveUserName(otherUid)
                    chat.copy(participantNames = mapOf(otherUid to name))
                }

                _chatsState.value = ChatsListUiState(chats = resolvedChats, isLoading = false)
            } catch (e: Exception) {
                _chatsState.value = ChatsListUiState(isLoading = false)
            }
        }
    }

    fun observeChat(chatId: String) {
        viewModelScope.launch {
            _chatScreenState.value = ChatScreenUiState(isLoading = true)

            // Resolve other user name from both participants and participantRefs
            try {
                val chatDoc = firestore.collection("chats").document(chatId).get().await()
                val data = chatDoc.data ?: emptyMap()

                // Collect UIDs from both participants (strings) and participantRefs (DocumentReferences)
                val allUids = mutableSetOf<String>()
                @Suppress("UNCHECKED_CAST")
                (data["participants"] as? List<String>)?.let { allUids.addAll(it) }
                @Suppress("UNCHECKED_CAST")
                (data["participantRefs"] as? List<*>)?.forEach { ref ->
                    (ref as? com.google.firebase.firestore.DocumentReference)?.id?.let { allUids.add(it) }
                }

                val otherUid = allUids.firstOrNull { it != currentUid } ?: ""
                val otherName = resolveUserName(otherUid)
                _chatScreenState.value = _chatScreenState.value.copy(otherUserName = otherName)
            } catch (_: Exception) {}

            chatRepository.observeMessages(chatId).collect { messages ->
                _chatScreenState.value = _chatScreenState.value.copy(
                    messages = messages,
                    isLoading = false
                )
            }
        }
    }

    fun sendMessage(chatId: String, text: String) {
        val uid = currentUid ?: return
        viewModelScope.launch {
            chatRepository.sendMessage(chatId, uid, text, "")
        }
    }

    suspend fun createOrGetChat(otherUid: String): String {
        val uid = currentUid ?: return ""
        return chatRepository.createOrGetChat(uid, otherUid)
    }

    private suspend fun resolveUserName(uid: String): String {
        // Try coach first, then client
        val coach = coachRepository.fetchCoach(uid)
        if (coach != null && coach.name.isNotBlank()) return coach.name
        val client = clientRepository.fetchClient(uid)
        if (client != null && client.name.isNotBlank()) return client.name
        return "User"
    }
}
