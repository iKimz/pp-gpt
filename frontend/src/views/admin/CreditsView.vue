<template>
  <div>
    <!-- Page Header -->
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-xl font-bold text-[#1a1b22] font-heading flex items-center gap-2">
          <span>Credit Rates & Cost Intelligence</span>
          <span class="text-xs font-normal px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200">
            Executive Cost Mapping
          </span>
        </h1>
        <p class="text-sm text-[#4d4732] mt-0.5">
          Configure provider pricing ($ per 1M tokens) or credit multipliers. Real-time cost estimates in THB (฿) and Credits.
        </p>
      </div>

      <!-- Financial Configuration Badge -->
      <div class="glass px-4 py-2 rounded-xl border border-[#e8e7f1] bg-white flex items-center gap-4 text-xs">
        <div class="flex items-center gap-1.5">
          <span class="text-[#705d00] font-semibold">💵 1 USD =</span>
          <div class="flex items-center gap-1">
            <span class="font-mono text-gray-500">฿</span>
            <input
              v-model.number="exchangeRate"
              type="number"
              step="0.1"
              min="1"
              class="w-16 input-field py-0.5 px-1.5 text-xs font-mono text-center font-bold text-brand-500"
            />
            <span class="text-gray-400 font-medium">THB</span>
          </div>
        </div>
        <div class="h-4 w-[1px] bg-gray-200" />
        <div class="flex items-center gap-1.5">
          <span class="text-[#705d00] font-semibold">🪙 1 Credit =</span>
          <span class="font-mono font-bold text-[#1a1b22]">฿{{ creditBaseThb }}</span>
        </div>
      </div>
    </div>

    <!-- Executive Summary Dashboard Cards -->
    <div v-if="!loading && tableRows.length > 0" class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
      <!-- Card 1: Lowest Cost Model -->
      <div class="glass rounded-xl p-4 border border-emerald-200/60 bg-emerald-50/30 flex items-start gap-3">
        <div class="w-10 h-10 rounded-lg bg-emerald-100 flex items-center justify-center text-lg shrink-0 text-emerald-700">
          🟢
        </div>
        <div>
          <p class="text-xs font-medium text-emerald-800 uppercase tracking-wider">Most Cost-Effective</p>
          <p class="text-sm font-bold text-[#1a1b22] mt-0.5">{{ lowestCostModel.displayName }}</p>
          <p class="text-xs text-emerald-700 mt-1 font-mono">
            <strong>฿{{ calcCostThb(lowestCostModel).toFixed(4) }}</strong> / request (~{{ calcPreview(lowestCostModel).toFixed(1) }} Credits)
          </p>
        </div>
      </div>

      <!-- Card 2: Highest Premium Model -->
      <div class="glass rounded-xl p-4 border border-purple-200/60 bg-purple-50/30 flex items-start gap-3">
        <div class="w-10 h-10 rounded-lg bg-purple-100 flex items-center justify-center text-lg shrink-0 text-purple-700">
          💎
        </div>
        <div>
          <p class="text-xs font-medium text-purple-800 uppercase tracking-wider">Highest Premium Tier</p>
          <p class="text-sm font-bold text-[#1a1b22] mt-0.5">{{ highestCostModel.displayName }}</p>
          <p class="text-xs text-purple-700 mt-1 font-mono">
            <strong>฿{{ calcCostThb(highestCostModel).toFixed(4) }}</strong> / request (~{{ calcPreview(highestCostModel).toFixed(1) }} Credits)
          </p>
        </div>
      </div>

      <!-- Card 3: Standard Benchmark Request -->
      <div class="glass rounded-xl p-4 border border-amber-200/60 bg-amber-50/30 flex items-start gap-3">
        <div class="w-10 h-10 rounded-lg bg-amber-100 flex items-center justify-center text-lg shrink-0 text-amber-700">
          📊
        </div>
        <div>
          <p class="text-xs font-medium text-amber-800 uppercase tracking-wider">Benchmark Request Baseline</p>
          <p class="text-sm font-bold text-[#1a1b22] mt-0.5">1k Input + 500 Output Tokens</p>
          <p class="text-xs text-amber-700 mt-1 font-mono">
            Avg Cost: <strong>฿{{ avgRequestCostThb.toFixed(4) }}</strong> / request
          </p>
        </div>
      </div>
    </div>

    <!-- Loading shimmer -->
    <div v-if="loading" class="space-y-2">
      <div v-for="i in 3" :key="i" class="shimmer h-16 rounded-xl" />
    </div>

    <!-- Matrix Table -->
    <div v-else class="table-wrapper">
      <table class="data-table">
        <thead>
          <tr>
            <th>Model</th>
            <th>Provider</th>
            <th>Cost per 1M Tokens (USD)</th>
            <th>Credit Multipliers</th>
            <th class="text-emerald-700">Est. Cost / Request (1k in / 500 out)</th>
            <th>Status</th>
            <th class="w-36 text-right pr-5">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(rate, idx) in tableRows" :key="rate.modelId">
            <!-- Model Name -->
            <td>
              <div class="font-bold text-sm text-[#1a1b22]">{{ rate.displayName }}</div>
              <div class="text-[11px] text-gray-500 font-mono">{{ rate.modelName }}</div>
            </td>

            <!-- Provider -->
            <td>
              <span :class="providerBadgeClass(rate.provider)">{{ rate.provider }}</span>
            </td>

            <!-- Cost per 1M Tokens (USD Input / Output) -->
            <td>
              <div class="flex items-center gap-2">
                <div class="flex flex-col gap-1">
                  <span class="text-[10px] text-gray-600 uppercase font-semibold">In ($/1M)</span>
                  <div class="relative">
                    <span class="absolute left-2 top-1.5 text-xs text-gray-400 font-mono">$</span>
                    <input
                      v-model.number="rate.inputPricePer1m"
                      @input="onPriceChange(rate, 'input')"
                      type="number"
                      step="0.01"
                      min="0"
                      class="input-field py-1 pl-5 w-24 font-mono text-xs"
                      placeholder="0.00"
                    />
                  </div>
                </div>
                <div class="flex flex-col gap-1">
                  <span class="text-[10px] text-gray-600 uppercase font-semibold">Out ($/1M)</span>
                  <div class="relative">
                    <span class="absolute left-2 top-1.5 text-xs text-gray-400 font-mono">$</span>
                    <input
                      v-model.number="rate.outputPricePer1m"
                      @input="onPriceChange(rate, 'output')"
                      type="number"
                      step="0.01"
                      min="0"
                      class="input-field py-1 pl-5 w-24 font-mono text-xs"
                      placeholder="0.00"
                    />
                  </div>
                </div>
              </div>
            </td>

            <!-- Credit Multipliers (Input / Output) -->
            <td>
              <div class="flex items-center gap-2">
                <div class="flex flex-col gap-1">
                  <span class="text-[10px] text-gray-600 uppercase font-semibold">Input Mult</span>
                  <input
                    v-model.number="rate.inputMultiplier"
                    @input="onMultiplierChange(rate, 'input')"
                    type="number"
                    step="0.001"
                    min="0"
                    class="input-field py-1 w-24 font-mono text-xs font-semibold text-brand-400"
                  />
                </div>
                <div class="flex flex-col gap-1">
                  <span class="text-[10px] text-gray-600 uppercase font-semibold">Output Mult</span>
                  <input
                    v-model.number="rate.outputMultiplier"
                    @input="onMultiplierChange(rate, 'output')"
                    type="number"
                    step="0.001"
                    min="0"
                    class="input-field py-1 w-24 font-mono text-xs font-semibold text-purple-400"
                  />
                </div>
              </div>
            </td>

            <!-- Est Cost / Request (1k in / 500 out) in THB & Credits -->
            <td>
              <div class="flex flex-col">
                <div class="flex items-center gap-1.5 font-mono font-bold text-sm" :class="costColorClass(rate)">
                  <span>฿{{ calcCostThb(rate).toFixed(4) }}</span>
                  <span class="text-[10px] px-1.5 py-0.2 rounded font-sans" :class="costBadgeClass(rate)">
                    {{ costCategory(rate) }}
                  </span>
                </div>
                <div class="text-[11px] text-gray-500 font-mono">
                  ~{{ calcPreview(rate).toFixed(1) }} Credits / request
                </div>
              </div>
            </td>

            <!-- Rate Status -->
            <td>
              <span v-if="rate.isCustom" class="badge badge-purple text-[10px]">Custom</span>
              <span v-else class="badge badge-gray text-[10px]">System Default</span>
            </td>

            <!-- Actions -->
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
import { ref, computed, onMounted } from 'vue'
import { adminApi } from '@/api/admin'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'

const toast = useToast()
const { confirm } = useConfirm()
const tableRows = ref([])
const loading   = ref(true)
const actionIdx = ref(-1)

// Financial baseline state
const exchangeRate  = ref(35.50) // 1 USD = 35.50 THB
const creditBaseThb = ref(0.01)  // 1 Credit = 0.01 THB

onMounted(async () => {
  await loadData()
})

async function loadData() {
  loading.value = true
  try {
    const [ratesRes, modelsRes] = await Promise.all([adminApi.getCreditRates(), adminApi.getModels()])
    const existingRates = ratesRes.data || []
    const models        = (modelsRes.data || []).filter(m => m.modelType !== 'GUARDRAIL')

    const rateMap = new Map(existingRates.map(r => [r.modelId, r]))

    tableRows.value = models.map(model => {
      const existing = rateMap.get(model.id)
      if (existing) {
        return {
          id:                existing.id,
          modelId:           model.id,
          displayName:       model.name || model.modelName,
          modelName:         model.modelName,
          provider:          model.provider,
          modelType:         model.modelType || 'GENERATION',
          inputMultiplier:   Number(existing.inputMultiplier),
          outputMultiplier:  Number(existing.outputMultiplier),
          inputPricePer1m:   existing.inputPricePer1m != null ? Number(existing.inputPricePer1m) : 0.0,
          outputPricePer1m:  existing.outputPricePer1m != null ? Number(existing.outputPricePer1m) : 0.0,
          isCustom:          true
        }
      } else {
        return {
          id:                null,
          modelId:           model.id,
          displayName:       model.name || model.modelName,
          modelName:         model.modelName,
          provider:          model.provider,
          modelType:         model.modelType || 'GENERATION',
          inputMultiplier:   1.0,
          outputMultiplier:  2.0,
          inputPricePer1m:   0.0,
          outputPricePer1m:  0.0,
          isCustom:          false
        }
      }
    }).sort((a, b) => (a.displayName || '').localeCompare(b.displayName || ''))
  } finally {
    loading.value = false
  }
}

// Recalculate multipliers when price per 1M changes
function onPriceChange(rate, type) {
  if (type === 'input') {
    const priceUsd = Number(rate.inputPricePer1m || 0)
    // Formula: Multiplier = (priceUsd * exchangeRate) / (1000 * creditBaseThb)
    // Example: $2.50 / 1M = $0.0025 / 1k = ฿0.08875 / 1k = 8.875 credits / 1k tokens = 0.008875 per token
    if (priceUsd > 0) {
      rate.inputMultiplier = Number(((priceUsd * exchangeRate.value) / (1000 * creditBaseThb.value)).toFixed(4))
    }
  } else if (type === 'output') {
    const priceUsd = Number(rate.outputPricePer1m || 0)
    if (priceUsd > 0) {
      rate.outputMultiplier = Number(((priceUsd * exchangeRate.value) / (1000 * creditBaseThb.value)).toFixed(4))
    }
  }
}

// Recalculate price per 1M when multiplier changes manually
function onMultiplierChange(rate, type) {
  if (type === 'input') {
    const mult = Number(rate.inputMultiplier || 0)
    if (mult > 0 && exchangeRate.value > 0) {
      rate.inputPricePer1m = Number(((mult * 1000 * creditBaseThb.value) / exchangeRate.value).toFixed(2))
    }
  } else if (type === 'output') {
    const mult = Number(rate.outputMultiplier || 0)
    if (mult > 0 && exchangeRate.value > 0) {
      rate.outputPricePer1m = Number(((mult * 1000 * creditBaseThb.value) / exchangeRate.value).toFixed(2))
    }
  }
}

// Calculate total Credits for 1k input + 500 output tokens
function calcPreview(rate) {
  return (1000 * Number(rate.inputMultiplier || 0)) + (500 * Number(rate.outputMultiplier || 0))
}

// Calculate total cost in THB (฿) for 1k input + 500 output tokens
function calcCostThb(rate) {
  const inPriceUsd  = Number(rate.inputPricePer1m || 0)
  const outPriceUsd = Number(rate.outputPricePer1m || 0)
  if (inPriceUsd > 0 || outPriceUsd > 0) {
    const costUsd = (1000 * inPriceUsd / 1000000) + (500 * outPriceUsd / 1000000)
    return costUsd * exchangeRate.value
  }
  // Fallback using credits if price per 1M isn't set:
  return calcPreview(rate) * creditBaseThb.value
}

function costCategory(rate) {
  const cost = calcCostThb(rate)
  if (cost <= 0.05) return 'Economical'
  if (cost <= 0.25) return 'Standard'
  return 'Premium'
}

function costColorClass(rate) {
  const cost = calcCostThb(rate)
  if (cost <= 0.05) return 'text-emerald-700'
  if (cost <= 0.25) return 'text-amber-700'
  return 'text-purple-700'
}

function costBadgeClass(rate) {
  const cost = calcCostThb(rate)
  if (cost <= 0.05) return 'bg-emerald-100 text-emerald-800'
  if (cost <= 0.25) return 'bg-amber-100 text-amber-800'
  return 'bg-purple-100 text-purple-800'
}

const lowestCostModel = computed(() => {
  if (tableRows.value.length === 0) return {}
  return [...tableRows.value].sort((a, b) => calcCostThb(a) - calcCostThb(b))[0]
})

const highestCostModel = computed(() => {
  if (tableRows.value.length === 0) return {}
  return [...tableRows.value].sort((a, b) => calcCostThb(b) - calcCostThb(a))[0]
})

const avgRequestCostThb = computed(() => {
  if (tableRows.value.length === 0) return 0
  const sum = tableRows.value.reduce((acc, r) => acc + calcCostThb(r), 0)
  return sum / tableRows.value.length
})

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
      id:                rate.id,
      modelId:           rate.modelId,
      inputMultiplier:   rate.inputMultiplier,
      outputMultiplier:  rate.outputMultiplier,
      inputPricePer1m:   rate.inputPricePer1m,
      outputPricePer1m:  rate.outputPricePer1m
    })
    rate.id       = saved.data.id
    rate.isCustom = true
    toast.success(`Credit rate for '${rate.displayName}' saved successfully!`)
  } catch (e) {
    toast.error(e.response?.data?.message || 'Save failed')
  } finally {
    actionIdx.value = -1
  }
}

async function resetRate(rate, idx) {
  const isConfirmed = await confirm({
    title: 'Reset Credit Multipliers',
    message: `Are you sure you want to reset credit rates for "${rate.displayName}" back to system defaults?`,
    confirmText: 'Reset Multipliers',
    type: 'warning'
  })
  if (!isConfirmed) return

  actionIdx.value = idx
  try {
    if (rate.id) {
      await adminApi.deleteCreditRate(rate.id)
    }
    rate.id               = null
    rate.inputMultiplier  = 1.0
    rate.outputMultiplier = 2.0
    rate.inputPricePer1m  = 0.0
    rate.outputPricePer1m = 0.0
    rate.isCustom         = false
    toast.success(`Credit rate for '${rate.displayName}' reset to defaults!`)
  } catch (e) {
    toast.error(e.response?.data?.message || 'Reset failed')
  } finally {
    actionIdx.value = -1
  }
}
</script>
