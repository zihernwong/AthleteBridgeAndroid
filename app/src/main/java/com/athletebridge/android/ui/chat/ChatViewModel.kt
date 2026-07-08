package com.athletebridge.android.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.ChatItem
import com.athletebridge.android.data.model.ChatMessage
import com.athletebridge.android.data.repository.AuthRepository
import com.athletebridge.android.data.repository.ChatRepository
import com.athletebridge.android.data.repository.CoachRepository
import com.athletebridge.android.data.repository.ClientRepository
import com.athletebridge.android.data.repository.NotificationRepository
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

private const val TAG = "ChatViewModel"

data class ChatsListUiState(
    val chats: List<ChatItem> = emptyList(),
    val isLoading: Boolean = true
)

data class ChatScreenUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = true,
    val otherUserName: String = "",
    val otherIsTyping: Boolean = false,
    val isSendingImage: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val coachRepository: CoachRepository,
    private val clientRepository: ClientRepository,
    private val notificationRepository: NotificationRepository,
    private val photoRepository: com.athletebridge.android.data.repository.PhotoRepository
) : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()
    val currentUid: String? get() = authRepository.currentUser?.uid

    private val _chatsState = MutableStateFlow(ChatsListUiState())
    val chatsState: StateFlow<ChatsListUiState> = _chatsState.asStateFlow()

    /** Number of conversations with unread messages. */
    val unreadCount: StateFlow<Int> = _chatsState
        .map { state -> state.chats.count { it.unreadCount > 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _chatScreenState = MutableStateFlow(ChatScreenUiState())
    val chatScreenState: StateFlow<ChatScreenUiState> = _chatScreenState.asStateFlow()

    fun observeChats() {
        viewModelScope.launch {
            // Wait for auth to be ready rather than bailing out — uid may be null
            // briefly at startup even though the user is logged in.
            val uid = currentUid ?: run {
                Log.d(TAG, "observeChats: uid null, waiting for auth state...")
                authRepository.observeAuthState().filterNotNull().first().uid
            }
            Log.d(TAG, "observeChats: starting for uid=$uid")
            _chatsState.value = _chatsState.value.copy(isLoading = true)
            try {
                chatRepository.observeChatsForUser(uid).collect { chats ->
                    Log.d(TAG, "observeChats: received ${chats.size} chats")
                    val resolvedChats = chats.map { chat ->
                        val otherUid = chat.participantIds.firstOrNull { it != uid } ?: ""
                        val name = resolveUserNameCached(otherUid)
                        chat.copy(participantNames = mapOf(otherUid to name))
                    }
                    _chatsState.value = ChatsListUiState(chats = resolvedChats, isLoading = false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "observeChats error", e)
                _chatsState.value = ChatsListUiState(isLoading = false)
            }
        }
    }

    fun observeChat(chatId: String) {
        Log.d(TAG, "observeChat called, chatId=$chatId, uid=$currentUid")
        // Mark chat as read immediately when opened
        currentUid?.let { uid ->
            viewModelScope.launch { chatRepository.markChatRead(chatId, uid) }
        }
        viewModelScope.launch {
            _chatScreenState.value = ChatScreenUiState(isLoading = true)

            // Resolve other user name — try cache first (instant), fall back to network
            try {
                val chatDoc = try {
                    firestore.collection("chats").document(chatId).get(Source.CACHE).await()
                } catch (cacheEx: Exception) {
                    Log.d(TAG, "observeChat: chat doc not cached, fetching from network")
                    firestore.collection("chats").document(chatId).get().await()
                }
                Log.d(TAG, "observeChat: chatDoc exists=${chatDoc.exists()}, fields=${chatDoc.data?.keys}")
                val data = chatDoc.data ?: emptyMap()

                val allUids = mutableSetOf<String>()
                @Suppress("UNCHECKED_CAST")
                (data["participants"] as? List<String>)?.let { allUids.addAll(it) }
                @Suppress("UNCHECKED_CAST")
                (data["participantRefs"] as? List<*>)?.forEach { ref ->
                    (ref as? com.google.firebase.firestore.DocumentReference)?.id?.let { allUids.add(it) }
                }
                Log.d(TAG, "observeChat: allUids=$allUids, currentUid=$currentUid")

                val otherUid = allUids.firstOrNull { it != currentUid } ?: ""
                val otherName = resolveUserNameCached(otherUid)
                _chatScreenState.value = _chatScreenState.value.copy(otherUserName = otherName)

                // Typing indicator for the other participant
                if (otherUid.isNotEmpty()) {
                    typingListenJob?.cancel()
                    typingListenJob = viewModelScope.launch {
                        chatRepository.observeTyping(chatId, otherUid).collect { typing ->
                            _chatScreenState.value = _chatScreenState.value.copy(otherIsTyping = typing)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "observeChat: error resolving chat doc (possibly not cached yet)", e)
            }

            Log.d(TAG, "observeChat: starting message collection for chatId=$chatId")
            try {
                chatRepository.observeMessages(chatId).collect { messages ->
                    Log.d(TAG, "observeChat: received ${messages.size} messages")
                    _chatScreenState.value = _chatScreenState.value.copy(
                        messages = messages,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "observeChat: collect threw exception", e)
                _chatScreenState.value = _chatScreenState.value.copy(isLoading = false)
            }
            Log.d(TAG, "observeChat: coroutine ended for chatId=$chatId")
        }
    }

    fun sendMessage(chatId: String, text: String) {
        val uid = currentUid ?: return
        stopTyping(chatId)
        viewModelScope.launch {
            chatRepository.sendMessage(chatId, uid, text, "")
            // Notification is handled by the backend Cloud Function triggered on new messages,
            // matching iOS behavior (iOS ChatView does not write to pendingNotifications).
        }
    }

    fun sendImage(chatId: String, imageBytes: ByteArray) {
        val uid = currentUid ?: return
        viewModelScope.launch {
            _chatScreenState.value = _chatScreenState.value.copy(isSendingImage = true)
            val filename = "${System.currentTimeMillis()}.jpg"
            photoRepository.uploadChatImage(chatId, imageBytes, filename).onSuccess { url ->
                chatRepository.sendImageMessage(chatId, uid, url)
            }
            _chatScreenState.value = _chatScreenState.value.copy(isSendingImage = false)
        }
    }

    // ── Typing state (mirrors iOS: write true on keystroke, auto-clear after a pause) ──

    private var typingListenJob: kotlinx.coroutines.Job? = null
    private var typingClearJob: kotlinx.coroutines.Job? = null
    private var iAmTyping = false

    fun onTyping(chatId: String) {
        val uid = currentUid ?: return
        if (!iAmTyping) {
            iAmTyping = true
            viewModelScope.launch { chatRepository.setTyping(chatId, uid, true) }
        }
        typingClearJob?.cancel()
        typingClearJob = viewModelScope.launch {
            kotlinx.coroutines.delay(3_000)
            iAmTyping = false
            chatRepository.setTyping(chatId, uid, false)
        }
    }

    fun stopTyping(chatId: String) {
        typingClearJob?.cancel()
        val uid = currentUid ?: return
        if (iAmTyping) {
            iAmTyping = false
            viewModelScope.launch { chatRepository.setTyping(chatId, uid, false) }
        }
    }

    suspend fun createOrGetChat(otherUid: String): String {
        val uid = currentUid ?: return ""
        return chatRepository.createOrGetChat(uid, otherUid)
    }

    private suspend fun resolveUserName(uid: String): String {
        // Try coach first, then client (network fetch — use only where hanging is acceptable)
        val coach = coachRepository.fetchCoach(uid)
        if (coach != null && coach.name.isNotBlank()) return coach.name
        val client = clientRepository.fetchClient(uid)
        if (client != null && client.name.isNotBlank()) return client.name
        return "User"
    }

    // Try cache first; fall back to network on cache miss
    private suspend fun resolveUserNameCached(uid: String): String {
        if (uid.isBlank()) return "User"
        val coach = coachRepository.fetchCoach(uid, Source.CACHE)
            ?: coachRepository.fetchCoach(uid)
        if (coach != null && coach.name.isNotBlank()) return coach.name
        val client = clientRepository.fetchClient(uid, Source.CACHE)
            ?: clientRepository.fetchClient(uid)
        if (client != null && client.name.isNotBlank()) return client.name
        return "User"
    }
}
