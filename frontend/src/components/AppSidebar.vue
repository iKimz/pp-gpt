<template>
  <aside class="w-64 h-screen flex flex-col bg-white border-r border-[#e8e7f1] shrink-0 shadow-sm">
    <!-- Brand header -->
    <div class="flex items-center gap-3 px-4 py-5 border-b border-[#e8e7f1]">
      <div class="w-8 h-8 rounded-lg bg-[#1a1b22] text-[#ffd700] flex items-center justify-center shadow-sm">
        <svg class="w-4 h-4 text-[#ffd700]" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
          <rect x="3" y="8" width="18" height="12" rx="4" />
          <path d="M12 2v6M9 5h6" stroke-linecap="round" />
          <circle cx="8.5" cy="13.5" r="1" fill="currentColor" />
          <circle cx="15.5" cy="13.5" r="1" fill="currentColor" />
          <path d="M10 17h4" stroke-linecap="round" />
        </svg>
      </div>
      <div>
        <p class="text-[#1a1b22] font-bold text-sm tracking-tight font-heading">PP-GPT</p>
        <p class="text-[#4d4732] text-xs">Enterprise AI Gateway</p>
      </div>
    </div>

    <!-- New chat button -->
    <div class="px-3 py-3">
      <button
        @click="chatStore.newSession()"
        class="w-full flex items-center justify-center gap-2 px-3 py-2.5 rounded-lg text-sm font-bold
               bg-[#ffd700] text-[#1a1b22] border border-[#e9c400]
               hover:bg-[#e9c400] transition-all duration-150 shadow-sm"
      >
        <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"/>
        </svg>
        New Chat
      </button>
    </div>

    <!-- Session history -->
    <div class="flex-1 overflow-y-auto px-2 pb-2 space-y-0.5">
      <p class="px-2 py-1.5 text-xs font-medium text-gray-500 uppercase tracking-wider">Recent</p>
      <div
        v-for="session in chatStore.sessions"
        :key="session.id"
        @click="chatStore.selectSession(session.id)"
        :class="[
          'nav-item group relative flex items-center justify-between pr-1.5',
          chatStore.activeSessionId === session.id ? 'active' : ''
        ]"
      >
        <div class="flex items-center gap-2 min-w-0 pr-1">
          <svg class="w-4 h-4 shrink-0 text-[#4d4732]" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
              d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"/>
          </svg>
          <span class="truncate text-xs">{{ session.title }}</span>
        </div>

        <button
          @click.stop="chatStore.deleteSession(session.id)"
          class="opacity-0 group-hover:opacity-100 p-1 text-[#4d4732] hover:text-red-600 rounded transition-opacity shrink-0"
          title="Remove from Recent"
        >
          <svg class="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
          </svg>
        </button>
      </div>

      <p v-if="chatStore.sessions.length === 0" class="px-2 py-4 text-xs text-gray-600 text-center">
        No conversations yet
      </p>
    </div>

    <!-- Admin link (if admin) -->
    <div v-if="authStore.isAdmin" class="px-3 py-2 border-t border-surface-600">
      <RouterLink to="/admin" class="nav-item">
        <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
            d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"/>
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
        </svg>
        <span class="text-xs">Admin Panel</span>
      </RouterLink>
    </div>

    <!-- User footer -->
    <div class="px-4 py-3 border-t border-surface-600">
      <div class="flex items-center gap-3">
        <div class="w-8 h-8 rounded-full bg-gradient-to-br from-brand-500 to-purple-600 flex items-center justify-center text-white text-xs font-bold">
          {{ authStore.username?.charAt(0).toUpperCase() }}
        </div>
        <div class="flex-1 min-w-0">
          <p class="text-sm font-medium text-black-200 truncate">{{ authStore.username }}</p>
          <p class="text-xs text-gray-500 truncate">{{ authStore.groupName }}</p>
        </div>
        <button @click="handleLogout" class="btn-icon" title="Logout">
          <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
              d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"/>
          </svg>
        </button>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { onMounted } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'

const authStore = useAuthStore()
const chatStore = useChatStore()
const router    = useRouter()

onMounted(() => chatStore.loadHistory())

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>
