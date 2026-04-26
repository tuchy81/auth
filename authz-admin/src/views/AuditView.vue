<template>
  <el-card class="audit">
    <template #header>
      <div style="display:flex; align-items:center; gap:12px;">
        <el-icon><Search /></el-icon><b>조회 조건</b>
        <span style="margin-left:auto; color:#6b7280;">시스템: <b>{{ app.systemCd }}</b></span>
      </div>
    </template>
    <el-form inline>
      <el-form-item label="조회 유형">
        <el-radio-group v-model="mode" @change="onModeChange">
          <el-radio-button value="user">사용자 기준</el-radio-button>
          <el-radio-button value="menu">메뉴 기준</el-radio-button>
          <el-radio-button value="api">API 기준</el-radio-button>
          <el-radio-button value="changes">변경 이력</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <template v-if="mode === 'user'">
        <el-form-item label="사용자">
          <el-select v-model="userId" filterable style="width:280px;">
            <el-option v-for="u in users" :key="u.userId"
                       :label="`${u.userNm} (${u.companyCd} / ${u.deptId} / ${u.userId})`"
                       :value="u.userId" />
          </el-select>
        </el-form-item>
      </template>

      <template v-else-if="mode === 'menu'">
        <el-form-item label="메뉴">
          <el-select v-model="menuId" filterable style="width:280px;">
            <el-option v-for="m in menus" :key="m.menuId" :label="`[${m.menuType}] ${m.menuNm} (#${m.menuId})`" :value="m.menuId" />
          </el-select>
        </el-form-item>
      </template>

      <template v-else-if="mode === 'api'">
        <el-form-item label="API ID">
          <el-input-number v-model="apiId" :min="1" />
        </el-form-item>
      </template>

      <template v-else-if="mode === 'changes'">
        <el-form-item label="주체ID"><el-input v-model="changesFilter.subject_id" /></el-form-item>
      </template>

      <el-form-item><el-button type="primary" @click="run">조회</el-button></el-form-item>
    </el-form>

    <!-- USER 결과 -->
    <div v-if="mode === 'user' && userResult">
      <el-divider />
      <el-row :gutter="14" class="summary-row">
        <el-col :span="6"><el-card shadow="never"><div class="lbl">메뉴 접근</div><div class="val">{{ userResult.summary.menu_count }} <span class="sub">/ {{ totalMenus }}</span></div><div class="sub">{{ pct(userResult.summary.menu_count, totalMenus) }}%</div></el-card></el-col>
        <el-col :span="6"><el-card shadow="never"><div class="lbl">API 접근</div><div class="val">{{ apiAccessCount }} <span class="sub">/ {{ totalApis }}</span></div><div class="sub">{{ pct(apiAccessCount, totalApis) }}%</div></el-card></el-col>
        <el-col :span="6"><el-card shadow="never"><div class="lbl">부여 경로</div><div class="val">{{ totalSources }}건</div><div class="sub">직접+그룹+상속</div></el-card></el-col>
        <el-col :span="6"><el-card shadow="never"><div class="lbl">마지막 변경</div><div class="val">{{ lastChange || '-' }}</div></el-card></el-col>
      </el-row>
      <div class="src-tags">
        <el-tag type="success">✓ 직접부여 {{ userResult.summary.direct }}건</el-tag>
        <el-tag type="warning">✓ UG경유 {{ userResult.summary.via_group }}건</el-tag>
        <el-tag type="primary">✓ 부서상속 {{ userResult.summary.via_dept }}건</el-tag>
        <el-tag>✓ 회사상속 {{ userResult.summary.via_company }}건</el-tag>
        <el-tag effect="plain">📁 폴더상속 {{ userResult.summary.via_folder }}건</el-tag>
      </div>
      <el-divider content-position="left">메뉴별 상세 권한</el-divider>
      <el-table :data="userResult.menus" size="small">
        <el-table-column label="메뉴" min-width="200">
          <template #default="{ row }">
            📄 <b>{{ row.menu_nm }}</b>
            <span class="hint">#{{ row.menu_id }} {{ row.menu_cd }}</span>
          </template>
        </el-table-column>
        <el-table-column v-for="a in actionDefs" :key="a.actionCd" :label="a.actionCd" width="55" align="center">
          <template #default="{ row }">
            <span v-if="row.actions[a.actionCd]" class="check">✓</span>
            <span v-else class="dash">-</span>
          </template>
        </el-table-column>
        <el-table-column label="권한 출처" min-width="220">
          <template #default="{ row }">
            <span v-for="(srcs, act) in row.actions" :key="act">
              <el-tag v-for="s in srcs" :key="s.perm_id" size="small" :type="srcType(s.source)" effect="plain" style="margin:1px;">
                <span v-if="s.via_folder">📁</span> {{ srcLabel(s.source) }}
              </el-tag>
            </span>
          </template>
        </el-table-column>
      </el-table>
      <div class="footer-row">
        <el-button size="small" @click="exportCsv">📊 엑셀 다운로드</el-button>
        <el-button size="small" @click="mode='changes'; changesFilter.subject_id=userId; run()">📋 변경이력 보기</el-button>
      </div>
    </div>

    <!-- MENU 결과 -->
    <div v-else-if="mode === 'menu' && menuResult">
      <el-divider />
      <p>이 메뉴에 권한이 부여된 주체 ({{ menuResult.length }}건):</p>
      <el-table :data="menuResult" size="small">
        <el-table-column prop="subjectType" label="주체타입" width="80" />
        <el-table-column prop="subjectId" label="주체ID" width="160" />
        <el-table-column prop="actionCd" label="액션" width="80" />
        <el-table-column prop="companyCd" label="회사" width="100" />
        <el-table-column prop="createdAt" label="부여일" />
      </el-table>
    </div>

    <!-- API 결과 -->
    <div v-else-if="mode === 'api' && apiResult">
      <el-divider />
      <p>이 API에 도달 가능한 주체 ({{ apiResult.menu_action_refs }}개 메뉴-액션 참조 / {{ apiResult.subjects.length }}건 부여):</p>
      <el-table :data="apiResult.subjects" size="small">
        <el-table-column prop="menu_id" label="메뉴ID" width="80" />
        <el-table-column prop="action_cd" label="액션" width="80" />
        <el-table-column prop="subject_type" label="주체타입" width="80" />
        <el-table-column prop="subject_id" label="주체ID" />
      </el-table>
    </div>

    <!-- CHANGES 결과 -->
    <div v-else-if="mode === 'changes' && changes">
      <el-divider />
      <el-table :data="changes.content" stripe size="small">
        <el-table-column prop="occurredAt" label="시각" width="180" />
        <el-table-column prop="actorId" label="행위자" width="150" />
        <el-table-column prop="action" label="작업" width="110" />
        <el-table-column prop="subjectType" label="주체" width="60" />
        <el-table-column prop="subjectId" label="주체ID" width="120" />
        <el-table-column prop="targetId" label="대상" width="80" />
        <el-table-column prop="actionCd" label="액션" width="60" />
        <el-table-column label="상세">
          <template #default="{ row }"><pre style="font-size:11px;margin:0">{{ JSON.stringify(row.detail) }}</pre></template>
        </el-table-column>
      </el-table>
    </div>

    <el-empty v-else description="조회 조건을 선택하고 [조회] 버튼을 누르세요" />
  </el-card>

  <el-card style="margin-top:14px;">
    <template #header><b>L1/L2 캐시 상태</b></template>
    <el-button @click="loadStats" type="primary" size="small">새로고침</el-button>
    <pre style="background:#f4f6f8; padding:10px;">{{ JSON.stringify(stats, null, 2) }}</pre>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { Authz, Master, Audit } from '@/api'
