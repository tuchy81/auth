<template>
  <div>
    <el-row :gutter="14">
      <el-col :span="6"><el-card shadow="never"><div class="lbl">총 요청</div><div class="val">{{ data.total_requests.toLocaleString() }}</div><div class="sub">{{ data.duration_sec }}초간</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><div class="lbl">처리량</div><div class="val">{{ data.rps.toFixed(0) }} <span class="sub">RPS</span></div><div class="sub">동시성 {{ data.concurrency }}</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><div class="lbl">P99 지연</div><div class="val" :class="latColor(data.latency_us.p99)">{{ formatLat(data.latency_us.p99) }}</div><div class="sub">목표 &lt;5ms</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><div class="lbl">에러</div><div class="val" :class="data.error_requests ? 'warn' : ''">{{ data.error_requests }} / {{ data.total_requests }}</div><div class="sub">{{ ((1 - data.error_requests / data.total_requests) * 100).toFixed(2) }}% 성공</div></el-card></el-col>
    </el-row>

    <!-- 결과 분포 -->
    <el-row :gutter="14" style="margin-top:14px;">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header><b>지연 분포 (히스토그램)</b></template>
          <svg :viewBox="`0 0 ${barW * data.histogram.length + 40} 220`" width="100%" height="220">
            <g transform="translate(34,10)">
              <line v-for="g in 4" :key="g" x1="0" :x2="barW * data.histogram.length"
                    :y1="200 - g*48" :y2="200 - g*48" stroke="#e5e7eb" stroke-dasharray="2 2" />
              <g v-for="(b, i) in data.histogram" :key="i" :transform="`translate(${i*barW}, 0)`">
                <rect :x="6" :y="200 - barH(b)" :width="barW - 12" :height="barH(b)"
                      :fill="barColor(i)" rx="2" />
                <text :x="barW/2" :y="216" text-anchor="middle" font-size="10" fill="#6b7280">{{ b.label }}</text>
                <text :x="barW/2" :y="200 - barH(b) - 4" text-anchor="middle" font-size="10" fill="#374151"
                      v-if="b.count > 0">{{ b.count.toLocaleString() }}</text>
              </g>
              <text x="-5" y="0" text-anchor="end" font-size="10" fill="#6b7280">{{ maxCount.toLocaleString() }}</text>
              <text x="-5" y="200" text-anchor="end" font-size="10" fill="#6b7280">0</text>
            </g>
          </svg>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header><b>퍼센타일</b></template>
          <table class="stat-tbl">
            <tr><td>min</td><td>{{ formatLat(data.latency_us.min) }}</td></tr>
            <tr><td>avg</td><td><b>{{ formatLat(data.latency_us.avg) }}</b></td></tr>
            <tr><td>P50</td><td>{{ formatLat(data.latency_us.p50) }}</td></tr>
            <tr><td>P90</td><td>{{ formatLat(data.latency_us.p90) }}</td></tr>
            <tr><td>P95</td><td>{{ formatLat(data.latency_us.p95) }}</td></tr>
            <tr><td>P99</td><td :class="latColor(data.latency_us.p99)"><b>{{ formatLat(data.latency_us.p99) }}</b></td></tr>
            <tr><td>P99.9</td><td>{{ formatLat(data.latency_us.p999) }}</td></tr>
            <tr><td>max</td><td>{{ formatLat(data.latency_us.max) }}</td></tr>
          </table>
        </el-card>
        <el-card shadow="never" style="margin-top:14px;">
          <template #header><b>응답 분포</b></template>
          <table class="stat-tbl">
            <tr><td>허용 (allowed)</td><td><el-tag size="small" type="success">{{ data.allowed.toLocaleString() }}</el-tag> ({{ pct(data.allowed) }}%)</td></tr>
            <tr><td>거부 (denied)</td><td><el-tag size="small" type="warning">{{ data.denied.toLocaleString() }}</el-tag> ({{ pct(data.denied) }}%)</td></tr>
            <tr><td>에러</td><td><el-tag size="small" type="danger">{{ data.error_requests.toLocaleString() }}</el-tag></td></tr>
          </table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 시간대별 RPS -->
    <el-card v-if="data.timeline" shadow="never" style="margin-top:14px;">
      <template #header><b>시간별 RPS</b></template>
      <svg :viewBox="`0 0 ${tlW * data.timeline.length + 40} 160`" width="100%" height="160">
        <g transform="translate(34,10)">
          <line v-for="g in 4" :key="g" x1="0" :x2="tlW * data.timeline.length"
                :y1="140 - g*30" :y2="140 - g*30" stroke="#e5e7eb" stroke-dasharray="2 2" />
          <g v-for="(t, i) in data.timeline" :key="i" :transform="`translate(${i*tlW}, 0)`">
            <rect :x="6" :y="140 - tlH(t)" :width="tlW - 12" :height="tlH(t)" fill="#3b82f6" rx="2" />
            <text :x="tlW/2" :y="156" text-anchor="middle" font-size="10" fill="#6b7280">{{ t.sec }}s</text>
            <text :x="tlW/2" :y="140 - tlH(t) - 4" text-anchor="middle" font-size="10" fill="#374151"
                  v-if="t.rps > 0">{{ t.rps.toLocaleString() }}</text>
          </g>
          <text x="-5" y="0" text-anchor="end" font-size="10" fill="#6b7280">{{ maxTimelineRps.toLocaleString() }}</text>
          <text x="-5" y="140" text-anchor="end" font-size="10" fill="#6b7280">0</text>
        </g>
      </svg>
    </el-card>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ data: Object })

const barW = computed(() => 60)
const tlW = computed(() => Math.max(40, Math.min(80, 600 / Math.max(1, props.data.timeline?.length || 1))))
const maxCount = computed(() => Math.max(1, ...props.data.histogram.map(b => b.count)))
const maxTimelineRps = computed(() => Math.max(1, ...(props.data.timeline || []).map(t => t.rps)))

function barH (b) { return Math.round((b.count / maxCount.value) * 200) }
function tlH (t) { return Math.round((t.rps / maxTimelineRps.value) * 130) }
function barColor (i) {
  if (i <= 4) return '#16a34a'      // <=1ms — green
  if (i <= 6) return '#eab308'      // <=5ms — yellow
  return '#ef4444'                  // >5ms — red
}
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
function pct (n) {
  const total = props.data.success_requests || 1
  return ((n / total) * 100).toFixed(1)
}
</script>

<style scoped>
.lbl { color:#6b7280; font-size:12px; }
.val { font-size: 22px; font-weight: 700; color: #111827; }
.val .sub { color:#94a3b8; font-size:13px; }
.val.ok { color: #16a34a; }
.val.warn-soft { color: #eab308; }
.val.warn { color: #ef4444; }
.sub { color: #94a3b8; font-size: 12px; }
.stat-tbl { width: 100%; border-collapse: collapse; }
.stat-tbl td { padding: 4px 8px; border-bottom: 1px dashed #f3f4f6; }
.stat-tbl td:first-child { color: #6b7280; width: 50%; }
.stat-tbl td.ok { color: #16a34a; }
.stat-tbl td.warn-soft { color: #eab308; }
.stat-tbl td.warn { color: #ef4444; }
</style>
