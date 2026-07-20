import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { streamChat } from '@/api/sse'
import apiClient from '@/api/client'
import { useAuthStore } from '@/stores/auth'

export const useChatStore = defineStore('chat', () => {
  // ─── State ───────────────────────────────────────────────────────────────
  const sessions        = ref([])       // [{ id, title, lastMessage, createdAt }]
  const activeSessionId = ref(null)
  const messages        = ref([])       // [{ id, role, content, createdAt }]
  const streamingContent= ref('')
  const isStreaming     = ref(false)
  const abortController = ref(null)
  const availableModels = ref([])
  const selectedModelId = ref(null)
  const error           = ref(null)

  // ─── Getters ─────────────────────────────────────────────────────────────
  const activeSession = computed(() =>
    sessions.value.find(s => s.id === activeSessionId.value)
  )

  const selectedModel = computed(() =>
    availableModels.value.find(m => m.id === selectedModelId.value)
  )

  // ─── Session management ───────────────────────────────────────────────────
  function newSession() {
    const id = crypto.randomUUID()
    sessions.value.unshift({
      id,
      title: 'New Chat',
      lastMessage: '',
      createdAt: new Date().toISOString()
    })
    activeSessionId.value = id
    messages.value = []
    streamingContent.value = ''
    error.value = null
  }

  function selectSession(id) {
    activeSessionId.value = id
    messages.value = []
    // Load history for this session from API
    loadSessionMessages(id)
  }

  async function loadSessionMessages(sessionId) {
    try {
      const { data } = await apiClient.get('/api/v1/chat/history', {
        params: { page: 0, size: 50 }
      })
      // Filter by sessionId and reverse so oldest is first (chronological order)
      const sessionLogs = data.filter(log => log.sessionId === sessionId).reverse()
      messages.value = sessionLogs.flatMap(log => [
        { id: log.id + '_user', role: 'user',      content: log.prompt,   createdAt: log.createdAt },
        { id: log.id + '_asst', role: 'assistant',  content: log.response, createdAt: log.createdAt }
      ])
    } catch (e) {
      console.error('Failed to load chat history', e)
    }
  }

  function getHiddenSessionIds() {
    const authStore = useAuthStore()
    const key = `ppgpt_hidden_sessions_${authStore.user?.username || 'default'}`
    try {
      return new Set(JSON.parse(localStorage.getItem(key) || '[]'))
    } catch (e) {
      return new Set()
    }
  }

  function saveHiddenSessionIds(set) {
    const authStore = useAuthStore()
    const key = `ppgpt_hidden_sessions_${authStore.user?.username || 'default'}`
    localStorage.setItem(key, JSON.stringify(Array.from(set)))
  }

  function deleteSession(sessionId) {
    const hidden = getHiddenSessionIds()
    hidden.add(sessionId)
    saveHiddenSessionIds(hidden)

    sessions.value = sessions.value.filter(s => s.id !== sessionId)

    if (activeSessionId.value === sessionId) {
      activeSessionId.value = null
      messages.value = []
      streamingContent.value = ''
    }
  }

  async function loadHistory() {
    try {
      const { data } = await apiClient.get('/api/v1/chat/history', {
        params: { page: 0, size: 100 }
      })
      const hidden = getHiddenSessionIds()
      // Group by session
      const grouped = {}
      data.forEach(log => {
        if (!hidden.has(log.sessionId) && !grouped[log.sessionId]) {
          grouped[log.sessionId] = {
            id: log.sessionId,
            title: log.prompt.slice(0, 40) + (log.prompt.length > 40 ? '…' : ''),
            lastMessage: log.createdAt,
            createdAt: log.createdAt
          }
        }
      })
      sessions.value = Object.values(grouped).sort(
        (a, b) => new Date(b.createdAt) - new Date(a.createdAt)
      )
    } catch (e) {
      console.error('Failed to load history', e)
    }
  }

  // ─── Streaming ───────────────────────────────────────────────────────────

  /**
   * Start a streaming chat completion.
   * Uses native fetch + ReadableStream so AbortController.abort()
   * severs the connection and triggers backend doFinally/doOnCancel.
   */
  async function sendMessage(text, images = []) {
    if (!selectedModelId.value) {
      error.value = 'Please select a model first'
      return
    }
    if (!activeSessionId.value) newSession()

    const authStore = useAuthStore()
    error.value = null

    // Add user message immediately
    const userMsg = {
      id: crypto.randomUUID(),
      role: 'user',
      content: text,
      images: images || [],
      createdAt: new Date().toISOString()
    }
    messages.value.push(userMsg)

    // Update session title if it's the first message
    const session = sessions.value.find(s => s.id === activeSessionId.value)
    if (session && session.title === 'New Chat') {
      session.title = text.slice(0, 40) + (text.length > 40 ? '…' : '')
    }

    // Prepare for streaming
    isStreaming.value = true
    streamingContent.value = ''
    abortController.value = new AbortController()

    try {
      // Build history from current messages (exclude the user msg we just added)
      const history = messages.value
        .slice(0, -1) // exclude the just-pushed user message
        .map(m => ({ role: m.role, content: m.content }))

      await streamChat(
        {
          modelId:   selectedModelId.value,
          message:   text,
          images:    images || [],
          sessionId: activeSessionId.value,
          history
        },
        authStore.token,
        // onChunk callback
        (chunk) => {
          if (chunk.content) streamingContent.value += chunk.content
        },
        // onDone callback
        (fullContent) => {
          messages.value.push({
            id:        crypto.randomUUID(),
            role:      'assistant',
            content:   fullContent,
            createdAt: new Date().toISOString()
          })
          streamingContent.value = ''
        },
        abortController.value.signal
      )
    } catch (e) {
      if (e.name !== 'AbortError') {
        error.value = e.message || 'Stream error'
        // Keep partial response in messages if any was accumulated
        if (streamingContent.value) {
          messages.value.push({
            id:        crypto.randomUUID(),
            role:      'assistant',
            content:   streamingContent.value,
            createdAt: new Date().toISOString(),
            partial:   true
          })
          streamingContent.value = ''
        }
      }
    } finally {
      isStreaming.value    = false
      abortController.value = null
      // Refresh quota usage
      authStore.refreshMe()
    }
  }

  function stopGeneration() {
    if (abortController.value) {
      abortController.value.abort()
    }
  }

  return {
    sessions, activeSessionId, activeSession, messages,
    streamingContent, isStreaming, availableModels, selectedModelId, selectedModel, error,
    newSession, selectSession, deleteSession, loadHistory, sendMessage, stopGeneration
  }
})