import api from '@/api'
import { useAppStore } from '@/store'

const app = useAppStore()
const mode = ref('user')
const userId = ref('U00001')
const menuId = ref(null)
const apiId = ref(1)
const changesFilter = reactive({ subject_id: '' })
const users = ref([])
const menus = ref([])
const actionDefs = ref([])
const userResult = ref(null)
const menuResult = ref(null)
const apiResult = ref(null)
const changes = ref(null)
const stats = ref({})
const totalMenus = ref(0)
const totalApis = ref(0)

onMounted(async () => {
  users.value = await Master.users()
  await refreshSystemData()
  await loadStats()
})
watch(() => app.systemCd, refreshSystemData)

async function refreshSystemData () {
  menus.value = (await Master.menus(app.systemCd)).filter(m => m.menuType === 'M')
  totalMenus.value = menus.value.length
  totalApis.value = (await Master.apis(app.systemCd)).length
  actionDefs.value = await Master.actions(app.systemCd)
}

const apiAccessCount = computed(() => {
  if (!userResult.value) return 0
  // sum of api count per (leaf, action) — approximate
  let n = 0
  userResult.value.menus.forEach(m => Object.keys(m.actions).forEach(() => n++))
  return n
})
const totalSources = computed(() => {
  if (!userResult.value) return 0
  const s = userResult.value.summary
  return s.direct + s.via_group + s.via_dept + s.via_company
})
const lastChange = ref('-')

