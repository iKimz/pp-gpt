<template>
  <Teleport to="body">
    <div class="modal-overlay" @mousedown.self="$emit('close')">
      <div
        :class="[
          'glass rounded-2xl shadow-glass w-full mx-4 animate-slide-up flex flex-col',
          wide ? 'max-w-xl' : 'max-w-md',
          'max-h-[88vh]'
        ]"
      >
        <!-- ── Header ── -->
        <div class="flex items-center justify-between px-6 py-4 border-b border-[#e8e7f1] shrink-0 bg-white rounded-t-2xl">
          <h2 class="text-base font-bold text-[#1a1b22] font-heading">{{ title }}</h2>
          <button @click="$emit('close')" class="btn-icon">
            <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
            </svg>
          </button>
        </div>

        <!-- ── Body (scrollable) ── -->
        <div class="flex-1 overflow-y-auto px-6 py-5 min-h-0 bg-white">
          <slot />
        </div>

        <!-- ── Footer (sticky) ── -->
        <div class="shrink-0 flex items-center justify-end gap-3 px-6 py-4 border-t border-[#e8e7f1] bg-[#fbf8ff] rounded-b-2xl">
          <slot name="footer-left" />
          <button v-if="showCancel" @click="$emit('close')" class="btn-secondary">Cancel</button>
          <button @click="$emit('confirm')" :disabled="loading" class="btn-primary flex items-center gap-2">
            <svg v-if="loading" class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
            </svg>
            {{ loading ? 'Saving…' : confirmText }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
defineProps({
  title:       { type: String,  default: 'Dialog' },
  confirmText: { type: String,  default: 'Save' },
  loading:     { type: Boolean, default: false },
  wide:        { type: Boolean, default: false },
  showCancel:  { type: Boolean, default: true }
})
defineEmits(['close', 'confirm'])
</script>

