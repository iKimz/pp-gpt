<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-xl font-bold text-[#1a1b22] font-heading flex items-center gap-2">
          <span>📊</span> Executive AI Analytics
        </h1>
        <p class="text-sm text-[#4d4732] mt-0.5">C-Level token consumption and model usage breakdown</p>
      </div>
      <div class="flex items-center gap-2">
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
          Refresh Data
        </button>
      </div>
    </div>

    <!-- Time Period Filter Bar -->
    <div class="glass rounded-xl p-4 mb-6 border border-[#e8e7f1] grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4 items-end">
      <div>
        <label class="label">Time Period</label>
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
          Search
        </button>
      </div>
    </div>

    <!-- Loading shimmer -->
    <div v-if="loading" class="space-y-4">
      <div class="grid grid-cols-4 gap-4">
        <div v-for="i in 4" :key="i" class="shimmer h-24 rounded-xl" />
      </div>
      <div class="grid grid-cols-3 gap-6">
        <div class="shimmer h-80 rounded-xl col-span-2" />
        <div class="shimmer h-80 rounded-xl col-span-1" />
      </div>
    </div>

    <div v-else class="space-y-6">
      <!-- Top Executive KPI Metric Cards -->
      <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div class="glass rounded-xl p-4 border border-[#e8e7f1]">
          <p class="text-xs font-semibold text-[#4d4732] uppercase tracking-wider">Total Tokens Consumed</p>
          <p class="text-2xl font-bold font-mono text-[#1a1b22] mt-1">
            {{ formatNumber(totals.totalTokens) }}
          </p>
          <span class="text-[11px] text-[#4d4732]">Across all groups & models</span>
        </div>

        <div class="glass rounded-xl p-4 border border-[#e8e7f1]">
          <p class="text-xs font-semibold text-[#4d4732] uppercase tracking-wider">Input Tokens</p>
          <p class="text-2xl font-bold font-mono text-blue-600 mt-1">
            {{ formatNumber(totals.inputTokens) }}
          </p>
          <span class="text-[11px] text-[#4d4732]">{{ inputPercentage }}% of total usage</span>
        </div>

        <div class="glass rounded-xl p-4 border border-[#e8e7f1]">
          <p class="text-xs font-semibold text-[#4d4732] uppercase tracking-wider">Output Tokens</p>
          <p class="text-2xl font-bold font-mono text-purple-600 mt-1">
            {{ formatNumber(totals.outputTokens) }}
          </p>
          <span class="text-[11px] text-[#4d4732]">{{ outputPercentage }}% of total usage</span>
        </div>

        <div class="glass rounded-xl p-4 border border-[#e8e7f1]">
          <p class="text-xs font-semibold text-[#4d4732] uppercase tracking-wider">Total Credits Consumed</p>
          <p class="text-2xl font-bold font-mono text-amber-700 mt-1">
            {{ formatNumber(totals.totalCredits) }}
          </p>
          <span class="text-[11px] text-[#4d4732]">{{ uniqueGroupsCount }} groups, {{ uniqueModelsCount }} models</span>
        </div>
      </div>

      <!-- Charts Section -->
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <!-- Chart 1: Bar Chart by Group & Model with View Switcher -->
        <div class="glass rounded-xl p-5 border border-[#e8e7f1] lg:col-span-2 flex flex-col justify-between">
          <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4">
            <div>
              <h2 class="text-base font-bold text-[#1a1b22] font-heading">Group & Model Consumption</h2>
              <p class="text-xs text-[#4d4732]">Token breakdown by User Group and AI Model</p>
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
                title="Side-by-side bars per group"
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
                title="Stacked bars per group"
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
                title="Total per model"
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

        <!-- Chart 2: Cost / Credit Breakdown Donut Chart -->
        <div class="glass rounded-xl p-5 border border-[#e8e7f1] flex flex-col justify-between">
          <div class="mb-4">
            <h2 class="text-base font-bold text-[#1a1b22] font-heading">Cost / Credit Breakdown</h2>
            <p class="text-xs text-[#4d4732]">Total credits consumed per AI Model</p>
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
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in sortedData" :key="item.groupId + ':' + item.modelId">
                <td class="font-medium text-[#1a1b22]">{{ item.groupName }}</td>
                <td>
                  <span class="font-medium text-xs text-[#705d00]">{{ item.modelName }}</span>
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
              </tr>
              <tr v-if="analyticsData.length === 0">
                <td colspan="6" class="text-center py-8 text-[#4d4732] text-sm">
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

const apexchart = VueApexCharts

const analyticsData = ref([])
const loading       = ref(true)

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
    analyticsData.value = res.data || []
  } catch (e) {
    console.error('Failed to load analytics:', e)
  } finally {
    loading.value = false
  }
}

