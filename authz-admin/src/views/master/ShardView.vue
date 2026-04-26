<template>
  <el-card>
    <template #header><b>시스템 샤딩 컨피그</b></template>
    <el-form inline>
      <el-form-item label="시스템">
        <el-select v-model="app.systemCd" size="small" @change="onSysChange">
          <el-option v-for="s in app.systems" :key="s.systemCd" :label="s.systemCd" :value="s.systemCd" />
        </el-select>
      </el-form-item>
    </el-form>
    <el-row :gutter="14">
      <el-col :span="10">
        <el-form v-if="cfg" label-width="180px">
          <el-form-item label="샤딩 전략">
            <el-radio-group v-model="cfg.shardStrategy" @change="recompute">
              <el-radio value="METHOD_DEPTH">METHOD_DEPTH</el-radio>
              <el-radio value="METHOD_DEPTH_SEG">METHOD_DEPTH_SEG</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="segment_position"><el-input-number v-model="cfg.segmentPosition" :min="0" @change="recompute" /></el-form-item>
          <el-form-item label="segment_max_length"><el-input-number v-model="cfg.segmentMaxLength" :min="1" @change="recompute" /></el-form-item>
          <el-form-item label="segment_fallback"><el-input v-model="cfg.segmentFallback" @change="recompute" /></el-form-item>
          <el-form-item label="샘플 URL 추가">
            <el-input v-model="newSample" @keyup.enter="addSample" placeholder="/api/your/url" />
            <el-button size="small" @click="addSample">+</el-button>
          </el-form-item>
          <el-form-item>
            <el-button @click="recompute">샘플 검증</el-button>
            <el-button type="primary" @click="save">저장 및 재구성</el-button>
            <el-button @click="reload">취소</el-button>
          </el-form-item>
        </el-form>
      </el-col>
      <el-col :span="14">
        <el-card v-if="preview" shadow="never">
          <template #header><b>적용 영향 미리보기</b></template>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="현재 전략">{{ preview.current_strategy }}</el-descriptions-item>
            <el-descriptions-item label="새 전략">{{ preview.new_strategy }}</el-descriptions-item>
            <el-descriptions-item label="현재 샤드 수">{{ preview.current_distinct_shards }}</el-descriptions-item>
            <el-descriptions-item label="변경 후 샤드 수">{{ preview.new_distinct_shards }}</el-descriptions-item>
            <el-descriptions-item label="현재 키 수">{{ preview.current_key_count }}</el-descriptions-item>
            <el-descriptions-item label="변경 후 키 수 (추정)">{{ preview.new_key_estimate }}</el-descriptions-item>
            <el-descriptions-item label="SEG fallback 수">{{ preview.seg_fallback_count }} ({{ preview.seg_fallback_ratio_pct }}%)</el-descriptions-item>
            <el-descriptions-item label="재구성 예상 시간">{{ Math.round(preview.rebuild_est_ms / 1000) }}초</el-descriptions-item>
          </el-descriptions>
          <el-divider content-position="left">샘플 URL 미리보기</el-divider>
          <el-table :data="preview.samples" size="small">
            <el-table-column prop="url" label="URL" />
            <el-table-column prop="depth" label="depth" width="80" />
            <el-table-column prop="seg" label="seg" width="100" />
            <el-table-column prop="shard" label="shard key">
              <template #default="{ row }"><code>{{ row.shard }}</code></template>
            </el-table-column>
          </el-table>
          <el-alert v-if="preview.seg_fallback_ratio_pct > 30" type="warning" :closable="false" style="margin-top:8px;">
            ⚠ SEG fallback 비율 {{ preview.seg_fallback_ratio_pct }}% — segment_position 재조정 또는 METHOD_DEPTH 전환 권장 (스펙 §11.5)
          </el-alert>
        </el-card>
        <el-empty v-else description="좌측에서 컨피그를 변경하면 미리보기가 표시됩니다" />
      </el-col>
    </el-row>
  </el-card>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { Master } from '@/api'
import api from '@/api'
import { ElMessage } from 'element-plus'
import { useAppStore } from '@/store'

const app = useAppStore()
const cfg = ref(null)
const preview = ref(null)
const newSample = ref('')
const samples = ref([
  '/api/purchase/requests',
  '/api/purchase/requests/{id}',
  '/api/orders',
  '/health'
])

onMounted(async () => { await reload() })
watch(() => app.systemCd, () => reload())

async function reload () {
  cfg.value = await Master.shardConfig(app.systemCd)
  await recompute()
}
async function onSysChange () { await reload() }
function addSample () {
  if (newSample.value) { samples.value.push(newSample.value); newSample.value = ''; recompute() }
}
async function recompute () {
  if (!cfg.value) return
  preview.value = await api.post(`/systems/${app.systemCd}/shard-config/preview`,
      { ...cfg.value, sample_urls: samples.value }).then(r => r.data)
}
async function save () {
  cfg.value = await Master.saveShardConfig(app.systemCd, cfg.value)
  ElMessage.success('저장됨 — 캐시 재구성이 백그라운드에서 진행됩니다 (Outbox)')
  await recompute()
}
</script>
