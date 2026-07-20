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
        <!-- Image Preview Container -->
        <div v-if="selectedImages.length" class="flex flex-wrap gap-2 mb-2 p-2 bg-gray-50 rounded-xl border border-gray-200">
          <div v-for="(img, idx) in selectedImages" :key="idx" class="relative group">
            <img :src="img" class="w-16 h-16 object-cover rounded-lg border border-gray-300 shadow-sm" />
            <button
              @click="removeImage(idx)"
              class="absolute -top-1.5 -right-1.5 bg-red-500 text-white rounded-full w-4 h-4 flex items-center justify-center text-[10px] shadow hover:bg-red-600 transition-colors"
            >
              ✕
            </button>
          </div>
        </div>

        <div :class="[
          'flex gap-2 bg-white border border-[#e8e7f1] rounded-2xl px-3.5 py-2 shadow-sm focus-within:border-[#ffd700] focus-within:ring-2 focus-within:ring-[#ffd700]/30 transition-all',
          isMultiLine ? 'items-end' : 'items-center'
        ]">
          <input
            type="file"
            ref="fileInput"
            accept="image/*"
            multiple
            class="hidden"
            @change="onFilesSelected"
          />
          <button
            v-if="chatStore.selectedModel?.supportsVision"
            @click="triggerFileInput"
            :disabled="chatStore.isStreaming"
            type="button"
            title="Attach image (Multimodal Vision supported)"
            :class="[
              'p-2 text-gray-500 hover:text-amber-600 rounded-xl hover:bg-gray-100 transition-colors disabled:opacity-40 shrink-0',
              isMultiLine ? 'mb-0.5' : ''
            ]"
          >
            <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.172 7l-6.586 6.586a2 2 0 102.828 2.828l6.414-6.586a4 4 0 00-5.656-5.656l-6.415 6.585a6 6 0 108.486 8.486L20.5 13" />
            </svg>
          </button>

          <textarea
            v-model="inputText"
            ref="textareaEl"
            id="chat-input"
            rows="1"
            class="flex-1 bg-transparent resize-none text-sm text-[#1a1b22] placeholder-gray-400 outline-none max-h-[96px] scrollbar-thin py-1 leading-normal overflow-y-hidden"
            placeholder="Send a message…"
            @keydown.enter.exact.prevent="handleSend"
            @keydown.shift.enter.prevent="handleShiftEnter"
            @input="autoResize"
            :disabled="chatStore.isStreaming"
          />
          <button
            @click="handleSend"
            :disabled="(!inputText.trim() && !selectedImages.length) || chatStore.isStreaming"
            id="send-button"
            :class="[
              'btn-primary p-2 rounded-xl disabled:opacity-40 disabled:cursor-not-allowed shrink-0',
              isMultiLine ? 'mb-0.5' : ''
            ]"
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

const chatStore      = useChatStore()
const inputText      = ref('')
const selectedImages = ref([])
const isMultiLine    = ref(false)
const messagesEl     = ref(null)
const textareaEl     = ref(null)
const fileInput      = ref(null)

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

watch(inputText, () => {
  nextTick(() => autoResize())
})

function scrollToBottom() {
  if (messagesEl.value) {
    messagesEl.value.scrollTop = messagesEl.value.scrollHeight
  }
}

function autoResize() {
  const el = textareaEl.value
  if (!el) {
    isMultiLine.value = false
    return
  }
  el.style.height = 'auto'
  const targetHeight = Math.min(el.scrollHeight, 96)
  el.style.height = targetHeight + 'px'
  el.style.overflowY = el.scrollHeight > 96 ? 'auto' : 'hidden'
  isMultiLine.value = el.scrollHeight > 36 || (inputText.value && inputText.value.includes('\n'))
}

function handleShiftEnter() {
  inputText.value += '\n'
  nextTick(() => autoResize())
}

function triggerFileInput() {
  if (fileInput.value) fileInput.value.click()
}

function onFilesSelected(e) {
  const files = Array.from(e.target.files || [])
  files.forEach(file => {
    if (!file.type.startsWith('image/')) return
    const reader = new FileReader()
    reader.onload = (event) => {
      selectedImages.value.push(event.target.result)
    }
    reader.readAsDataURL(file)
  })
  e.target.value = ''
}

function removeImage(index) {
  selectedImages.value.splice(index, 1)
}

async function handleSend() {
  const text = inputText.value.trim()
  const images = [...selectedImages.value]
  if ((!text && !images.length) || chatStore.isStreaming) return
  inputText.value = ''
  selectedImages.value = []
  isMultiLine.value = false
  if (textareaEl.value) textareaEl.value.style.height = 'auto'
  await chatStore.sendMessage(text, images)
}
</script>
