<template>
  <div class="flex h-screen overflow-hidden bg-[#fbf8ff]">
    <!-- Sidebar -->
    <AppSidebar />

    <!-- Main area -->
    <div class="flex-1 flex flex-col overflow-hidden bg-[#fbf8ff]">
      <!-- Topbar -->
      <AppTopbar />

      <!-- Messages area -->
      <div
        ref="messagesEl"
        class="flex-1 overflow-y-auto px-4 py-6 space-y-6"
      >
        <!-- Empty state -->
        <div v-if="chatStore.messages.length === 0 && !chatStore.isStreaming" class="flex flex-col items-center justify-center h-full text-center">
          <div class="w-16 h-16 rounded-2xl bg-[#1a1b22] text-[#ffd700] flex items-center justify-center mb-4 shadow-sm">
            <svg class="w-8 h-8 text-[#ffd700]" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"/>
            </svg>
          </div>
          <h2 class="text-xl font-bold text-[#1a1b22] font-heading mb-2">How can I help you?</h2>
          <p class="text-[#4d4732] text-sm max-w-sm">
            Select a model above and start a conversation. Your daily quota is shown in the top bar.
          </p>
        </div>

        <!-- Chat messages -->
        <MessageBubble
          v-for="msg in chatStore.messages"
          :key="msg.id"
          :message="msg"
          :isStreaming="false"
        />

        <!-- Streaming bubble -->
        <MessageBubble
          v-if="chatStore.isStreaming || chatStore.streamingContent"
          :message="{ role: 'assistant', content: chatStore.streamingContent }"
          :isStreaming="chatStore.isStreaming"
        />
      </div>

      <!-- Error banner -->
      <div v-if="chatStore.error" class="mx-4 mb-2 px-4 py-2 rounded-lg bg-[#ffdad6] border border-[#ffb4ab] text-[#ba1a1a] text-xs flex items-center gap-2">
        <svg class="w-4 h-4 shrink-0" fill="currentColor" viewBox="0 0 20 20">
          <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/>
        </svg>
        {{ chatStore.error }}
      </div>

      <!-- Input area -->
      <div class="px-4 pb-5 pt-2 border-t border-[#e8e7f1] bg-white">
        <div class="flex items-end gap-2 bg-white border border-[#e8e7f1] rounded-xl px-4 py-3 shadow-sm focus-within:border-[#ffd700] focus-within:ring-2 focus-within:ring-[#ffd700]/30 transition-all">
          <textarea
            v-model="inputText"
            ref="textareaEl"
            id="chat-input"
            rows="1"
            class="flex-1 bg-transparent resize-none text-sm text-[#1a1b22] placeholder-gray-400 outline-none max-h-40 scrollbar-thin"
            placeholder="Send a message…"
            @keydown.enter.exact.prevent="handleSend"
            @keydown.shift.enter.prevent="inputText += '\n'"
            @input="autoResize"
            :disabled="chatStore.isStreaming"
          />
          <button
            @click="handleSend"
            :disabled="!inputText.trim() || chatStore.isStreaming"
            id="send-button"
            class="btn-primary p-2 rounded-lg disabled:opacity-40 disabled:cursor-not-allowed shrink-0"
          >
            <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"/>
            </svg>
          </button>
        </div>
        <p class="text-xs text-[#4d4732] text-center mt-2">Enter to send · Shift+Enter for newline</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, watch } from 'vue'
import AppSidebar   from '@/components/AppSidebar.vue'
import AppTopbar    from '@/components/AppTopbar.vue'
import MessageBubble from '@/components/MessageBubble.vue'
import { useChatStore } from '@/stores/chat'

const chatStore  = useChatStore()
const inputText  = ref('')
const messagesEl = ref(null)
const textareaEl = ref(null)

// Auto-scroll to bottom on new messages
watch(
  () => chatStore.streamingContent,
  () => nextTick(() => scrollToBottom()),
  { immediate: false }
)

watch(
  () => chatStore.messages.length,
  () => nextTick(() => scrollToBottom())
)

function scrollToBottom() {
  if (messagesEl.value) {
    messagesEl.value.scrollTop = messagesEl.value.scrollHeight
  }
}

function autoResize() {
  const el = textareaEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || chatStore.isStreaming) return
  inputText.value = ''
  if (textareaEl.value) textareaEl.value.style.height = 'auto'
  await chatStore.sendMessage(text)
}
</script>
