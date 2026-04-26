<template>
  <el-card>
    <template #header><b>시스템 마스터</b></template>
    <el-table :data="rows" stripe size="small">
      <el-table-column prop="systemCd" label="코드" width="100" />
      <el-table-column prop="systemNm" label="이름" />
      <el-table-column prop="systemType" label="유형" width="100" />
      <el-table-column prop="ownerCompanyCd" label="소유회사" width="100" />
      <el-table-column prop="frontendType" label="프런트엔드" width="100" />
      <el-table-column prop="baseUrl" label="Base URL" />
      <el-table-column prop="status" label="상태" width="80" />
    </el-table>
  </el-card>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { Master } from '@/api'
const rows = ref([])
onMounted(async () => { rows.value = await Master.systems() })
</script>
