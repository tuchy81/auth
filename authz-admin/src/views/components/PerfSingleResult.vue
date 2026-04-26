<template>
  <div>
    <!-- 4 KPI cards (el-statistic) -->
    <el-row :gutter="14">
      <el-col :span="6">
        <el-card shadow="never">
          <el-statistic :value="data.total_requests" title="총 요청">
            <template #suffix>건</template>
          </el-statistic>
          <div class="sub">{{ data.duration_sec }}초간</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <el-statistic :value="data.rps" :precision="0" title="처리량">
            <template #suffix>RPS</template>
          </el-statistic>
          <div class="sub">동시성 {{ data.concurrency }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <el-statistic :value="data.latency_us.p99" :precision="0" title="P99 지연" :value-style="{ color: latColor(data.latency_us.p99) }">
            <template #suffix>μs</template>
          </el-statistic>
          <div class="sub">목표 &lt;5,000μs (5ms)</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <el-statistic :value="data.error_requests" title="에러" :value-style="{ color: data.error_requests ? '#ef4444' : '#16a34a' }" />
          <div class="sub">{{ ((1 - data.error_requests / data.total_requests) * 100).toFixed(2) }}% 성공</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Histogram + Percentile/Distribution -->
    <el-row :gutter="14" style="margin-top:14px;">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header><b>지연 분포 (히스토그램)</b></template>
          <v-chart :option="histogramOption" :autoresize="true" style="height: 260px;" />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header><b>퍼센타일</b></template>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="min">{{ formatLat(data.latency_us.min) }}</el-descriptions-item>
            <el-descriptions-item label="avg"><b>{{ formatLat(data.latency_us.avg) }}</b></el-descriptions-item>
            <el-descriptions-item label="P50">{{ formatLat(data.latency_us.p50) }}</el-descriptions-item>
            <el-descriptions-item label="P90">{{ formatLat(data.latency_us.p90) }}</el-descriptions-item>
            <el-descriptions-item label="P95">{{ formatLat(data.latency_us.p95) }}</el-descriptions-item>
            <el-descriptions-item label="P99">
              <b :style="{ color: latColor(data.latency_us.p99) }">{{ formatLat(data.latency_us.p99) }}</b>
            </el-descriptions-item>
            <el-descriptions-item label="P99.9">{{ formatLat(data.latency_us.p999) }}</el-descriptions-item>
            <el-descriptions-item label="max">{{ formatLat(data.latency_us.max) }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
        <el-card shadow="never" style="margin-top:14px;">
          <template #header><b>응답 분포</b></template>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="허용 (allowed)">
              <el-tag size="small" type="success">{{ data.allowed.toLocaleString() }}</el-tag>
              ({{ pct(data.allowed) }}%)
            </el-descriptions-item>
            <el-descriptions-item label="거부 (denied)">
              <el-tag size="small" type="warning">{{ data.denied.toLocaleString() }}</el-tag>
              ({{ pct(data.denied) }}%)
            </el-descriptions-item>
            <el-descriptions-item label="에러">
              <el-tag size="small" type="danger">{{ data.error_requests.toLocaleString() }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <!-- Timeline -->
    <el-card v-if="data.timeline" shadow="never" style="margin-top:14px;">
      <template #header><b>시간별 RPS</b></template>
      <v-chart :option="timelineOption" :autoresize="true" style="height: 220px;" />
    </el-card>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ data: Object })

const histogramOption = computed(() => {
  const buckets = props.data.histogram
  return {
    grid: { left: 50, right: 20, top: 20, bottom: 50 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: {
      type: 'category',
      data: buckets.map(b => b.label),
      axisLabel: { fontSize: 10, rotate: 30 }
    },
    yAxis: { type: 'value', axisLabel: { fontSize: 10 } },
    series: [{
      type: 'bar',
      data: buckets.map((b, i) => ({
        value: b.count,
        itemStyle: { color: i <= 4 ? '#16a34a' : i <= 6 ? '#eab308' : '#ef4444' }
      })),
      label: {
        show: true, position: 'top', fontSize: 10,
        formatter: (p) => p.value > 0 ? p.value.toLocaleString() : ''
      }
    }]
  }
})

const timelineOption = computed(() => {
  const tl = props.data.timeline || []
  return {
    grid: { left: 50, right: 20, top: 20, bottom: 30 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: tl.map(t => `${t.sec}s`),
      axisLabel: { fontSize: 10 }
    },
    yAxis: { type: 'value', name: 'RPS', axisLabel: { fontSize: 10 } },
    series: [{
      type: 'bar', name: 'RPS', data: tl.map(t => t.rps),
      itemStyle: { color: '#3b82f6' },
      label: { show: true, position: 'top', fontSize: 10 }
    }]
  }
})

function pct (n) {
  const total = props.data.success_requests || 1
  return ((n / total) * 100).toFixed(1)
}
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

<style scoped>
.sub { color: #94a3b8; font-size: 12px; padding-top: 4px; }
</style>