// ─── Computed Metrics ─────────────────────────────────────────────────

const totals = computed(() => {
  return analyticsData.value.reduce(
    (acc, curr) => {
      acc.inputTokens  += Number(curr.totalInputTokens || 0)
      acc.outputTokens += Number(curr.totalOutputTokens || 0)
      acc.totalTokens  += Number(curr.totalTokens || 0)
      acc.totalCredits += Number(curr.totalCredits || 0)
      return acc
    },
    { inputTokens: 0, outputTokens: 0, totalTokens: 0, totalCredits: 0 }
  )
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

const chartViewMode = ref('group-clustered') // 'group-clustered', 'group-stacked', 'by-model'

// Group-based Series (Group Name on X-Axis, Models as Series)
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

// Single Model Series (Models on X-Axis)
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

// Chart 2: Donut Chart for Cost / Credit Breakdown per Model
const modelCreditMap = computed(() => {
  const map = new Map()
  analyticsData.value.forEach(d => {
    const name = d.modelName || 'Unknown Model'
    const credits = Number(d.totalCredits || 0)
    map.set(name, (map.get(name) || 0) + credits)
  })
  return map
})

const donutChartSeries = computed(() => {
  return Array.from(modelCreditMap.value.values())
})

const donutChartOptions = computed(() => {
  const labels = Array.from(modelCreditMap.value.keys())
  const colors = ['#059669', '#0284c7', '#7c3aed', '#d97706', '#ffd700', '#db2777', '#4f46e5']

  return {
    chart: {
      type: 'donut',
      background: 'transparent',
      fontFamily: 'Inter, sans-serif'
    },
    theme: { mode: 'light' },
    labels,
    colors: colors.slice(0, Math.max(labels.length, 1)),
    stroke: { colors: ['#ffffff'], width: 2 },
    legend: {
      position: 'bottom',
      labels: { colors: '#1a1b22' }
    },
    dataLabels: {
      enabled: true,
      formatter: (val) => val.toFixed(1) + '%'
    },
    tooltip: {
      theme: 'light',
      y: { formatter: val => Number(val).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' credits' }
    }
  }
})

function exportCsv() {
  if (!analyticsData.value || analyticsData.value.length === 0) {
    alert('No analytics data available to export')
    return
  }
  const headers = ['Group ID', 'Group Name', 'Model ID', 'Model Name', 'Input Tokens', 'Output Tokens', 'Total Tokens', 'Total Credits']
  const rows = analyticsData.value.map(d => [
    `"${d.groupId || ''}"`,
    `"${(d.groupName || '').replace(/"/g, '""')}"`,
    `"${d.modelId || ''}"`,
    `"${(d.modelName || '').replace(/"/g, '""')}"`,
    d.totalInputTokens || 0,
    d.totalOutputTokens || 0,
    d.totalTokens || 0,
    d.totalCredits || 0
  ])
  const csvContent = 'data:text/csv;charset=utf-8,\uFEFF' + [headers.join(','), ...rows.map(e => e.join(','))].join('\n')
  const encodedUri = encodeURI(csvContent)
  const link = document.createElement('a')
  link.setAttribute('href', encodedUri)
  link.setAttribute('download', `ai_analytics_${startDate.value || 'all'}_to_${endDate.value || 'all'}.csv`)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}
</script>
