<template>
  <div>
    <el-card>
      <template #header>
        <div style="display:flex; align-items:center; gap:10px;">
          <el-icon><RefreshRight /></el-icon><b>캐시 전파 시나리오 테스트</b>
        </div>
      </template>

      <el-alert type="info" :closable="false" style="margin-bottom:14px;">
        쓰기 작업(권한 부여/해제, UG 멤버 추가, 메뉴 등록, 매핑 변경) 후 <b>인가 조회 결과가 캐시에 반영될 때까지의 시간</b>을
        시나리오별로 측정합니다. 각 반복은 (1) 쓰기 → (2) <code>/authz/check</code> 가 기대값을 반환할 때까지 polling → (3) cleanup 으로 구성됩니다.
      </el-alert>

      <el-form inline label-width="120px">
        <el-form-item label="반복 횟수"><el-input-number v-model="form.iterations" :min="1" :max="50" /></el-form-item>
        <el-form-item label="시나리오">
          <el-checkbox-group v-model="form.scenarios">
            <el-checkbox v-for="s in scenarioOptions" :key="s.code" :value="s.code">
              {{ s.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="Fast Sync">
          <el-switch v-model="form.fastSync" />
          <span style="color:#94a3b8; margin-left:6px; font-size:12px;">
            on: SyncWorker 즉시 호출 (rebuild만 측정) · off: 실제 1초 polling 포함
          </span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="running" @click="run"><el-icon><CaretRight /></el-icon>실행</el-button>
        </el-form-item>
      </el-form>

      <el-progress v-if="running" :percentage="progress" :stroke-width="6" status="success" />
    </el-card>

    <el-card v-if="result" style="margin-top:14px;">
      <template #header>
        <b>결과 요약</b>
        <el-tag size="small" style="margin-left:8px;" :type="result.fast_sync ? 'success' : 'info'">
          {{ result.fast_sync ? 'Fast Sync (즉시 rebuild)' : 'Natural Sync (1s polling)' }}
        </el-tag>
      </template>

      <el-table :data="resultRows" border size="small">
        <el-table-column prop="scenario" label="시나리오" width="220">
          <template #default="{ row }">
            <b>{{ row.label }}</b>
            <div class="hint">{{ row.desc }}</div>
          </template>
        </el-table-column>
        <el-table-column label="성공/시도" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.success === row.iterations ? 'success' : 'warning'">
              {{ row.success }} / {{ row.iterations }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="DB Write (μs)" align="right">
          <template #default="{ row }">
            <div v-if="row.write_us?.count">
              <div>avg <b>{{ row.write_us.avg.toLocaleString() }}</b> μs</div>
              <div class="sub">P95 {{ row.write_us.p95.toLocaleString() }} / max {{ row.write_us.max.toLocaleString() }}</div>
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="캐시 반영 (ms)" align="right">
          <template #default="{ row }">
            <div v-if="row.propagation_ms?.count">
              <div>avg <b :class="latColor(row.propagation_ms.avg)">{{ row.propagation_ms.avg.toLocaleString() }}</b> ms</div>
              <div class="sub">P95 {{ row.propagation_ms.p95 }} / P99 {{ row.propagation_ms.p99 }} / max {{ row.propagation_ms.max }}</div>
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="합계 (ms)" align="right">
          <template #default="{ row }">
            <div v-if="row.total_ms?.count">
              <b :class="latColor(row.total_ms.avg)">{{ row.total_ms.avg.toLocaleString() }}</b> ms (avg)
              <div class="sub">max {{ row.total_ms.max.toLocaleString() }} ms</div>
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- per-scenario sample chart -->
      <el-card v-for="r in resultRows.filter(x => x.samples?.length)" :key="r.scenario" shadow="never" style="margin-top:14px;">
        <template #header>
          <b>{{ r.label }} — 반복별 측정값</b>
          <span class="legend">
            쓰기 평균 {{ r.write_us.avg }}μs · 캐시 반영 평균 {{ r.propagation_ms.avg }}ms · 합계 평균 {{ r.total_ms.avg }}ms
          </span>
        </template>
        <v-chart :option="sampleChartOption(r)" :autoresize="true" style="height: 220px;" />
      </el-card>

      <el-alert type="info" :closable="false" style="margin-top:14px;">
        ⏱ <b>Fast Sync OFF</b>: 실제 운영 환경(스펙 §3.2: 권한 변경 반영 30초 이내)을 흉내낸 값.
        SyncWorker가 1초마다 polling하므로 평균 ~500ms + rebuild 시간이 일반적입니다.
        <br/>
        ⚡ <b>Fast Sync ON</b>: SyncWorker.poll()을 즉시 호출 — Outbox + DB rebuild + Pub/Sub 무효화 자체만 측정 (수십 ms).
        <br/>
        🎯 <b>측정 대상</b>: 쓰기 직후 <code>/authz/check</code> 가 기대값을 반환할 때까지의 polling 시간.
      </el-alert>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { RefreshRight, CaretRight } from '@element-plus/icons-vue'
import api from '@/api'
import { ElMessage } from 'element-plus'

const scenarioOptions = [
  { code: 'PERM_GRANT', label: 'PERM_GRANT — 권한 부여', desc: '사용자 직접 부여 → 캐시 반영' },
  { code: 'PERM_REVOKE', label: 'PERM_REVOKE — 권한 해제', desc: '직접 부여된 권한 해제 → 거부 반영' },
  { code: 'UG_MEMBER_ADD', label: 'UG_MEMBER_ADD — UG 멤버 추가', desc: '권한 있는 UG에 사용자 추가' },
  { code: 'MENU_CREATE_LEAF', label: 'MENU_CREATE_LEAF — 신규 메뉴', desc: '리프 + 매핑 + 권한 일괄 생성' },
  { code: 'MENU_ACTION_API_CHANGE', label: 'MENU_ACTION_API_CHANGE — 매핑 변경', desc: '기존 메뉴-액션에 새 API 추가' }
]

const running = ref(false)
const progress = ref(0)
const result = ref(null)
const form = reactive({ iterations: 5, scenarios: ['PERM_GRANT', 'UG_MEMBER_ADD', 'MENU_CREATE_LEAF'], fastSync: true })

const resultRows = computed(() => {
  if (!result.value) return []
  return result.value.scenarios.map(s => ({
    ...s,
    label: scenarioOptions.find(o => o.code === s.scenario)?.label || s.scenario,
    desc: scenarioOptions.find(o => o.code === s.scenario)?.desc || ''
  }))
})

async function run () {
  running.value = true
  progress.value = 5
  result.value = null
  // crude progress estimate based on number of scenarios × iterations × est. wait
  const est = form.scenarios.length * form.iterations * (form.fastSync ? 0.5 : 1.5) * 1000
  const tick = setInterval(() => { progress.value = Math.min(progress.value + 100 / (est / 200), 95) }, 200)
  try {
    const r = await api.post('/proptest/run', {
      scenarios: form.scenarios,
      iterations: form.iterations,
      fastSync: form.fastSync
    }, { timeout: 600_000 })
    result.value = r.data
    progress.value = 100
    ElMessage.success('완료')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message)
  } finally {
    clearInterval(tick)
    running.value = false
  }
}

function sampleChartOption (r) {
  const samples = r.samples || []
  return {
    grid: { left: 50, right: 20, top: 20, bottom: 30 },
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const i = params[0].dataIndex
        const s = samples[i]
        return `#${i + 1}<br/>쓰기: ${s.write_us}μs<br/>캐시반영: <b>${s.prop_ms}ms</b>`
      }
    },
    xAxis: { type: 'category', data: samples.map((_, i) => `#${i + 1}`), axisLabel: { fontSize: 10 } },
    yAxis: { type: 'value', name: 'ms', axisLabel: { fontSize: 10 } },
    series: [{
      type: 'bar', name: '캐시 반영 (ms)',
      data: samples.map(s => s.prop_ms),
      itemStyle: { color: '#3b82f6', borderRadius: [4, 4, 0, 0] },
      label: { show: true, position: 'top', fontSize: 10, formatter: '{c}ms' }
    }]
  }
}

function latColor (ms) {
  if (ms == null) return ''
  if (ms < 100) return 'ok'
  if (ms < 1500) return 'warn-soft'
  return 'warn'
}
</script>

<style scoped>
.hint { color: #94a3b8; font-size: 11px; }
.sub { color: #94a3b8; font-size: 11px; }
.legend { color: #6b7280; font-size: 12px; padding: 4px 0; }
.ok { color: #16a34a; } .warn-soft { color: #eab308; } .warn { color: #ef4444; }
code { background: #f4f6f8; padding: 1px 4px; border-radius: 3px; font-size: 12px; }
</style>
