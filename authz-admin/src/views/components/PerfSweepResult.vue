<template>
  <div>
    <el-table :data="data.levels" size="small" border>
      <el-table-column prop="concurrency" label="동시성" width="100" align="center">
        <template #default="{ row }"><b>{{ row.concurrency }}</b></template>
      </el-table-column>
      <el-table-column label="총 요청" width="120" align="right">
        <template #default="{ row }">{{ row.total_requests.toLocaleString() }}</template>
      </el-table-column>
      <el-table-column label="RPS" width="100" align="right">
        <template #default="{ row }"><b>{{ row.rps.toFixed(0) }}</b></template>
      </el-table-column>
      <el-table-column label="에러" width="80" align="right">
        <template #default="{ row }">
          <el-tag size="small" :type="row.error_requests ? 'danger' : 'success'">{{ row.error_requests }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="P50" width="100" align="right">
        <template #default="{ row }">{{ formatLat(row.latency_us.p50) }}</template>
      </el-table-column>
      <el-table-column label="P95" width="100" align="right">
        <template #default="{ row }">{{ formatLat(row.latency_us.p95) }}</template>
      </el-table-column>
      <el-table-column label="P99" width="120" align="right">
        <template #default="{ row }">
          <b :style="{ color: latColor(row.latency_us.p99) }">{{ formatLat(row.latency_us.p99) }}</b>
        </template>
      </el-table-column>
      <el-table-column label="max" width="100" align="right">
        <template #default="{ row }">{{ formatLat(row.latency_us.max) }}</template>
      </el-table-column>
      <el-table-column label="avg" width="100" align="right">
        <template #default="{ row }">{{ formatLat(row.latency_us.avg) }}</template>
      </el-table-column>
    </el-table>

    <el-row :gutter="14" style="margin-top:14px;">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><b>RPS vs 동시성</b></template>
          <v-chart :option="rpsOption" :autoresize="true" style="height: 240px;" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><b>P99 지연 vs 동시성</b></template>
          <v-chart :option="p99Option" :autoresize="true" style="height: 240px;" />
        </el-card>
      </el-col>
    </el-row>

    <el-alert type="info" :closable="false" style="margin-top:14px;">
      💡 동시성을 높일수록 RPS는 보통 증가하다 어느 시점에서 포화됩니다. 그 직전 구간이 시스템의 최적 동작점입니다.
      P99이 5ms 목표(스펙 §3.2)를 넘으면 캐시 미스·GC pause를 의심해보세요.
    </el-alert>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ data: Object })

const concurrencies = computed(() => props.data.levels.map(l => `c=${l.concurrency}`))

const rpsOption = computed(() => ({
  grid: { left: 60, right: 20, top: 20, bottom: 40 },
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: concurrencies.value, axisLabel: { fontSize: 11 } },
  yAxis: { type: 'value', name: 'RPS', axisLabel: { fontSize: 10 } },
  series: [{
    type: 'line', smooth: true, name: 'RPS',
    data: props.data.levels.map(l => Math.round(l.rps)),
    lineStyle: { width: 3, color: '#3b82f6' },
    itemStyle: { color: '#3b82f6' },
    label: { show: true, position: 'top', formatter: (p) => p.value.toLocaleString(), fontSize: 11 },
    areaStyle: { color: 'rgba(59, 130, 246, 0.1)' }
  }]
}))

const p99Option = computed(() => ({
  grid: { left: 60, right: 20, top: 20, bottom: 40 },
  tooltip: { trigger: 'axis', valueFormatter: (v) => formatLat(v) },
  xAxis: { type: 'category', data: concurrencies.value, axisLabel: { fontSize: 11 } },
  yAxis: { type: 'value', name: 'μs', axisLabel: { fontSize: 10, formatter: (v) => formatLat(v) } },
  series: [{
    type: 'line', smooth: true, name: 'P99',
    data: props.data.levels.map(l => l.latency_us.p99 || 0),
    lineStyle: { width: 3, color: '#ef4444' },
    itemStyle: { color: '#ef4444' },
    label: { show: true, position: 'top', formatter: (p) => formatLat(p.value), fontSize: 11 },
    areaStyle: { color: 'rgba(239, 68, 68, 0.08)' }
  }]
}))

function formatLat (us) {
  if (us == null) return '-'
  if (us < 1000) return us + ' μs'
  if (us < 1_000_000) return (us / 1000).toFixed(2) + ' ms'
  return (us / 1_000_000).toFixed(2) + ' s'
}
function latColor (us) {
  if (us == null) return '#111827'
  if (us < 1000) return '#16a34a'
  if (us < 5000) return '#eab308'
  return '#ef4444'
}
</script>
