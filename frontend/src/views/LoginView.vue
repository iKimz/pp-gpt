<template>
  <div class="min-h-screen flex items-center justify-center relative overflow-hidden bg-[#fbf8ff]">
    <!-- Soft background decorative glow -->
    <div class="absolute w-[500px] h-[500px] rounded-full bg-[#ffd700]/15 blur-3xl top-[-100px] left-[-100px] pointer-events-none" />
    <div class="absolute w-[400px] h-[400px] rounded-full bg-[#eeedf7]/60 blur-3xl bottom-[-50px] right-[-50px] pointer-events-none" />

    <!-- Login card (Hiroshi DS Level 2 Surface) -->
    <div class="relative z-10 w-full max-w-sm mx-4">
      <div class="bg-white rounded-2xl border border-[#e8e7f1] p-8 shadow-md animate-slide-up">
        <!-- Logo -->
        <div class="flex flex-col items-center mb-8">
          <div class="w-14 h-14 rounded-2xl bg-[#1a1b22] text-[#ffd700]
                      flex items-center justify-center mb-4 shadow-sm font-heading font-extrabold">
            <svg class="w-7 h-7 text-[#ffd700]" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                d="M9.75 3.104v5.714a2.25 2.25 0 01-.659 1.591L5 14.5M9.75 3.104c-.251.023-.501.05-.75.082m.75-.082a24.301 24.301 0 014.5 0m0 0v5.714c0 .597.237 1.17.659 1.591L19.8 15.3M14.25 3.104c.251.023.501.05.75.082M19.8 15.3l-1.57.393A9.065 9.065 0 0112 15a9.065 9.065 0 00-6.23-.693L5 14.5m14.8.8l1.402 1.402c1.232 1.232.65 3.318-1.067 3.611A48.309 48.309 0 0112 21c-2.773 0-5.491-.235-8.135-.687-1.718-.293-2.3-2.379-1.067-3.61L5 14.5"/>
            </svg>
          </div>
          <h1 class="text-2xl font-extrabold text-[#1a1b22] font-heading">PP-GPT</h1>
          <p class="text-sm text-[#4d4732] mt-0.5 font-medium">Enterprise AI Gateway</p>
        </div>

        <!-- Auth source toggle -->
        <div class="flex rounded-lg p-1 bg-[#f4f2fd] border border-[#e8e7f1] mb-6">
          <button
            v-for="src in ['LOCAL', 'AZURE_AD']"
            :key="src"
            @click="form.authSource = src"
            :class="[
              'flex-1 py-1.5 rounded-md text-xs font-semibold transition-all duration-200',
              form.authSource === src
                ? 'bg-[#ffd700] text-[#1a1b22] shadow-sm'
                : 'text-[#4d4732] hover:text-[#1a1b22]'
            ]"
          >
            {{ src === 'LOCAL' ? '🔑 Local' : '☁️ Azure AD' }}
          </button>
        </div>

        <!-- Form -->
        <form @submit.prevent="handleLogin" class="space-y-4">
          <div>
            <label class="label">Username</label>
            <input
              v-model="form.username"
              type="text"
              id="login-username"
              class="input-field"
              placeholder="Enter username"
              autocomplete="username"
              required
            />
          </div>
          <div>
            <label class="label">Password</label>
            <div class="relative">
              <input
                v-model="form.password"
                :type="showPassword ? 'text' : 'password'"
                id="login-password"
                class="input-field pr-10"
                placeholder="••••••••"
                autocomplete="current-password"
                required
              />
              <button type="button" @click="showPassword = !showPassword"
                class="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-200">
                <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path v-if="!showPassword" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                    d="M15 12a3 3 0 11-6 0 3 3 0 016 0z M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"/>
                  <path v-else stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                    d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- Error message -->
          <div v-if="error" class="flex items-center gap-2 px-3 py-2.5 rounded-lg bg-red-900/30 border border-red-700/40 text-red-400 text-xs">
            <svg class="w-4 h-4 shrink-0" fill="currentColor" viewBox="0 0 20 20">
              <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/>
            </svg>
            {{ error }}
          </div>

          <button
            type="submit"
            id="login-submit"
            :disabled="loading"
            class="btn-primary w-full py-2.5 flex items-center justify-center gap-2"
          >
            <svg v-if="loading" class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
            </svg>
            {{ loading ? 'Signing in…' : 'Sign In' }}
          </button>
        </form>
      </div>

      <p class="text-center text-xs text-gray-600 mt-4">Enterprise AI Gateway · Internal Use Only</p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router    = useRouter()
const route     = useRoute()
const authStore = useAuthStore()

const loading      = ref(false)
const error        = ref('')
const showPassword = ref(false)

const form = reactive({
  username:   '',
  password:   '',
  authSource: 'LOCAL'
})

async function handleLogin() {
  loading.value = true
  error.value   = ''
  try {
    await authStore.login(form)
    const redirect = route.query.redirect || '/chat'
    router.push(redirect)
  } catch (e) {
    error.value = e.response?.data?.message || e.message || 'Authentication failed'
  } finally {
    loading.value = false
  }
}
</script>
