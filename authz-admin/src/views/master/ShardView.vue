<template>
  <el-card>
    <template #header><b>샤딩 컨피그</b></template>
    <el-form inline>
      <el-form-item label="시스템">
        <el-select v-model="systemCd" size="small" @change="load">
          <el-option v-for="s in systems" :key="s.systemCd" :label="s.systemCd" :value="s.systemCd" />
        </el-select>
      </el-form-item>
    </el-form>
    <el-form v-if="cfg" label-width="180px" style="max-width:520px;">
      <el-form-item label="샤딩 전략">
        <el-radio-group v-model="cfg.shardStrategy">
          <el-radio value="METHOD_DEPTH">METHOD_DEPTH</el-radio>
          <el-radio value="METHOD_DEPTH_SEG">METHOD_DEPTH_SEG</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="segment_position"><el-input-number v-model="cfg.segmentPosition" :min="0" /></el-form-item>
      <el-form-item label="segment_max_length"><el-input-number v-model="cfg.segmentMaxLength" :min="1" /></el-form-item>
      <el-form-item label="segment_fallback"><el-input v-model="cfg.segmentFallback" /></el-form-item>
      <el-form-item>
        <el-button type="primary" @click="save">저장</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { Master } from '@/api'
import { ElMessage } from 'element-plus'
const systems = ref([])
const systemCd = ref('ERP')
const cfg = ref(null)
onMounted(async () => { systems.value = await Master.systems(); await load() })
async function load () { cfg.value = await Master.shardConfig(systemCd.value) }
async function save () { cfg.value = await Master.saveShardConfig(systemCd.value, cfg.value); ElMessage.success('저장됨') }
</script>
