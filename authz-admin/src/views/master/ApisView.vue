<template>
  <el-card>
    <template #header>
      <b>API 마스터</b>
      <el-select v-model="systemCd" size="small" style="margin-left:12px;width:160px;" @change="load">
        <el-option v-for="s in systems" :key="s.systemCd" :label="s.systemCd" :value="s.systemCd" />
      </el-select>
      <el-tag style="margin-left:12px;" type="info">{{ rows.length }} APIs</el-tag>
    </template>
    <el-table :data="rows" stripe size="small" max-height="700">
      <el-table-column prop="apiId" label="ID" width="80" />
      <el-table-column prop="httpMethod" label="Method" width="80">
        <template #default="{ row }">
          <el-tag :type="methodColor(row.httpMethod)" size="small">{{ row.httpMethod }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="urlPattern" label="URL 패턴" />
      <el-table-column prop="urlDepth" label="depth" width="80" />
      <el-table-column prop="shardSeg" label="shard_seg" width="120" />
      <el-table-column prop="serviceNm" label="서비스" width="120" />
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
async function load () { rows.value = await Master.apis(systemCd.value) }
function methodColor (m) {
  return ({ GET:'success', POST:'warning', PUT:'primary', PATCH:'primary', DELETE:'danger' })[m] || 'info'
}
</script>
