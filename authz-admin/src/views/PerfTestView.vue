<template>
  <div class="perf">
    <el-card>
      <template #header>
        <div style="display:flex; align-items:center; gap:10px;">
          <el-icon><Lightning /></el-icon><b>인가 조회 성능 테스트</b>
          <span style="margin-left:auto; color:#6b7280;">시스템: <b>{{ app.systemCd }}</b></span>
        </div>
      </template>

      <el-radio-group v-model="mode" size="default">
        <el-radio-button value="single">단일 부하</el-radio-button>
        <el-radio-button value="sweep">단계별 Sweep (concurrency 비교)</el-radio-button>
      </el-radio-group>

      <!-- 단일 부하 -->
      <el-form v-if="mode === 'single'" inline label-width="120px" style="margin-top:14px;">
        <el-form-item label="동시성"><el-input-number v-model="single.concurrency" :min="1" :max="500" /></el-form-item>
        <el-form-item label="지속시간(초)"><el-input-number v-model="single.durationSec" :min="1" :max="60" /></el-form-item>
        <el-form-item label="사용자 샘플"><el-input-number v-model="single.userSample" :min="1" :max="200" /></el-form-item>
        <el-form-item label="URL 샘플"><el-input-number v-model="single.urlSample" :min="1" :max="1000" /></el-form-item>
        <el-form-item><el-checkbox v-model="single.warmup">실행 전 Warm-up</el-checkbox></el-form-item>
        <el-form-item><el-button type="primary" :loading="running" @click="runSingle"><el-icon><CaretRight /></el-icon>실행</el-button></el-form-item>
      </el-form>

      <!-- Sweep -->
      <el-form v-else inline label-width="180px" style="margin-top:14px;">
        <el-form-item label="레벨별 지속시간(초)"><el-input-number v-model="sweep.durationSecPerLevel" :min="1" :max="30" /></el-form-item>
        <el-form-item label="동시성 레벨">
          <el-select v-model="sweep.concurrencyLevels" multiple collapse-tags placeholder="레벨 선택" style="width:280px;">
            <el-option v-for="n in [1,5,10,20,50,100,200,300,500]" :key="n" :label="n" :value="n" />
          </el-select>
        </el-form-item>
        <el-form-item><el-checkbox v-model="sweep.warmup">시작 전 Warm-up</el-checkbox></el-form-item>
        <el-form-item><el-button type="primary" :loading="running" @click="runSweep"><el-icon><CaretRight /></el-icon>Sweep 실행</el-button></el-form-item>
      </el-form>

      <el-alert v-if="!running && !singleResult && !sweepResult" type="info" :closable="false" style="margin-top:10px;">
        <p>👉 무엇을 측정하나? <code>AuthzQueryService.check()</code> 의 인가 판정 시간 (Caffeine L1 + Redis L2 + 폴더 전개·샤딩 키 조회 포함)을 직접 측정합니다. HTTP 오버헤드는 제외합니다.</p>
        <p>시드된 사용자 N명과 시스템 전체 API에서 무작위 조합으로 부하를 만들어, latency 분포·P99·throughput을 계산합니다.</p>
      </el-alert>
    </el-card>

    <!-- 단일 결과 -->
    <el-card v-if="singleResult" style="margin-top:14px;">
      <template #header><b>결과</b></template>
      <SingleResult :data="singleResult" />
    </el-card>

    <!-- Sweep 결과 -->
    <el-card v-if="sweepResult" style="margin-top:14px;">
      <template #header><b>Sweep 결과</b></template>
      <SweepResult :data="sweepResult" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { Lightning, CaretRight } from '@element-plus/icons-vue'
import api from '@/api'
import { ElMessage } from 'element-plus'
import { useAppStore } from '@/store'
import SingleResult from './components/PerfSingleResult.vue'
import SweepResult from './components/PerfSweepResult.vue'

const app = useAppStore()
const mode = ref('single')
const running = ref(false)
const singleResult = ref(null)
const sweepResult = ref(null)

const single = reactive({ concurrency: 50, durationSec: 5, userSample: 100, urlSample: 500, warmup: true })
const sweep = reactive({ durationSecPerLevel: 3, concurrencyLevels: [1, 10, 50, 100, 200], warmup: true })

async function runSingle () {
  running.value = true
  singleResult.value = null
  try {
    const r = await api.post('/perftest/run', { ...single, systemCd: app.systemCd }, { timeout: 120_000 })
    singleResult.value = r.data
    if (singleResult.value.error) ElMessage.error(singleResult.value.error)
    else ElMessage.success(`${singleResult.value.success_requests}건 / ${singleResult.value.rps.toFixed(0)} RPS`)
  } catch (e) { ElMessage.error(e.message) } finally { running.value = false }
}

async function runSweep () {
  running.value = true
  sweepResult.value = null
  try {
    const r = await api.post('/perftest/sweep', { ...sweep, systemCd: app.systemCd }, { timeout: 600_000 })
    sweepResult.value = r.data
    ElMessage.success(`${sweepResult.value.levels.length} 레벨 완료`)
  } catch (e) { ElMessage.error(e.message) } finally { running.value = false }
}
</script>

<style scoped>
.perf code { background:#f4f6f8; padding:1px 6px; border-radius:3px; font-size:12px; }
</style>
