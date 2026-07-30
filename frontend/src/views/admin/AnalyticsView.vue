<template>
  <div>
    <!-- Page Header & Global Controls Bar -->
    <div class="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-6">
      <div>
        <h1 class="text-xl font-bold text-[#1a1b22] font-heading flex items-center gap-2">
          <span>📊</span> Executive AI Analytics & Cost Intelligence
        </h1>
        <p class="text-sm text-[#4d4732] mt-0.5">Comprehensive AI token consumption, request metrics, and financial breakdown</p>
      </div>

      <!-- Controls: Currency Switcher + CSV + Refresh -->
      <div class="flex items-center gap-3 shrink-0">
        <!-- Interactive Currency Switcher -->
        <div class="flex items-center gap-1 bg-[#f4f2fd] p-1 rounded-xl border border-[#e8e7f1] text-xs">
          <button
            @click="currency = 'THB'"
            :class="[
              'px-2.5 py-1 rounded-lg font-bold transition-all duration-200 flex items-center gap-1',
              currency === 'THB'
                ? 'bg-brand-500 text-white shadow-sm'
                : 'text-[#4d4732] hover:text-[#1a1b22] hover:bg-white'
            ]"
          >
            <span>฿</span> THB
          </button>
          <button
            @click="currency = 'USD'"
            :class="[
              'px-2.5 py-1 rounded-lg font-bold transition-all duration-200 flex items-center gap-1',
              currency === 'USD'
                ? 'bg-brand-500 text-white shadow-sm'
                : 'text-[#4d4732] hover:text-[#1a1b22] hover:bg-white'
            ]"
          >
            <span>$</span> USD
          </button>
        </div>

        <button @click="exportCsv" class="btn-secondary text-xs flex items-center gap-1.5" :disabled="loading || !analyticsData.length">
          <svg class="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
          Export CSV
        </button>

        <button @click="loadData" class="btn-secondary text-xs flex items-center gap-1.5" :disabled="loading">
          <svg class="w-3.5 h-3.5" :class="{ 'animate-spin': loading }" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"/>
          </svg>
          Refresh
        </button>
      </div>
    </div>

    <!-- Time Period Filter Bar -->
    <div class="glass rounded-xl p-4 mb-6 border border-[#e8e7f1] grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4 items-end">
      <div>
        <label class="label">Time Period Preset</label>
        <select 
          v-model="presetRange" 
          @change="handlePresetChange"
          class="input-field py-1.5 text-xs"
        >
          <option value="all">🌐 All Time</option>
          <option value="7d">📅 Last 7 Days</option>
          <option value="30d">📅 Last 30 Days</option>
          <option value="60d">📅 Last 60 Days</option>
          <option value="90d">📅 Last 90 Days</option>
          <option value="current_month">📆 Current Month</option>
          <option value="custom">✏️ Custom Range</option>
        </select>
      </div>

      <div>
        <label class="label">Start Date</label>
        <input 
          type="date" 
          v-model="startDate" 
          @change="handleDateChange"
          class="input-field py-1.5 text-xs"
        />
      </div>

      <div>
        <label class="label">End Date</label>
        <input 
          type="date" 
          v-model="endDate" 
          @change="handleDateChange"
          class="input-field py-1.5 text-xs"
        />
      </div>

      <div class="flex items-center gap-2">
        <button @click="resetFilters" class="btn-secondary py-1.5 text-xs flex-1">Reset</button>
        <button @click="loadData" class="btn-primary py-1.5 text-xs flex-1 flex items-center justify-center gap-1">
          <svg class="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
          </svg>
          Filter
        </button>
      </div>
    </div>

    <!-- Loading Shimmer -->
    <div v-if="loading" class="space-y-4">
      <div class="grid grid-cols-5 gap-4">
        <div v-for="i in 5" :key="i" class="shimmer h-24 rounded-xl" />
      </div>
      <div class="grid grid-cols-3 gap-6">
        <div class="shimmer h-80 rounded-xl col-span-2" />
        <div class="shimmer h-80 rounded-xl col-span-1" />
      </div>
    </div>

    <div v-else class="space-y-6">
      <!-- Top Executive KPI Metric Cards (5 Cards) -->
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
        <!-- Card 1: Total AI Requests Processed -->
        <div class="glass rounded-xl p-4 border border-[#e8e7f1]">
          <p class="text-xs font-semibold text-[#4d4732] uppercase tracking-wider">AI Requests Processed</p>
          <p class="text-2xl font-bold font-mono text-[#1a1b22] mt-1">
            {{ formatNumber(totals.totalRequests) }}
          </p>
          <span class="text-[11px] text-[#4d4732] flex items-center gap-1 mt-0.5">
            <span>Avg:</span>
            <strong class="font-mono text-[#1a1b22]">{{ formatCurrency(avgCostPerRequest) }}</strong>
            <span>/ chat</span>
          </span>
        </div>

        <!-- Card 2: Total Tokens Consumed -->
        <div class="glass rounded-xl p-4 border border-[#e8e7f1]">
          <p class="text-xs font-semibold text-[#4d4732] uppercase tracking-wider">Total Tokens</p>
          <p class="text-2xl font-bold font-mono text-[#1a1b22] mt-1">
            {{ formatNumber(totals.totalTokens) }}
          </p>
          <span class="text-[11px] text-[#4d4732]">Across {{ uniqueGroupsCount }} groups & {{ uniqueModelsCount }} models</span>
        </div>

        <!-- Card 3: Input / Output Token Ratio -->
        <div class="glass rounded-xl p-4 border border-[#e8e7f1]">
          <div class="flex items-center justify-between">
            <p class="text-xs font-semibold text-[#4d4732] uppercase tracking-wider">Token Ratio</p>
            <span class="text-[10px] font-mono text-gray-500">{{ inputPercentage }}% In</span>
          </div>
          <!-- Ratio Bar -->
          <div class="w-full h-2 rounded-full bg-purple-100 overflow-hidden flex mt-2.5">
            <div class="bg-blue-500 h-full" :style="{ width: inputPercentage + '%' }" title="Input Tokens" />
            <div class="bg-purple-500 h-full" :style="{ width: outputPercentage + '%' }" title="Output Tokens" />
          </div>
          <div class="flex justify-between text-[11px] font-mono mt-2">
            <span class="text-blue-600 font-semibold">{{ formatNumber(totals.inputTokens) }} In</span>
            <span class="text-purple-600 font-semibold">{{ formatNumber(totals.outputTokens) }} Out</span>
          </div>
        </div>

        <!-- Card 4: Total Credits Consumed -->
        <div class="glass rounded-xl p-4 border border-[#e8e7f1]">
          <p class="text-xs font-semibold text-[#4d4732] uppercase tracking-wider">Credits Consumed</p>
          <p class="text-2xl font-bold font-mono text-amber-700 mt-1">
            {{ formatNumber(totals.totalCredits) }}
          </p>
          <span class="text-[11px] text-[#4d4732]">System quota deductions</span>
        </div>

        <!-- Card 5: Est. Total Expenditure -->
        <div class="glass rounded-xl p-4 border border-emerald-200 bg-emerald-50/30">
          <p class="text-xs font-semibold text-emerald-800 uppercase tracking-wider">Total Est. Expenditure</p>
          <p class="text-2xl font-bold font-mono text-emerald-700 mt-1">
            {{ formatCurrency(totalsCost) }}
          </p>
          <span class="text-[11px] text-emerald-600 font-mono">
            {{ currency === 'THB' ? `~$${totals.totalCostUsd.toFixed(2)} USD` : `~฿${(totals.totalCostUsd * 35.5).toFixed(2)} THB` }}
          </span>
        </div>
      </div>

      <!-- Executive Widgets: Top Groups Leaderboard & Model Usage Pattern -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <!-- Widget 1: Top Consuming User Groups Leaderboard -->
        <div class="glass rounded-xl p-5 border border-[#e8e7f1] flex flex-col justify-between">
          <div class="flex items-center justify-between mb-4">
            <div>
              <h2 class="text-base font-bold text-[#1a1b22] font-heading flex items-center gap-2">
                <span>🏆</span> Top Consuming Groups
              </h2>
              <p class="text-xs text-[#4d4732]">Teams ranked by spend and token volume</p>
            </div>
            <span class="text-xs font-mono text-brand-500 font-semibold">Share of Total</span>
          </div>

          <div v-if="topGroupsLeaderboard.length === 0" class="py-8 text-center text-xs text-[#4d4732]">
            No consumption recorded yet.
          </div>
          <div v-else class="space-y-3.5">
            <div v-for="(group, idx) in topGroupsLeaderboard" :key="group.groupName" class="space-y-1">
              <div class="flex items-center justify-between text-xs">
                <div class="flex items-center gap-2">
                  <span class="font-bold text-sm" :class="medalColor(idx)">
                    {{ idx === 0 ? '🥇' : idx === 1 ? '🥈' : idx === 2 ? '🥉' : '#' + (idx + 1) }}
                  </span>
                  <span class="font-bold text-[#1a1b22]">{{ group.groupName }}</span>
                  <span class="text-[11px] text-gray-400">({{ formatNumber(group.totalTokens) }} tokens)</span>
                </div>
                <div class="font-mono font-bold text-emerald-700">
                  {{ formatCurrency(group.cost) }}
                  <span class="text-[10px] text-gray-500 font-normal ml-1">({{ group.share.toFixed(1) }}%)</span>
                </div>
              </div>
              <!-- Progress Bar -->
              <div class="w-full h-2 rounded-full bg-gray-100 overflow-hidden">
                <div 
                  class="h-full rounded-full transition-all duration-500" 
                  :class="barColor(idx)"
                  :style="{ width: Math.max(group.share, 2) + '%' }" 
                />
              </div>
            </div>
          </div>
        </div>

        <!-- Widget 2: Model Usage Profile & Efficiency Breakdown -->
        <div class="glass rounded-xl p-5 border border-[#e8e7f1] flex flex-col justify-between">
          <div class="flex items-center justify-between mb-4">
            <div>
              <h2 class="text-base font-bold text-[#1a1b22] font-heading flex items-center gap-2">
                <span>🤖</span> Model Profile & Efficiency
              </h2>
              <p class="text-xs text-[#4d4732]">Usage pattern classification (Context vs Output generation)</p>
            </div>
          </div>

          <div v-if="modelProfileBreakdown.length === 0" class="py-8 text-center text-xs text-[#4d4732]">
            No model data recorded yet.
          </div>
          <div v-else class="space-y-3.5">
            <div v-for="m in modelProfileBreakdown" :key="m.modelName" class="p-2.5 rounded-lg border border-[#e8e7f1] bg-white/50 flex items-center justify-between">
              <div>
                <div class="flex items-center gap-2">
                  <span class="font-bold text-xs text-[#1a1b22]">{{ m.modelName }}</span>
                  <span class="text-[10px] px-1.5 py-0.2 rounded font-medium" :class="m.badgeClass">
                    {{ m.profileType }}
                  </span>
                </div>
                <div class="text-[11px] text-gray-500 font-mono mt-0.5">
                  In: {{ formatNumber(m.inputTokens) }} | Out: {{ formatNumber(m.outputTokens) }}
                </div>
              </div>
              <div class="text-right font-mono">
                <div class="text-xs font-bold text-brand-400">{{ formatCurrency(m.cost) }}</div>
                <div class="text-[10px] text-gray-400">{{ m.inputRatio.toFixed(0) }}% In / {{ m.outputRatio.toFixed(0) }}% Out</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Charts Section -->
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <!-- Chart 1: Bar Chart by Group & Model -->
        <div class="glass rounded-xl p-5 border border-[#e8e7f1] lg:col-span-2 flex flex-col justify-between">
          <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4">
            <div>
              <h2 class="text-base font-bold text-[#1a1b22] font-heading">Group & Model Volume</h2>
              <p class="text-xs text-[#4d4732]">Token consumption breakdown by User Group and Model</p>
            </div>
            <!-- View Mode Switcher -->
            <div class="flex items-center gap-1 bg-[#f4f2fd] p-1 rounded-xl border border-[#e8e7f1] text-xs shrink-0 self-start sm:self-auto">
              <button
                @click="chartViewMode = 'group-clustered'"
                :class="[
                  'px-2.5 py-1 rounded-lg font-medium transition-all duration-200',
                  chartViewMode === 'group-clustered'
                    ? 'bg-[#ffd700] text-[#1a1b22] font-semibold shadow-sm'
                    : 'text-[#4d4732] hover:text-[#1a1b22] hover:bg-white'
                ]"
              >
                📊 Clustered
              </button>
              <button
                @click="chartViewMode = 'group-stacked'"
                :class="[
                  'px-2.5 py-1 rounded-lg font-medium transition-all duration-200',
                  chartViewMode === 'group-stacked'
                    ? 'bg-[#ffd700] text-[#1a1b22] font-semibold shadow-sm'
                    : 'text-[#4d4732] hover:text-[#1a1b22] hover:bg-white'
                ]"
              >
                🥞 Stacked
              </button>
              <button
                @click="chartViewMode = 'by-model'"
                :class="[
                  'px-2.5 py-1 rounded-lg font-medium transition-all duration-200',
                  chartViewMode === 'by-model'
                    ? 'bg-[#ffd700] text-[#1a1b22] font-semibold shadow-sm'
                    : 'text-[#4d4732] hover:text-[#1a1b22] hover:bg-white'
                ]"
              >
                🤖 By Model
              </button>
            </div>
          </div>

          <div v-if="analyticsData.length === 0" class="h-64 flex items-center justify-center text-[#4d4732] text-xs">
            No token usage recorded yet. Stream chat messages to populate.
          </div>
          <div v-else class="w-full h-72">
            <apexchart
              :key="chartViewMode"
              type="bar"
              height="100%"
              :options="activeChartOptions"
              :series="activeChartSeries"
            />
          </div>
        </div>

        <!-- Chart 2: Cost & Credits Donut Chart -->
        <div class="glass rounded-xl p-5 border border-[#e8e7f1] flex flex-col justify-between">
          <div class="mb-4">
            <h2 class="text-base font-bold text-[#1a1b22] font-heading">Expenditure Distribution</h2>
            <p class="text-xs text-[#4d4732]">Financial spend share per AI Model</p>
          </div>

          <div v-if="totals.totalCredits === 0" class="h-64 flex items-center justify-center text-[#4d4732] text-xs">
            No credit usage recorded yet.
          </div>
          <div v-else class="w-full h-72 flex items-center justify-center">
            <apexchart
              type="donut"
              width="100%"
              height="100%"
              :options="donutChartOptions"
              :series="donutChartSeries"
            />
          </div>
        </div>
      </div>

      <!-- Detailed Data Grid Table -->
      <div class="glass rounded-xl p-5 border border-[#e8e7f1]">
        <div class="flex items-center justify-between mb-4">
          <div>
            <h2 class="text-base font-bold text-[#1a1b22] font-heading">Raw Consumption Data Grid</h2>
            <p class="text-xs text-[#4d4732]">Detailed metric records grouped by team and model</p>
          </div>
        </div>

        <div class="table-wrapper">
          <table class="data-table">
            <thead>
              <tr>
                <th @click="sortBy('groupName')" class="cursor-pointer hover:text-white">
                  User Group {{ sortKey === 'groupName' ? (sortOrder === 1 ? '▲' : '▼') : '' }}
                </th>
                <th @click="sortBy('modelName')" class="cursor-pointer hover:text-white">
                  Model Name {{ sortKey === 'modelName' ? (sortOrder === 1 ? '▲' : '▼') : '' }}
                </th>
                <th @click="sortBy('totalRequests')" class="text-right cursor-pointer hover:text-white">
                  Requests {{ sortKey === 'totalRequests' ? (sortOrder === 1 ? '▲' : '▼') : '' }}
                </th>
                <th @click="sortBy('totalInputTokens')" class="text-right cursor-pointer hover:text-white">
                  Input Tokens {{ sortKey === 'totalInputTokens' ? (sortOrder === 1 ? '▲' : '▼') : '' }}
                </th>
                <th @click="sortBy('totalOutputTokens')" class="text-right cursor-pointer hover:text-white">
                  Output Tokens {{ sortKey === 'totalOutputTokens' ? (sortOrder === 1 ? '▲' : '▼') : '' }}
                </th>
                <th @click="sortBy('totalTokens')" class="text-right cursor-pointer hover:text-[#1a1b22]">
                  Total Tokens {{ sortKey === 'totalTokens' ? (sortOrder === 1 ? '▲' : '▼') : '' }}
                </th>
                <th @click="sortBy('totalCredits')" class="text-right cursor-pointer hover:text-[#1a1b22]">
                  Total Credits {{ sortKey === 'totalCredits' ? (sortOrder === 1 ? '▲' : '▼') : '' }}
                </th>
                <th class="text-right text-emerald-700 font-bold">
                  Est. Cost ({{ currency }})
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in sortedData" :key="item.groupId + ':' + item.modelId">
                <td class="font-medium text-[#1a1b22]">{{ item.groupName }}</td>
                <td>
                  <span class="font-medium text-xs text-[#705d00]">{{ item.modelName }}</span>
                </td>
                <td class="text-right font-mono text-[#1a1b22] font-semibold">
                  {{ formatNumber(item.totalRequests || 1) }}
                </td>
                <td class="text-right font-mono text-[#4d4732]">
                  {{ formatNumber(item.totalInputTokens) }}
                </td>
                <td class="text-right font-mono text-[#4d4732]">
                  {{ formatNumber(item.totalOutputTokens) }}
                </td>
                <td class="text-right font-mono font-semibold text-[#1a1b22]">
                  {{ formatNumber(item.totalTokens) }}
                </td>
                <td class="text-right font-mono font-bold text-[#705d00]">
                  {{ formatNumber(item.totalCredits) }}
                </td>
                <td class="text-right font-mono font-bold text-emerald-700">
                  {{ formatCurrency(rowCost(item)) }}
                </td>
              </tr>
              <tr v-if="analyticsData.length === 0">
                <td colspan="8" class="text-center py-8 text-[#4d4732] text-sm">
                  No metric records found in `dashboard_metrics`.
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import VueApexCharts from 'vue3-apexcharts'
import { adminApi } from '@/api/admin'
import { useToast } from '@/composables/useToast'

