<template>
  <ToastNotification />
  <ConfirmModal />
  <RouterView v-slot="{ Component, route }">
    <Transition name="page" mode="out-in">
      <component :is="Component" :key="route.path" />
    </Transition>
  </RouterView>
</template>

<script setup>
import { RouterView } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { onMounted } from 'vue'
import ToastNotification from '@/components/ui/ToastNotification.vue'
import ConfirmModal from '@/components/ui/ConfirmModal.vue'

const authStore = useAuthStore()

// Rehydrate auth state from localStorage on app load
onMounted(() => authStore.refreshFromStorage())
</script>
