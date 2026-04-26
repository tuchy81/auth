<template>
  <el-card>
    <template #header><b>사용자 메뉴 트리 (인가 적용 결과)</b></template>
    <el-form inline>
      <el-form-item label="시스템">
        <el-select v-model="form.system_cd" style="width:140px">
          <el-option v-for="s in systems" :key="s.systemCd" :label="s.systemCd" :value="s.systemCd" />
        </el-select>
      </el-form-item>
      <el-form-item label="회사"><el-input v-model="form.company_cd" style="width:120px" /></el-form-item>
      <el-form-item label="부서"><el-input v-model="form.dept_id" style="width:160px" /></el-form-item>
      <el-form-item label="사용자"><el-input v-model="form.user_id" style="width:140px" /></el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load">조회</el-button>
        <el-button @click="warmup">Warm-up</el-button>
      </el-form-item>
    </el-form>
    <el-tree :data="tree" :props="{ label: 'menu_nm', children: 'children' }" node-key="menu_id">
      <template #default="{ data }">
        <span>
          {{ data.menu_type === 'F' ? '📁' : data.menu_type === 'L' ? '🔗' : '📄' }}
          {{ data.menu_nm }}
          <el-tag v-if="data.actions" size="small" type="success" style="margin-left:6px">
            {{ data.actions.join(',') }}
          </el-tag>
          <span v-if="data.route_path" class="path">{{ data.route_path }}</span>
        </span>
      </template>
    </el-tree>
    <div v-if="warmupInfo" class="warm">Warm-up: {{ JSON.stringify(warmupInfo) }}</div>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Authz, Master } from '@/api'
import { ElMessage } from 'element-plus'

const systems = ref([])
const tree = ref([])
const warmupInfo = ref(null)
const form = ref({ system_cd: 'ERP', company_cd: 'CO01', dept_id: 'CO01-D1', user_id: 'U00001' })

onMounted(async () => { systems.value = await Master.systems(); await load() })

async function load () {
  const r = await Authz.menuTree(form.value)
  tree.value = r.tree
  ElMessage.success(`${count(r.tree)}개 노드 조회됨`)
}
async function warmup () {
  warmupInfo.value = await Authz.warmupSystem({ system_cd: form.value.system_cd, user_id: form.value.user_id })
  ElMessage.success(`Warm-up 완료: ${warmupInfo.value.shard_count} shards / ${warmupInfo.value.total_apis} apis`)
  await load()
}
function count (nodes) { return nodes.reduce((n, x) => n + 1 + (x.children ? count(x.children) : 0), 0) }
</script>
<style scoped>
.path { color: #94a3b8; margin-left: 8px; font-size: 12px; }
.warm { background: #f4f6f8; padding: 6px; margin-top: 8px; font-size: 12px; }
</style>
