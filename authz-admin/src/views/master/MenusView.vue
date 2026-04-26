<template>
  <el-card>
    <template #header>
      <b>메뉴 마스터</b>
      <el-select v-model="systemCd" size="small" style="margin-left:12px;width:160px;" @change="load">
        <el-option v-for="s in systems" :key="s.systemCd" :label="s.systemCd" :value="s.systemCd" />
      </el-select>
    </template>
    <el-table :data="rows" stripe size="small" max-height="600">
      <el-table-column prop="menuId" label="ID" width="80" />
      <el-table-column prop="menuType" label="유형" width="80" />
      <el-table-column prop="parentMenuId" label="부모" width="80" />
      <el-table-column prop="menuCd" label="코드" width="180" />
      <el-table-column prop="menuNm" label="이름" />
      <el-table-column prop="status" label="상태" width="60" />
    </el-table>
  </el-card>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { Master } from '@/api'
const systems = ref([])
const systemCd = ref('ERP')
const rows = ref([])
onMounted(async () => { systems.value = await Master.systems(); await load() })
async function load () { rows.value = await Master.menus(systemCd.value) }
</script>