const apexchart = VueApexCharts
const toast     = useToast()

const analyticsData = ref([])
const loading       = ref(true)
const currency      = ref('THB') // 'THB' or 'USD'

const presetRange = ref('all')
const startDate   = ref('')
const endDate     = ref('')

const sortKey   = ref('totalTokens')
const sortOrder = ref(-1) // -1 desc, 1 asc

onMounted(() => {
  loadData()
})

function handlePresetChange() {
  const now = new Date()
  const todayStr = now.toISOString().split('T')[0]

  if (presetRange.value === 'all') {
    startDate.value = ''
    endDate.value = ''
  } else if (presetRange.value === '7d') {
    const d = new Date()
    d.setDate(d.getDate() - 7)
    startDate.value = d.toISOString().split('T')[0]
    endDate.value = todayStr
  } else if (presetRange.value === '30d') {
    const d = new Date()
    d.setDate(d.getDate() - 30)
    startDate.value = d.toISOString().split('T')[0]
    endDate.value = todayStr
  } else if (presetRange.value === '60d') {
    const d = new Date()
    d.setDate(d.getDate() - 60)
    startDate.value = d.toISOString().split('T')[0]
    endDate.value = todayStr
  } else if (presetRange.value === '90d') {
    const d = new Date()
    d.setDate(d.getDate() - 90)
    startDate.value = d.toISOString().split('T')[0]
    endDate.value = todayStr
  } else if (presetRange.value === 'current_month') {
    const yyyy = now.getFullYear()
    const mm = String(now.getMonth() + 1).padStart(2, '0')
    startDate.value = `${yyyy}-${mm}-01`
    endDate.value = todayStr
  }
  loadData()
}

