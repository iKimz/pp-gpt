<template>
  <div
    :class="[
      'flex gap-3 animate-fade-in',
      message.role === 'user' ? 'justify-end' : 'justify-start'
    ]"
  >
    <!-- Assistant avatar -->
    <div
      v-if="message.role === 'assistant'"
      class="w-7 h-7 rounded-lg bg-[#1a1b22] text-[#ffd700]
             flex items-center justify-center shrink-0 mt-1 shadow-sm"
    >
      <svg class="w-4 h-4 text-[#ffd700]" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
        <rect x="3" y="8" width="18" height="12" rx="4" />
        <path d="M12 2v6M9 5h6" stroke-linecap="round" />
        <circle cx="8.5" cy="13.5" r="1" fill="currentColor" />
        <circle cx="15.5" cy="13.5" r="1" fill="currentColor" />
        <path d="M10 17h4" stroke-linecap="round" />
      </svg>
    </div>

    <!-- Bubble -->
    <div
      :class="[
        'max-w-[80%] rounded-2xl px-4 py-3 text-sm leading-relaxed shadow-sm',
        message.role === 'user'
          ? 'bg-[#ffd700] text-[#1a1b22] font-medium rounded-br-sm border border-[#e9c400]'
          : 'bg-white text-[#1a1b22] rounded-bl-sm border border-[#e8e7f1]'
      ]"
    >
      <!-- User message: plain text -->
      <p v-if="message.role === 'user'" class="whitespace-pre-wrap break-words">{{ message.content }}</p>

      <!-- Empty state animation before streaming actually starts -->
      <div v-else-if="isStreaming && !message.content" class="flex items-center gap-1.5 h-5 px-1 py-1">
        <div class="w-2 h-2 rounded-full bg-brand-400 animate-bounce" style="animation-delay: 0ms"></div>
        <div class="w-2 h-2 rounded-full bg-brand-400 animate-bounce" style="animation-delay: 150ms"></div>
        <div class="w-2 h-2 rounded-full bg-brand-400 animate-bounce" style="animation-delay: 300ms"></div>
      </div>

      <!-- Assistant Content -->
      <div v-else class="flex flex-col gap-3">
        <!-- Active Thinking UI -->
        <div v-if="parsedMessage.isThinking" class="flex items-center gap-3 text-brand-300 font-medium text-xs py-1.5 px-3 bg-surface-800 rounded-lg border border-brand-500/20 shadow-glow animate-pulse w-max">
          <svg class="w-4 h-4 animate-spin text-brand-400" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
          </svg>
          <span class="tracking-wide">Thinking...</span>
        </div>

        <!-- Rendered markdown main text -->
        <div
          v-if="parsedMessage.main"
          class="prose-dark"
          v-html="renderedContent"
        />
      </div>

      <!-- Streaming indicator (only when there is content and we're actively streaming the main text) -->
      <span v-if="isStreaming && message.content && !parsedMessage.isThinking" class="typing-cursor" />
    </div>

    <!-- User avatar -->
    <div
      v-if="message.role === 'user'"
      class="w-7 h-7 rounded-full bg-surface-600 border border-surface-500
             flex items-center justify-center text-gray-300 text-xs shrink-0 mt-1"
    >
      {{ userInitial }}
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'
import { useAuthStore } from '@/stores/auth'

const props = defineProps({
  message: { type: Object, required: true },
  isStreaming: { type: Boolean, default: false }
})

const authStore = useAuthStore()
const userInitial = computed(() => authStore.username?.charAt(0).toUpperCase() ?? 'U')

// Configure marked with highlight.js
marked.setOptions({
  highlight: (code, lang) => {
    const language = hljs.getLanguage(lang) ? lang : 'plaintext'
    return hljs.highlight(code, { language }).value
  },
  breaks: true,
  gfm: true
})

const parsedMessage = computed(() => {
  let text = props.message.content || ''
  if (props.message.role !== 'assistant') {
    return { isThinking: false, main: text }
  }

  let isThinking = false
  let mainContent = text

  const openTags = (text.match(/<(think|reasoning)>/gi) || []).length
  const closeTags = (text.match(/<\/(think|reasoning)>/gi) || []).length
  
  if (openTags > closeTags && props.isStreaming) {
    isThinking = true
  }

  if (openTags > 0) {
    // Strip the thinking block completely
    mainContent = text.replace(/<(think|reasoning)>[\s\S]*?(<\/\1>|$)/gi, '').trim()
  }

  return {
    isThinking,
    main: mainContent
  }
})

const renderedContent = computed(() => {
  if (!parsedMessage.value.main) return ''
  const raw = marked.parse(parsedMessage.value.main)
  return DOMPurify.sanitize(raw)
})
</script>
