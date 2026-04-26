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
          <span :class="latColor(row.latency_us.p99)"><b>{{ formatLat(row.latency_us.p99) }}</b></span>
        </template>
      </el-table-column>
      <el-table-column label="max" width="100" align="right">
        <template #default="{ row }">{{ formatLat(row.latency_us.max) }}</template>
      </el-table-column>
      <el-table-column label="avg" width="100" align="right">
        <template #default="{ row }">{{ formatLat(row.latency_us.avg) }}</template>
      </el-table-column>
    </el-table>

    <!-- RPS vs concurrency chart -->
    <el-row :gutter="14" style="margin-top:14px;">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><b>RPS vs 동시성</b></template>
          <svg :viewBox="`0 0 ${chartW + 40} 220`" width="100%" height="220">
            <g transform="translate(40,10)">
              <line v-for="g in 4" :key="g" x1="0" :x2="chartW"
                    :y1="180 - g*40" :y2="180 - g*40" stroke="#e5e7eb" stroke-dasharray="2 2" />
              <polyline :points="rpsPoints" fill="none" stroke="#3b82f6" stroke-width="2.5" />
              <g v-for="(l, i) in data.levels" :key="i" :transform="`translate(${i * stepX}, 0)`">
                <circle :cx="stepX/2" :cy="180 - rpsY(l)" r="4" fill="#3b82f6" />
                <text :x="stepX/2" :y="200" text-anchor="middle" font-size="10" fill="#6b7280">c={{ l.concurrency }}</text>
                <text :x="stepX/2" :y="180 - rpsY(l) - 8" text-anchor="middle" font-size="10" fill="#1f2937" font-weight="600">{{ l.rps.toFixed(0) }}</text>
              </g>
              <text x="-5" y="0" text-anchor="end" font-size="10" fill="#6b7280">{{ maxRps.toFixed(0) }}</text>
              <text x="-5" y="180" text-anchor="end" font-size="10" fill="#6b7280">0</text>
            </g>
          </svg>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><b>P99 지연 vs 동시성</b></template>
          <svg :viewBox="`0 0 ${chartW + 40} 220`" width="100%" height="220">
            <g transform="translate(40,10)">
              <line v-for="g in 4" :key="g" x1="0" :x2="chartW"
                    :y1="180 - g*40" :y2="180 - g*40" stroke="#e5e7eb" stroke-dasharray="2 2" />
              <polyline :points="p99Points" fill="none" stroke="#ef4444" stroke-width="2.5" />
              <g v-for="(l, i) in data.levels" :key="i" :transform="`translate(${i * stepX}, 0)`">
                <circle :cx="stepX/2" :cy="180 - p99Y(l)" r="4" fill="#ef4444" />
                <text :x="stepX/2" :y="200" text-anchor="middle" font-size="10" fill="#6b7280">c={{ l.concurrency }}</text>
                <text :x="stepX/2" :y="180 - p99Y(l) - 8" text-anchor="middle" font-size="10" fill="#1f2937" font-weight="600">{{ formatLat(l.latency_us.p99) }}</text>
              </g>
              <text x="-5" y="0" text-anchor="end" font-size="10" fill="#6b7280">{{ formatLat(maxP99) }}</text>
              <text x="-5" y="180" text-anchor="end" font-size="10" fill="#6b7280">0</text>
            </g>
          </svg>
        </el-card>
      </el-col>
    </el-row>

    <el-alert type="info" :closable="false" style="margin-top:14px;">
      💡 동시성을 높일수록 RPS는 보통 증가하다 어느 시점에서 포화됩니다. 그 직전 구간이 시스템의 최적 동작점입니다. P99이 5ms 목표(스펙 §3.2)를 넘으면 캐시 미스·GC pause를 의심해보세요.
    </el-alert>
  </div>
</template>

<script setup>
import { computed } from 'vue'
const props = defineProps({ data: Object })

const chartW = 460
const stepX = computed(() => chartW / Math.max(1, props.data.levels.length))
const maxRps = computed(() => Math.max(1, ...props.data.levels.map(l => l.rps)))
const maxP99 = computed(() => Math.max(1, ...props.data.levels.map(l => l.latency_us.p99 || 0)))

function rpsY (l) { return Math.round((l.rps / maxRps.value) * 160) }
function p99Y (l) { return Math.round(((l.latency_us.p99 || 0) / maxP99.value) * 160) }

const rpsPoints = computed(() => props.data.levels.map((l, i) => `${i * stepX.value + stepX.value/2},${180 - rpsY(l)}`).join(' '))
const p99Points = computed(() => props.data.levels.map((l, i) => `${i * stepX.value + stepX.value/2},${180 - p99Y(l)}`).join(' '))

function formatLat (us) {
  if (us == null) return '-'
  if (us < 1000) return us + ' μs'
  if (us < 1_000_000) return (us / 1000).toFixed(2) + ' ms'
  return (us / 1_000_000).toFixed(2) + ' s'
}
function latColor (us) {
  if (us == null) return ''
  if (us < 1000) return 'ok'
  if (us < 5000) return 'warn-soft'
  return 'warn'
}
</script>

<style scoped>
.ok { color:#16a34a; } .warn-soft { color:#eab308; } .warn { color:#ef4444; }
</style>