function handleDateChange() {
  presetRange.value = 'custom'
}

function resetFilters() {
  presetRange.value = 'all'
  startDate.value = ''
  endDate.value = ''
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    const params = {}
    if (startDate.value) params.startDate = startDate.value
    if (endDate.value) params.endDate = endDate.value

    const res = await adminApi.getAnalytics(params)
    analyticsData.value = Array.isArray(res.data) ? res.data : (res.data?.metrics || res.data?.items || [])
  } catch (e) {
    console.error('Failed to load analytics:', e)
  } finally {
    loading.value = false
  }
}

// ─── Computed Totals & Metrics ─────────────────────────────────────────

const totals = computed(() => {
  return analyticsData.value.reduce(
    (acc, curr) => {
      acc.totalRequests += Number(curr.totalRequests || 1)
      acc.inputTokens   += Number(curr.totalInputTokens || 0)
      acc.outputTokens  += Number(curr.totalOutputTokens || 0)
      acc.totalTokens   += Number(curr.totalTokens || 0)
      acc.totalCredits  += Number(curr.totalCredits || 0)
      const costUsd = curr.totalCostUsd != null ? Number(curr.totalCostUsd) : (Number(curr.totalCredits || 0) * 0.0003)
      acc.totalCostUsd  += costUsd
      return acc
    },
    { totalRequests: 0, inputTokens: 0, outputTokens: 0, totalTokens: 0, totalCredits: 0, totalCostUsd: 0 }
  )
})

