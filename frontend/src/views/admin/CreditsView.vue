<template>
  <div>
    <div class="mb-6">
      <h1 class="text-xl font-bold text-[#1a1b22] font-heading">Credit Rates</h1>
      <p class="text-sm text-[#4d4732] mt-0.5">Configure token multipliers per model. Simulation preview updates live.</p>
    </div>

    <!-- Simulation note -->
    <div class="glass rounded-xl p-4 mb-6 flex items-start gap-3 border border-[#e8e7f1] bg-white">
      <span class="text-lg">💡</span>
      <div>
        <p class="text-sm font-semibold text-[#705d00]">Live Preview</p>
        <p class="text-xs text-[#4d4732] mt-0.5">
          Preview column shows estimated credits for a typical request: <strong class="text-[#1a1b22]">1,000 input + 500 output tokens</strong>.
          Formula: <code class="text-[#705d00] bg-[#f4f2fd] px-1.5 py-0.5 rounded text-xs font-mono">(1000 × inMult) + (500 × outMult)</code>
        </p>
      </div>
    </div>

    <!-- Loading shimmer -->
    <div v-if="loading" class="space-y-2">
      <div v-for="i in 3" :key="i" class="shimmer h-16 rounded-xl" />
    </div>

    <!-- Matrix table -->
    <div v-else class="table-wrapper">
      <table class="data-table">
        <thead>
          <tr>
            <th>Model</th>
            <th>Provider</th>
            <th>Rate Status</th>
            <th>Input Multiplier</th>
            <th>Output Multiplier</th>
            <th class="text-amber-400">Preview (1k in / 500 out)</th>
            <th class="w-36 text-right pr-5">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(rate, idx) in tableRows" :key="rate.modelId">
            <td class="font-bold text-sm text-[#1a1b22]">{{ rate.displayName }}</td>
            <td>
              <span :class="providerBadgeClass(rate.provider)">{{ rate.provider }}</span>
            </td>
            <td>
              <span v-if="rate.isCustom" class="badge badge-purple text-[10px]">Custom</span>
              <span v-else class="badge badge-gray text-[10px]">System Default</span>
            </td>
            <td>
              <input
                v-model.number="rate.inputMultiplier"
                type="number"
                step="0.001"
                min="0"
                class="input-field py-1 w-28 font-mono text-xs"
              />
            </td>
            <td>
              <input
                v-model.number="rate.outputMultiplier"
                type="number"
                step="0.001"
                min="0"
                class="input-field py-1 w-28 font-mono text-xs"
              />
            </td>
            <td>
              <span class="font-mono font-semibold" :class="previewColor(rate)">
                {{ calcPreview(rate).toFixed(2) }}
              </span>
            </td>
            <td class="text-right pr-4 whitespace-nowrap">
              <div class="flex items-center justify-end gap-1.5">
                <button
                  @click="saveRate(rate, idx)"
                  :disabled="actionIdx === idx"
                  class="btn-primary py-1 px-2.5 text-xs"
                >
                  <svg v-if="actionIdx === idx" class="w-3 h-3 animate-spin inline mr-1" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
                  </svg>
                  Save
                </button>
                <button
                  v-if="rate.isCustom"
                  @click="resetRate(rate, idx)"
                  :disabled="actionIdx === idx"
                  class="btn-secondary py-1 px-2 text-xs text-gray-400 hover:text-red-400 hover:border-red-500/50"
                  title="Reset to System Default"
                >
                  Reset
                </button>
              </div>
            </td>
          </tr>
          <tr v-if="tableRows.length === 0">
            <td colspan="7" class="text-center py-6 text-gray-500 text-sm">
              No models available. Add AI models in the <router-link to="/admin/models" class="text-brand-400 underline">Models section</router-link> first.
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { adminApi } from '@/api/admin'

const tableRows = ref([])
const loading   = ref(true)
const actionIdx = ref(-1)

onMounted(async () => {
  await loadData()
})

async function loadData() {
  loading.value = true
  try {
    const [ratesRes, modelsRes] = await Promise.all([adminApi.getCreditRates(), adminApi.getModels()])
    const existingRates = ratesRes.data || []
    // Filter out Guardrail models so credit rates only apply to Generation models
    const models        = (modelsRes.data || []).filter(m => m.modelType !== 'GUARDRAIL')

    const rateMap = new Map(existingRates.map(r => [r.modelId, r]))

    tableRows.value = models.map(model => {
      const existing = rateMap.get(model.id)
      if (existing) {
        return {
          id:               existing.id,
          modelId:          model.id,
          displayName:      model.name || model.modelName,
          modelName:        model.modelName,
          provider:         model.provider,
          modelType:        model.modelType || 'GENERATION',
          inputMultiplier:  Number(existing.inputMultiplier),
          outputMultiplier: Number(existing.outputMultiplier),
          isCustom:         true
        }
      } else {
        return {
          id:               null,
          modelId:          model.id,
          displayName:      model.name || model.modelName,
          modelName:        model.modelName,
          provider:         model.provider,
          modelType:        model.modelType || 'GENERATION',
          inputMultiplier:  1.0,
          outputMultiplier: 2.0,
          isCustom:         false
        }
      }
    })
  } finally {
    loading.value = false
  }
}

function calcPreview(rate) {
  return (1000 * Number(rate.inputMultiplier || 0)) + (500 * Number(rate.outputMultiplier || 0))
}

function previewColor(rate) {
  const p = calcPreview(rate)
  if (p > 5000) return 'text-red-400'
  if (p > 2000) return 'text-amber-400'
  return 'text-emerald-400'
}

function providerBadgeClass(provider) {
  const map = {
    OPENAI:      'badge badge-purple',
    AZURE:       'badge badge-blue',
    AWS_BEDROCK: 'badge badge-green',
    ANTHROPIC:   'badge badge-gray',
    GOOGLE:      'badge badge-blue',
  }
  return map[provider] || 'badge badge-gray'
}

async function saveRate(rate, idx) {
  actionIdx.value = idx
  try {
    const saved = await adminApi.upsertCreditRate({
      id:               rate.id,
      modelId:          rate.modelId,
      inputMultiplier:  rate.inputMultiplier,
      outputMultiplier: rate.outputMultiplier
    })
    rate.id       = saved.data.id
    rate.isCustom = true
  } catch (e) {
    alert(e.response?.data?.message || 'Save failed')
  } finally {
    actionIdx.value = -1
  }
}

async function resetRate(rate, idx) {
  if (!confirm(`Reset credit multipliers for "${rate.modelName}" back to system defaults (1.0 in / 2.0 out)?`)) return
  actionIdx.value = idx
  try {
    if (rate.id) {
      await adminApi.deleteCreditRate(rate.id)
    }
    rate.id               = null
    rate.inputMultiplier  = 1.0
    rate.outputMultiplier = 2.0
    rate.isCustom         = false
  } catch (e) {
    alert(e.response?.data?.message || 'Reset failed')
  } finally {
    actionIdx.value = -1
  }
}
</script>
