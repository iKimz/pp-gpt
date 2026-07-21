<template>
  <div class="flex h-screen overflow-hidden bg-[#fbf8ff]">
    <!-- Admin sidebar (Hiroshi DS Level 1 Surface) -->
    <aside class="w-56 h-screen flex flex-col bg-white border-r border-[#e8e7f1] shrink-0 shadow-sm">
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
          <p class="text-[#1a1b22] font-bold text-xs tracking-tight font-heading">PP-GPT Admin</p>
          <p class="text-[#4d4732] text-[11px]">Control Panel</p>
        </div>
      </div>

      <nav class="flex-1 px-3 py-4 space-y-1">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="nav-item"
          :class="{ active: route.path.startsWith(item.to) }"
        >
          <span class="text-base">{{ item.icon }}</span>
          <span class="text-xs font-medium">{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="px-3 py-3 border-t border-[#e8e7f1]">
        <RouterLink to="/chat" class="nav-item text-[#4d4732] hover:text-[#1a1b22]">
          <span>💬</span>
          <span class="text-xs font-medium">Back to Chat</span>
        </RouterLink>
      </div>
    </aside>

    <!-- Main content canvas (Hiroshi Level 0 Canvas #FBF8FF) -->
    <div class="flex-1 overflow-y-auto bg-[#fbf8ff]">
      <div class="w-full max-w-[1600px] mx-auto px-6 sm:px-8 py-8">
        <RouterView />
      </div>
    </div>
  </div>
</template>

<script setup>
import { RouterLink, RouterView, useRoute } from 'vue-router'

const route = useRoute()
const navItems = [
  { to: '/admin/analytics', icon: '📊', label: 'Analytics'   },
  { to: '/admin/groups',    icon: '👥', label: 'Groups'      },
  { to: '/admin/models',    icon: '🤖', label: 'AI Models'   },
  { to: '/admin/mcp-servers', icon: '🛠️', label: 'MCP Servers' },
  { to: '/admin/credits',   icon: '💳', label: 'Credit Rates'},
  { to: '/admin/users',     icon: '👤', label: 'Users'       },
  { to: '/admin/logs',      icon: '📋', label: 'Audit Logs'  },
]
</script>
