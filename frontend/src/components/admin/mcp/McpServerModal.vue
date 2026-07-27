<template>
  <div v-if="show" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm">
    <div class="bg-white rounded-2xl border border-[#e8e7f1] shadow-2xl w-full max-w-lg overflow-hidden animate-in fade-in zoom-in-95 duration-200">
      <!-- Header -->
      <div class="px-6 py-4 border-b border-[#e8e7f1] flex items-center justify-between bg-[#fcfbfe]">
        <h3 class="text-sm font-bold text-[#1a1b22] font-heading flex items-center gap-2">
          <span>🔌</span> {{ editingServer ? 'Edit MCP Server' : 'Add New MCP Server' }}
        </h3>
        <button @click="$emit('close')" class="text-gray-400 hover:text-gray-600 text-lg leading-none">&times;</button>
      </div>

      <!-- Form -->
      <form @submit.prevent="handleSubmit" class="p-6 space-y-4 text-xs">
        <div>
          <label class="block font-semibold text-gray-700 mb-1">Server Name *</label>
          <input
            v-model="form.name"
            type="text"
            required
            placeholder="e.g. Finance REST Microservice / Firecrawl"
            class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs"
          />
        </div>

        <div>
          <label class="block font-semibold text-gray-700 mb-1">Endpoint URL *</label>
          <input
            v-model="form.endpointUrl"
            type="url"
            required
            placeholder="http://localhost:8088 or https://api.example.com/mcp"
            class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs font-mono"
          />
        </div>

        <div>
          <label class="block font-semibold text-gray-700 mb-1">Authentication Strategy *</label>
          <div class="grid grid-cols-2 gap-3">
            <label
              :class="[
                'flex items-center gap-2 p-3 rounded-xl border cursor-pointer transition-all',
                form.authType === 'STATIC_KEY'
                  ? 'border-[#ffd700] bg-[#fffdf0] text-[#1a1b22]'
                  : 'border-[#e8e7f1] bg-[#f8f7fa] text-gray-600'
              ]"
            >
              <input type="radio" v-model="form.authType" value="STATIC_KEY" class="hidden" />
              <span class="text-base">🔑</span>
              <div>
                <p class="font-bold text-xs">Static Bearer Key</p>
                <p class="text-[10px] text-gray-400">Header: Authorization Bearer ...</p>
              </div>
            </label>

            <label
              :class="[
                'flex items-center gap-2 p-3 rounded-xl border cursor-pointer transition-all',
                form.authType === 'OAUTH2'
                  ? 'border-[#ffd700] bg-[#fffdf0] text-[#1a1b22]'
                  : 'border-[#e8e7f1] bg-[#f8f7fa] text-gray-600'
              ]"
            >
              <input type="radio" v-model="form.authType" value="OAUTH2" class="hidden" />
              <span class="text-base">🛡️</span>
              <div>
                <p class="font-bold text-xs">OAuth 2.0 Auth</p>
                <p class="text-[10px] text-gray-400">RFC 7591 / Auto Discovery</p>
              </div>
            </label>
          </div>
        </div>

        <!-- Static Key Field -->
        <div v-if="form.authType === 'STATIC_KEY'">
          <label class="block font-semibold text-gray-700 mb-1">
            API Key / Token
            <span v-if="editingServer?.hasApiKey" class="text-[10px] text-emerald-600 font-normal ml-1">
              (Encrypted key stored. Leave blank to keep existing key)
            </span>
          </label>
          <input
            v-model="form.apiKey"
            type="password"
            :placeholder="editingServer?.hasApiKey ? '••••••••••••••••' : 'Enter API Key / Token'"
            class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs font-mono"
          />
        </div>

        <!-- OAuth 2.0 Configuration Fields -->
        <div v-if="form.authType === 'OAUTH2'" class="space-y-3 p-3 bg-purple-50/50 rounded-xl border border-purple-100">
          <div>
            <label class="block font-semibold text-purple-900 mb-1">Authorize URL (Optional)</label>
            <input
              v-model="form.oauthAuthorizeUrl"
              type="url"
              placeholder="Auto-discovered via RFC 7591 if left blank"
              class="w-full px-3 py-2 bg-white border border-purple-200 rounded-xl focus:outline-none focus:border-[#ffd700] text-xs font-mono"
            />
          </div>
          <div>
            <label class="block font-semibold text-purple-900 mb-1">Token URL (Optional)</label>
            <input
              v-model="form.oauthTokenUrl"
              type="url"
              placeholder="Auto-discovered via RFC 7591 if left blank"
              class="w-full px-3 py-2 bg-white border border-purple-200 rounded-xl focus:outline-none focus:border-[#ffd700] text-xs font-mono"
            />
          </div>
          <div class="grid grid-cols-2 gap-2">
            <div>
              <label class="block font-semibold text-purple-900 mb-1">Client ID</label>
              <input
                v-model="form.oauthClientId"
                type="text"
                placeholder="Optional"
                class="w-full px-3 py-2 bg-white border border-purple-200 rounded-xl focus:outline-none focus:border-[#ffd700] text-xs font-mono"
              />
            </div>
            <div>
              <label class="block font-semibold text-purple-900 mb-1">Client Secret</label>
              <input
                v-model="form.oauthClientSecret"
                type="password"
                placeholder="Optional"
                class="w-full px-3 py-2 bg-white border border-purple-200 rounded-xl focus:outline-none focus:border-[#ffd700] text-xs font-mono"
              />
            </div>
          </div>
        </div>

        <div>
          <label class="block font-semibold text-gray-700 mb-1">Description (Optional)</label>
          <textarea
            v-model="form.description"
            rows="2"
            placeholder="Brief notes describing this service..."
            class="w-full px-3 py-2 bg-[#f8f7fa] border border-[#e8e7f1] rounded-xl focus:outline-none focus:border-[#ffd700] text-xs resize-none"
          ></textarea>
        </div>

        <!-- Footer -->
        <div class="pt-4 border-t border-[#e8e7f1] flex items-center justify-end gap-3">
          <button
            type="button"
            @click="$emit('close')"
            class="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 font-semibold rounded-xl text-xs transition-all"
          >
            Cancel
          </button>
          <button
            type="submit"
            :disabled="submitting"
            class="px-5 py-2 bg-[#ffd700] hover:bg-[#e9c400] text-[#1a1b22] font-semibold rounded-xl text-xs shadow-sm transition-all disabled:opacity-50 flex items-center gap-1.5"
          >
            <span v-if="submitting" class="animate-spin text-sm">⏳</span>
            {{ editingServer ? 'Update Server' : 'Add Server' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { reactive, watch } from 'vue'

const props = defineProps({
  show: Boolean,
  editingServer: Object,
  submitting: Boolean
})

const emit = defineEmits(['close', 'save'])

const form = reactive({
  name: '',
  endpointUrl: '',
  authType: 'STATIC_KEY',
  apiKey: '',
  oauthAuthorizeUrl: '',
  oauthTokenUrl: '',
  oauthClientId: '',
  oauthClientSecret: '',
  description: '',
  isActive: true
})

watch(() => props.editingServer, (srv) => {
  if (srv) {
    form.name = srv.name || ''
    form.endpointUrl = srv.endpointUrl || ''
    form.authType = srv.authType || 'STATIC_KEY'
    form.apiKey = ''
    form.oauthAuthorizeUrl = srv.oauthAuthorizeUrl || ''
    form.oauthTokenUrl = srv.oauthTokenUrl || ''
    form.oauthClientId = srv.oauthClientId || ''
    form.oauthClientSecret = ''
    form.description = srv.description || ''
    form.isActive = srv.isActive !== false
  } else {
    form.name = ''
    form.endpointUrl = ''
    form.authType = 'STATIC_KEY'
    form.apiKey = ''
    form.oauthAuthorizeUrl = ''
    form.oauthTokenUrl = ''
    form.oauthClientId = ''
    form.oauthClientSecret = ''
    form.description = ''
    form.isActive = true
  }
}, { immediate: true })

function handleSubmit() {
  emit('save', { ...form })
}
</script>
