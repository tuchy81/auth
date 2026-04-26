<template>
  <el-card>
    <template #header><b>What-if 시뮬레이션</b></template>
    <el-form inline label-width="90px">
      <el-form-item label="시스템"><el-input v-model="f.system_cd" style="width:120px" /></el-form-item>
      <el-form-item label="주체타입"><el-input v-model="f.subject_type" style="width:80px" /></el-form-item>
      <el-form-item label="주체ID"><el-input v-model="f.subject_id" style="width:140px" /></el-form-item>
      <el-form-item label="대상메뉴ID"><el-input-number v-model="f.target_id" :min="1" /></el-form-item>
      <el-form-item label="액션"><el-input v-model="f.action_cd" style="width:80px" /></el-form-item>
      <el-form-item>
        <el-button type="primary" @click="run('grant')">GRANT 시뮬</el-button>
        <el-button type="warning" @click="run('revoke')">REVOKE 시뮬</el-button>
      </el-form-item>
    </el-form>
    <el-descriptions v-if="result" :column="2" border>
      <el-descriptions-item label="작업">{{ result.operation }}</el-descriptions-item>
      <el-descriptions-item label="이미 부여됨?">{{ result.already_granted }}</el-descriptions-item>
      <el-descriptions-item label="대상 메뉴">{{ result.target_menu?.menu_nm }} ({{ result.target_menu?.menu_type }})</el-descriptions-item>
      <el-descriptions-item label="영향 사용자수">{{ result.affected_user_count }}</el-descriptions-item>
      <el-descriptions-item label="영향 리프수" :span="2">{{ result.affected_leaf_count }} ({{ Array.from(result.affected_leaves || []).slice(0, 8).join(', ') }}{{ (result.affected_leaves || []).length > 8 ? '…' : '' }})</el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>

<script setup>
import { ref } from 'vue'
import { Authz } from '@/api'
const f = ref({ system_cd: 'ERP', subject_type: 'U', subject_id: 'U00001', target_id: 1, action_cd: 'R' })
const result = ref(null)
async function run (op) {
  result.value = op === 'grant' ? await Authz.simulateGrant(f.value) : await Authz.simulateRevoke(f.value)
}
</script>