const totalsCost = computed(() => {
  return currency.value === 'THB' ? (totals.value.totalCostUsd * 35.50) : totals.value.totalCostUsd
})

const avgCostPerRequest = computed(() => {
  if (!totals.value.totalRequests) return 0
  return totalsCost.value / totals.value.totalRequests
})

const inputPercentage = computed(() => {
  if (!totals.value.totalTokens) return 0
  return ((totals.value.inputTokens / totals.value.totalTokens) * 100).toFixed(1)
})

const outputPercentage = computed(() => {
  if (!totals.value.totalTokens) return 0
  return ((totals.value.outputTokens / totals.value.totalTokens) * 100).toFixed(1)
})

const uniqueGroupsCount = computed(() => {
  return new Set(analyticsData.value.map(d => d.groupName)).size
})

const uniqueModelsCount = computed(() => {
  return new Set(analyticsData.value.map(d => d.modelName)).size
})

function formatNumber(num) {
  return Number(num || 0).toLocaleString()
}

function formatCurrency(val) {
  const symbol = currency.value === 'THB' ? '฿' : '$'
  return `${symbol}${Number(val || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function rowCost(item) {
  const usd = item.totalCostUsd != null ? Number(item.totalCostUsd) : (Number(item.totalCredits || 0) * 0.0003)
  return currency.value === 'THB' ? (usd * 35.50) : usd
}

// ─── Leaderboard & Model Profile ─────────────────────────────────────

const topGroupsLeaderboard = computed(() => {
  const map = new Map()
  analyticsData.value.forEach(d => {
    const name = d.groupName || 'Default Group'
    const tokens = Number(d.totalTokens || 0)
    const usd = d.totalCostUsd != null ? Number(d.totalCostUsd) : (Number(d.totalCredits || 0) * 0.0003)
    const existing = map.get(name) || { groupName: name, totalTokens: 0, costUsd: 0 }
    existing.totalTokens += tokens
    existing.costUsd += usd
    map.set(name, existing)
  })

  const totalCost = Array.from(map.values()).reduce((acc, g) => acc + g.costUsd, 0)
  return Array.from(map.values())
    .map(g => ({
      ...g,
      cost: currency.value === 'THB' ? (g.costUsd * 35.50) : g.costUsd,
      share: totalCost > 0 ? (g.costUsd / totalCost) * 100 : 0
    }))
    .sort((a, b) => b.totalTokens - a.totalTokens)
    .slice(0, 5)
})

const modelProfileBreakdown = computed(() => {
  const map = new Map()
  analyticsData.value.forEach(d => {
    const name = d.modelName || 'Unknown Model'
    const inTok = Number(d.totalInputTokens || 0)
    const outTok = Number(d.totalOutputTokens || 0)
    const usd = d.totalCostUsd != null ? Number(d.totalCostUsd) : (Number(d.totalCredits || 0) * 0.0003)
    const existing = map.get(name) || { modelName: name, inputTokens: 0, outputTokens: 0, costUsd: 0 }
    existing.inputTokens += inTok
    existing.outputTokens += outTok
    existing.costUsd += usd
    map.set(name, existing)
  })

  return Array.from(map.values()).map(m => {
    const total = m.inputTokens + m.outputTokens
    const inRatio = total > 0 ? (m.inputTokens / total) * 100 : 50
    const outRatio = total > 0 ? (m.outputTokens / total) * 100 : 50

    let profileType = 'Balanced'
    let badgeClass  = 'bg-blue-50 text-blue-700 border border-blue-200'
    if (inRatio >= 75) {
      profileType = 'High-Context RAG'
      badgeClass  = 'bg-purple-50 text-purple-700 border border-purple-200'
    } else if (outRatio >= 60) {
      profileType = 'Long-Generation'
      badgeClass  = 'bg-amber-50 text-amber-700 border border-amber-200'
    }

    return {
      ...m,
      cost: currency.value === 'THB' ? (m.costUsd * 35.50) : m.costUsd,
      inputRatio: inRatio,
      outputRatio: outRatio,
      profileType,
      badgeClass
    }
  }).sort((a, b) => b.costUsd - a.costUsd)
})

function medalColor(idx) {
  if (idx === 0) return 'text-amber-500'
  if (idx === 1) return 'text-slate-400'
  if (idx === 2) return 'text-amber-700'
  return 'text-gray-400'
}

function barColor(idx) {
  if (idx === 0) return 'bg-amber-500'
  if (idx === 1) return 'bg-slate-400'
  if (idx === 2) return 'bg-amber-700'
  return 'bg-brand-500'
}

// ─── Table Sorting ───────────────────────────────────────────────────

function sortBy(key) {
  if (sortKey.value === key) {
    sortOrder.value = -sortOrder.value
  } else {
    sortKey.value = key
    sortOrder.value = -1
  }
}

const sortedData = computed(() => {
  return [...analyticsData.value].sort((a, b) => {
    let valA = a[sortKey.value]
    let valB = b[sortKey.value]
    if (typeof valA === 'string') {
      return valA.localeCompare(valB) * sortOrder.value
    }
    return (valA - valB) * sortOrder.value
  })
})

// ─── ApexCharts Configuration ─────────────────────────────────────────

const chartViewMode = ref('group-clustered')

const groupChartSeries = computed(() => {
  const models = Array.from(new Set(analyticsData.value.map(d => d.modelName)))
  const groups = Array.from(new Set(analyticsData.value.map(d => d.groupName)))

  return models.map(model => {
    const data = groups.map(group => {
      const item = analyticsData.value.find(d => d.groupName === group && d.modelName === model)
      return item ? Number(item.totalTokens) : 0
    })
    return { name: model, data }
  })
})

const byModelSeries = computed(() => {
  const modelTotals = new Map()
  analyticsData.value.forEach(d => {
    const name = d.modelName || 'Unknown Model'
    modelTotals.set(name, (modelTotals.get(name) || 0) + Number(d.totalTokens))
  })
  return [{
    name: 'Total Tokens',
    data: Array.from(modelTotals.values())
  }]
})

const activeChartSeries = computed(() => {
  return chartViewMode.value === 'by-model' ? byModelSeries.value : groupChartSeries.value
})

const activeChartOptions = computed(() => {
  const colors = ['#705d00', '#0284c7', '#d97706', '#059669', '#db2777', '#4f46e5']

  if (chartViewMode.value === 'by-model') {
    const modelTotals = new Map()
    analyticsData.value.forEach(d => {
      const name = d.modelName || 'Unknown Model'
      modelTotals.set(name, (modelTotals.get(name) || 0) + Number(d.totalTokens))
    })
    const models = Array.from(modelTotals.keys())

    return {
      chart: { type: 'bar', toolbar: { show: false }, background: 'transparent', fontFamily: 'Inter, sans-serif' },
      theme: { mode: 'light' },
      colors,
      plotOptions: { bar: { distributed: true, borderRadius: 6, columnWidth: '45%' } },
      xaxis: {
        categories: models,
        labels: { style: { colors: '#4d4732', fontSize: '11px', fontWeight: 600 } },
        axisBorder: { color: '#e8e7f1' },
        axisTicks: { color: '#e8e7f1' }
      },
      yaxis: {
        labels: { style: { colors: '#4d4732', fontSize: '11px' }, formatter: val => (val >= 1000 ? (val / 1000).toFixed(0) + 'k' : val) }
      },
      grid: { borderColor: '#e8e7f1' },
      legend: { show: false },
      tooltip: { theme: 'light', y: { formatter: val => Number(val).toLocaleString() + ' tokens' } }
    }
  }

  const groups = Array.from(new Set(analyticsData.value.map(d => d.groupName)))
  const isStacked = chartViewMode.value === 'group-stacked'

  return {
    chart: { type: 'bar', stacked: isStacked, toolbar: { show: false }, background: 'transparent', fontFamily: 'Inter, sans-serif' },
    theme: { mode: 'light' },
    colors,
    plotOptions: { bar: { distributed: false, horizontal: false, borderRadius: 4, columnWidth: '45%' } },
    xaxis: {
      categories: groups,
      labels: { style: { colors: '#4d4732', fontSize: '11px', fontWeight: 600 } },
      axisBorder: { color: '#e8e7f1' },
      axisTicks: { color: '#e8e7f1' }
    },
    yaxis: {
      labels: { style: { colors: '#4d4732', fontSize: '11px' }, formatter: val => (val >= 1000 ? (val / 1000).toFixed(0) + 'k' : val) }
    },
    grid: { borderColor: '#e8e7f1' },
    legend: { position: 'top', horizontalAlign: 'left', labels: { colors: '#1a1b22' } },
    tooltip: { theme: 'light', y: { formatter: val => Number(val).toLocaleString() + ' tokens' } }
  }
})

const donutChartSeries = computed(() => {
  return modelProfileBreakdown.value.map(m => m.cost)
})

const donutChartOptions = computed(() => {
  const labels = modelProfileBreakdown.value.map(m => m.modelName)
  const colors = ['#059669', '#0284c7', '#7c3aed', '#d97706', '#ffd700', '#db2777', '#4f46e5']

  return {
    chart: { type: 'donut', background: 'transparent', fontFamily: 'Inter, sans-serif' },
    theme: { mode: 'light' },
    labels,
    colors: colors.slice(0, Math.max(labels.length, 1)),
    stroke: { colors: ['#ffffff'], width: 2 },
    legend: { position: 'bottom', labels: { colors: '#1a1b22' } },
    dataLabels: { enabled: true, formatter: (val) => val.toFixed(1) + '%' },
    tooltip: {
      theme: 'light',
      y: { formatter: val => formatCurrency(val) }
    }
  }
})

function exportCsv() {
  if (!analyticsData.value || analyticsData.value.length === 0) {
    toast.warning('No analytics data available to export')
    return
  }
  const curr = currency.value
  const headers = ['Group ID', 'Group Name', 'Model ID', 'Model Name', 'Requests', 'Input Tokens', 'Output Tokens', 'Total Tokens', 'Total Credits', `Est. Cost (${curr})`]
  const rows = analyticsData.value.map(d => [
    `"${d.groupId || ''}"`,
    `"${(d.groupName || '').replace(/"/g, '""')}"`,
    `"${d.modelId || ''}"`,
    `"${(d.modelName || '').replace(/"/g, '""')}"`,
    d.totalRequests || 1,
    d.totalInputTokens || 0,
    d.totalOutputTokens || 0,
    d.totalTokens || 0,
    d.totalCredits || 0,
    rowCost(d).toFixed(2)
  ])
  const csvContent = 'data:text/csv;charset=utf-8,\uFEFF' + [headers.join(','), ...rows.map(e => e.join(','))].join('\n')
  const encodedUri = encodeURI(csvContent)
  const link = document.createElement('a')
  link.setAttribute('href', encodedUri)
  link.setAttribute('download', `ai_executive_analytics_${curr}_${startDate.value || 'all'}_to_${endDate.value || 'all'}.csv`)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}
</script>
