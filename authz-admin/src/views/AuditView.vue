<template>
  <el-tabs v-model="tab">
    <el-tab-pane label="변경 이력" name="changes">
      <el-form inline>
        <el-form-item label="시스템"><el-input v-model="filters.system_cd" /></el-form-item>
        <el-form-item label="주체ID"><el-input v-model="filters.subject_id" /></el-form-item>
        <el-form-item><el-button type="primary" @click="loadChanges">조회</el-button></el-form-item>
      </el-form>
      <el-table :data="changes.content" stripe size="small">
        <el-table-column prop="occurredAt" label="시각" width="180" />
        <el-table-column prop="actorId" label="행위자" width="150" />
        <el-table-column prop="action" label="작업" width="110" />
        <el-table-column prop="systemCd" label="시스템" width="80" />
        <el-table-column prop="subjectType" label="주체타입" width="80" />
        <el-table-column prop="subjectId" label="주체ID" width="120" />
        <el-table-column prop="targetId" label="메뉴ID" width="100" />
        <el-table-column prop="actionCd" label="액션" width="80" />
        <el-table-column label="상세">
          <template #default="{ row }">
            <pre style="font-size:11px; margin:0">{{ JSON.stringify(row.detail) }}</pre>
          </template>
        </el-table-column>
      </el-table>
      <div class="meta">총 {{ changes.total || 0 }}건</div>
    </el-tab-pane>

    <el-tab-pane label="사용자별 권한" name="byUser">
      <el-form inline>
        <el-form-item label="시스템"><el-input v-model="byU.system_cd" /></el-form-item>
        <el-form-item label="사용자ID"><el-input v-model="byU.user_id" /></el-form-item>
        <el-form-item><el-button type="primary" @click="loadByUser">조회</el-button></el-form-item>
      </el-form>
      <el-table :data="userPerms" size="small">
        <el-table-column prop="permId" label="ID" width="60" />
        <el-table-column prop="targetId" label="메뉴ID" width="100" />
        <el-table-column prop="actionCd" label="액션" width="80" />
        <el-table-column prop="createdAt" label="생성" />
      </el-table>
    </el-tab-pane>

    <el-tab-pane label="메뉴별 권한" name="byMenu">
      <el-form inline>
        <el-form-item label="시스템"><el-input v-model="byM.system_cd" /></el-form-item>
        <el-form-item label="메뉴ID"><el-input-number v-model="byM.menu_id" /></el-form-item>
        <el-form-item><el-button type="primary" @click="loadByMenu">조회</el-button></el-form-item>
      </el-form>
      <el-table :data="menuPerms" size="small">
        <el-table-column prop="subjectType" label="주체타입" width="80" />
        <el-table-column prop="subjectId" label="주체ID" width="140" />
        <el-table-column prop="actionCd" label="액션" width="80" />
        <el-table-column prop="createdAt" label="생성" />
      </el-table>
    </el-tab-pane>

    <el-tab-pane label="캐시 상태" name="cache">
      <el-button @click="loadStats" type="primary" size="small">새로고침</el-button>
      <pre style="background:#f4f6f8; padding:10px;">{{ JSON.stringify(stats, null, 2) }}</pre>
    </el-tab-pane>
  </el-tabs>
</template>

<script setup>
import { ref } from 'vue'
import { Audit, Authz } from '@/api'
const tab = ref('changes')
const filters = ref({ system_cd: 'ERP', subject_id: '' })
const changes = ref({ content: [], total: 0 })
const byU = ref({ system_cd: 'ERP', user_id: 'U00001' })
const byM = ref({ system_cd: 'ERP', menu_id: 1 })
const userPerms = ref([])
const menuPerms = ref([])
const stats = ref({})
async function loadChanges () { changes.value = await Audit.changes(filters.value) }
async function loadByUser () { userPerms.value = await Audit.byUser(byU.value) }
async function loadByMenu () { menuPerms.value = await Audit.byMenu(byM.value) }
async function loadStats () { stats.value = await Authz.cacheStats() }
loadChanges(); loadStats()
</script>
<style scoped>.meta { color:#6b7280; padding:6px 0; font-size:12px; }</style>
