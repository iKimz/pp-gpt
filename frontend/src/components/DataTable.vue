<template>
  <div>
    <!-- Loading shimmer -->
    <div v-if="loading" class="space-y-2">
      <div v-for="i in 4" :key="i" class="shimmer h-10 rounded-lg" />
    </div>

    <div v-else class="table-wrapper">
      <table class="data-table">
        <thead>
          <tr>
            <th v-for="col in columns" :key="col.key" :class="col.class">{{ col.label }}</th>
            <th v-if="$slots.actions || showActions" class="w-24 text-right pr-5">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="rows.length === 0">
            <td :colspan="columns.length + 1" class="py-8 text-center text-gray-500 text-sm">
              No records found
            </td>
          </tr>
          <tr v-for="row in rows" :key="row.id">
            <td v-for="col in columns" :key="col.key" :class="col.class">
              <!-- Custom slot for cell rendering -->
              <slot :name="`cell-${col.key}`" :row="row" :value="row[col.key]">
                <!-- Default: badge for boolean, plain text otherwise -->
                <template v-if="typeof row[col.key] === 'boolean'">
                  <span :class="row[col.key] ? 'badge badge-green' : 'badge badge-gray'">
                    {{ row[col.key] ? 'Active' : 'Inactive' }}
                  </span>
                </template>
                <template v-else>
                  {{ row[col.key] ?? '—' }}
                </template>
              </slot>
            </td>
            <td v-if="showActions" class="text-right pr-4">
              <div class="flex items-center justify-end gap-1">
                <slot name="actions" :row="row">
                  <button @click="$emit('edit', row)" class="btn-icon" title="Edit">
                    <svg class="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                        d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/>
                    </svg>
                  </button>
                  <button @click="$emit('delete', row)" class="btn-icon text-red-400 hover:text-red-300" title="Delete">
                    <svg class="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                        d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
                    </svg>
                  </button>
                </slot>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Pagination -->
    <div v-if="totalPages > 1" class="flex items-center justify-between mt-4 px-1">
      <p class="text-xs text-gray-500">Page {{ currentPage + 1 }} of {{ totalPages }}</p>
      <div class="flex gap-1">
        <button @click="$emit('page', currentPage - 1)" :disabled="currentPage === 0"
          class="btn-secondary py-1 px-2 text-xs disabled:opacity-40 disabled:cursor-not-allowed">
          ← Prev
        </button>
        <button @click="$emit('page', currentPage + 1)" :disabled="currentPage >= totalPages - 1"
          class="btn-secondary py-1 px-2 text-xs disabled:opacity-40 disabled:cursor-not-allowed">
          Next →
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  columns:     { type: Array,   required: true },
  rows:        { type: Array,   default: () => [] },
  loading:     { type: Boolean, default: false },
  showActions: { type: Boolean, default: true },
  currentPage: { type: Number,  default: 0 },
  totalPages:  { type: Number,  default: 1 }
})

defineEmits(['edit', 'delete', 'page'])
</script>