function pct (a, b) { return b > 0 ? ((a / b) * 100).toFixed(1) : '0.0' }

function onModeChange () { userResult.value = null; menuResult.value = null; apiResult.value = null; changes.value = null }

async function run () {
  if (mode.value === 'user') {
    userResult.value = await api.get('/permissions/effective/by-user', { params: { system_cd: app.systemCd, user_id: userId.value } }).then(r => r.data)
    // last change
    const h = await Audit.changes({ system_cd: app.systemCd, subject_id: userId.value, size: 1 }).catch(() => null)
    lastChange.value = h?.content?.[0]?.occurredAt || '-'
  } else if (mode.value === 'menu') {
    menuResult.value = await Audit.byMenu({ system_cd: app.systemCd, menu_id: menuId.value })
  } else if (mode.value === 'api') {
    apiResult.value = await api.get('/audit/permissions/by-api', { params: { system_cd: app.systemCd, api_id: apiId.value } }).then(r => r.data)
  } else if (mode.value === 'changes') {
    changes.value = await Audit.changes({ system_cd: app.systemCd, subject_id: changesFilter.subject_id || null, size: 100 })
  }
}

async function loadStats () { stats.value = await Authz.cacheStats() }

function srcType (s) {
  if (!s) return 'info'
  if (s.startsWith('DIRECT')) return 'success'
  if (s.startsWith('USER_GROUP')) return 'warning'
  if (s.startsWith('DEPT')) return 'primary'
  if (s.startsWith('COMPANY')) return 'info'
  return 'info'
}
function srcLabel (s) {
  if (s === 'DIRECT_USER' || s.startsWith('DIRECT_')) return '직접부여'
  if (s.startsWith('USER_GROUP')) return 'UG경유'
  if (s.startsWith('DEPT')) return '부서상속'
  if (s.startsWith('COMPANY')) return '회사상속'
  return s
}

function exportCsv () {
  if (!userResult.value) return
  const rows = [['menu_id', 'menu_nm', 'action', 'source', 'perm_id', 'via_folder']]
  userResult.value.menus.forEach(m => {
    Object.entries(m.actions).forEach(([act, srcs]) => {
      srcs.forEach(s => rows.push([m.menu_id, m.menu_nm, act, s.source, s.perm_id, s.via_folder]))
    })
  })
  const csv = rows.map(r => r.join(',')).join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `effective-${userId.value}-${app.systemCd}.csv`
  a.click()
}
</script>

<style scoped>
.summary-row { margin-top: 8px; }
.summary-row .lbl { color: #6b7280; font-size: 12px; }
.summary-row .val { font-size: 22px; font-weight: 700; color: #111827; }
.summary-row .val .sub { color: #94a3b8; font-size: 14px; }
.summary-row .sub { color: #94a3b8; font-size: 12px; }
.src-tags { padding: 8px 0; display: flex; gap: 8px; }
.check { color: #16a34a; font-weight: 700; }
.dash { color: #d1d5db; }
.hint { color: #94a3b8; font-size: 11px; margin-left: 6px; }
.footer-row { text-align: right; margin-top: 8px; }
</style>
