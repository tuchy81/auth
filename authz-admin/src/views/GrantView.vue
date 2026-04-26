<template>
  <div class="grant-page">
    <div class="topbar">
      <span style="color:#6b7280;">시스템:</span>
      <b style="margin-right:14px;">{{ app.systemCd }}</b>
      <el-radio-group v-model="filter" size="small">
        <el-radio-button value="all">전체</el-radio-button>
        <el-radio-button value="direct">직접</el-radio-button>
        <el-radio-button value="inherited">상속</el-radio-button>
      </el-radio-group>
      <div style="margin-left:auto; display:flex; gap:8px;">
        <el-button @click="resetSelection"><el-icon><RefreshLeft /></el-icon>초기화</el-button>
        <el-button type="primary" @click="bulkDlg.open()"><el-icon><Lightning /></el-icon>일괄 부여</el-button>
        <el-button type="success" :disabled="!selected.length" @click="saveAll"><el-icon><Check /></el-icon>저장</el-button>
      </div>
    </div>

    <el-row :gutter="14" style="margin-top:10px;">
      <!-- A. 주체 선택 -->
      <el-col :span="6">
        <el-card>
          <template #header>
            <b>A. 주체 선택</b>
            <el-tag size="small" effect="plain" style="margin-left:6px;">SUBJECT</el-tag>
          </template>
          <el-radio-group v-model="subjectType" size="small" @change="loadSubjects">
            <el-radio-button v-for="t in subjectTypes" :key="t.code" :value="t.code">{{ t.label }}</el-radio-button>
          </el-radio-group>
          <el-input v-model="search" placeholder="검색" size="small" style="margin-top:8px" clearable :prefix-icon="Search" />

          <!-- 다중 선택 리스트 -->
          <div class="subject-list">
            <div v-for="s in filteredSubjects" :key="s.id"
                 class="sub-row" :class="{ active: isSelected(s) }"
                 @click="toggleSubject(s)">
              <el-checkbox :model-value="isSelected(s)" @click.stop @change="toggleSubject(s)" />
              <el-avatar :size="28" :style="avatarStyle(subjectType)">
                <span v-if="TYPE_META[subjectType].group" class="dual-icon">
                  <el-icon class="dual-back"><component :is="iconFor(subjectType)" /></el-icon>
                  <el-icon class="dual-front"><component :is="iconFor(subjectType)" /></el-icon>
                </span>
                <el-icon v-else><component :is="iconFor(subjectType)" /></el-icon>
              </el-avatar>
              <div class="row-body">
                <div class="nm">{{ s.label }}</div>
                <div v-if="s.sub" class="sub">{{ s.sub }}</div>
              </div>
              <span v-if="s.count != null" class="cnt">{{ s.count }}명</span>
            </div>
            <div v-if="!filteredSubjects.length" class="hint sm">검색 결과 없음</div>
          </div>

          <!-- 선택된 주체 -->
          <el-card v-if="selected.length" shadow="never" class="selected-summary">
            <template #header>
              <b>선택된 주체 ({{ selected.length }})</b>
              <el-button size="small" type="danger" link @click="selected = []" style="margin-left:auto;">전체 해제</el-button>
            </template>
            <ul class="selected-list">
              <li v-for="s in selected" :key="s.id">
                <el-tag size="small" :type="tagType(subjectType)" effect="plain">{{ subjectType }}</el-tag>
                {{ s.label }}
                <span v-if="s.count != null" class="cnt">({{ s.count }}명)</span>
              </li>
            </ul>
          </el-card>
        </el-card>
      </el-col>

      <!-- B. 메뉴 트리 -->
      <el-col :span="10">
        <el-card>
          <template #header>
            <b>B. 메뉴 / 메뉴그룹</b>
            <el-tag size="small" effect="plain" style="margin-left:6px;">TARGET</el-tag>
          </template>
          <el-input v-model="treeFilter" placeholder="메뉴명 검색" size="small" style="margin-bottom:8px" :prefix-icon="Search" clearable />
          <el-tree
            ref="treeRef" :data="tree"
            :props="{ label: 'menu_nm', children: 'children' }"
            node-key="menu_id" highlight-current
            :filter-node-method="filterNode"
            @node-click="onMenuClick">
            <template #default="{ data }">
              <span class="node">
                <span class="ic">{{ menuIcon(data.menu_type) }}</span>
                <span>{{ data.menu_nm }}</span>
                <el-tag size="small" :type="data.menu_type==='F'?'info':data.menu_type==='L'?'warning':''" effect="plain">{{ data.menu_type }}</el-tag>
              </span>
            </template>
          </el-tree>
        </el-card>
      </el-col>

      <!-- C. 액션 & 부여 상태 -->
      <el-col :span="8">
        <el-card>
          <template #header>
            <b>C. 액션 & 부여상태</b>
            <el-tag size="small" effect="plain" style="margin-left:6px;">ACTION</el-tag>
          </template>

          <div v-if="!selected.length || !selectedMenu" class="hint">좌측에서 주체와 메뉴를 모두 선택하세요</div>
          <div v-else>
            <!-- 메뉴 breadcrumb -->
            <div class="menu-breadcrumb">
              <span class="ic">{{ menuIcon(selectedMenu.menu_type) }}</span>
              <b>{{ selectedMenu.menu_nm }}</b>
              <div class="sub">{{ app.systemCd }} › {{ selectedMenu.menu_nm }}</div>
            </div>
            <el-alert v-if="selectedMenu.menu_type === 'F'" type="info" :closable="false" style="margin:6px 0;">
              📁 폴더 메뉴: 권한 부여 시 자손 리프 <b>{{ folderLeafCount }}개</b>로 자동 전개됩니다.
            </el-alert>
            <el-alert v-if="selected.length > 1" type="warning" :closable="false" style="margin:6px 0;">
              ⚠ 다중 선택 모드 ({{ selected.length }}명/그룹) — 액션을 적용하면 모두에게 일괄 부여됩니다.
            </el-alert>

            <!-- 액션 표 -->
            <el-table :data="actionRows" size="small">
              <el-table-column label="" width="40">
                <template #default="{ row }"><el-checkbox v-model="row.checked" /></template>
              </el-table-column>
              <el-table-column label="액션" min-width="120">
                <template #default="{ row }">
                  <b>{{ row.actionCd }}</b> ({{ row.actionNm }})
                </template>
              </el-table-column>
              <el-table-column label="부여 상태">
                <template #default="{ row }">
                  <el-tag v-if="!row.sources.length" size="small" type="info" effect="plain">미부여</el-tag>
                  <el-tag v-for="s in row.sources" :key="s.source + s.perm_id"
                          size="small" :type="sourceColor(s.source)" effect="plain"
                          style="margin-right:3px;">
                    <span v-if="s.via_folder">📁 </span>{{ sourceLabel(s.source) }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>

            <!-- 권한 출처 상세 -->
            <el-collapse v-if="selected.length === 1 && actionRows.some(r => r.sources.length)" style="margin-top:6px;">
              <el-collapse-item title="권한 출처 상세" name="src">
                <div v-for="r in actionRows.filter(x => x.sources.length)" :key="r.actionCd" class="src-detail">
                  <b>{{ r.actionCd }}:</b>
                  <span v-for="s in r.sources" :key="s.perm_id" class="src-line">
                    <el-tag size="small" :type="sourceColor(s.source)">{{ sourceLabel(s.source) }}</el-tag>
                    <span v-if="s.via_folder" class="folder-hint">📁 폴더 상속</span>
                  </span>
                </div>
              </el-collapse-item>
            </el-collapse>

            <el-divider />
            <div class="footer-btns">
              <el-button type="primary" :disabled="!actionRows.some(r => r.checked)" @click="grant">
                <el-icon><Check /></el-icon>부여 적용
              </el-button>
              <el-button type="warning" plain :disabled="!hasDirectGrant" @click="revoke">
                <el-icon><Close /></el-icon>부여 해제
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <BulkGrantDialog ref="bulkDlg" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import {
  Lightning, Check, Close, Search, RefreshLeft,
  OfficeBuilding, HomeFilled, User
} from '@element-plus/icons-vue'
import { Master } from '@/api'
import api from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAppStore } from '@/store'
import BulkGrantDialog from './components/BulkGrantDialog.vue'

const app = useAppStore()
const filter = ref('all')
const subjectType = ref('UG')
const search = ref('')
const treeFilter = ref('')
const subjects = ref([])
const selected = ref([])
const tree = ref([])
const flatMenus = ref([])
const actionDefs = ref([])
const selectedMenu = ref(null)
const treeRef = ref(null)
const allPermsBySubject = ref({}) // subject_id -> permissions[]
const effective = ref({})         // user_id -> effective response
const bulkDlg = ref(null)

const subjectTypes = [
  { code: 'C',  label: '회사' },
  { code: 'D',  label: '부서' },
  { code: 'U',  label: '사용자' },
  { code: 'CG', label: '회사그룹' },
  { code: 'DG', label: '부서그룹' },
  { code: 'UG', label: '사용자그룹' }
]

// 타입별 아이콘과 색상.
//  - 그룹 타입(CG/DG/UG)은 group:true → 같은 base 아이콘을 2개 겹쳐 표시 (그룹 의미 시각화)
const TYPE_META = {
  C:  { icon: OfficeBuilding, group: false, bg: '#3b82f6', tag: 'primary' },  // 회사 — 파랑
  D:  { icon: HomeFilled,     group: false, bg: '#10b981', tag: 'success' },  // 부서 — 초록
  U:  { icon: User,           group: false, bg: '#f59e0b', tag: 'warning' },  // 사용자 — 황색
  CG: { icon: OfficeBuilding, group: true,  bg: '#1d4ed8', tag: 'primary' },  // 회사그룹 — 짙은 파랑 + 회사 아이콘 ×2
  DG: { icon: HomeFilled,     group: true,  bg: '#047857', tag: 'success' },  // 부서그룹 — 짙은 초록 + 부서 아이콘 ×2
  UG: { icon: User,           group: true,  bg: '#b91c1c', tag: 'danger'  }   // 사용자그룹 — 짙은 빨강 + 사용자 아이콘 ×2
}
function iconFor (t) { return TYPE_META[t]?.icon || User }
function avatarStyle (t) { return { background: TYPE_META[t]?.bg || '#6b7280', color: '#fff' } }
function tagType (t) { return TYPE_META[t]?.tag || 'info' }

const filteredSubjects = computed(() => {
  const s = search.value.toLowerCase()
  return subjects.value.filter(x => !s || x.label.toLowerCase().includes(s)).slice(0, 200)
})

function isSelected (s) { return selected.value.some(x => x.id === s.id) }
function toggleSubject (s) {
  const i = selected.value.findIndex(x => x.id === s.id)
  if (i >= 0) selected.value.splice(i, 1)
  else selected.value.push(s)
  if (selectedMenu.value) recomputeActionRows()
  if (i < 0) loadPermsFor(s)
}

async function loadPermsFor (s) {
  allPermsBySubject.value[s.id] = await Master.permissionsBySubject({
    system_cd: app.systemCd, subject_type: subjectType.value, subject_id: s.id
  }).catch(() => [])
  if (subjectType.value === 'U') {
    effective.value[s.id] = await api.get('/permissions/effective/by-user', {
      params: { system_cd: app.systemCd, user_id: s.id }
    }).then(r => r.data).catch(() => null)
  }
}

function menuIcon (t) { return t === 'F' ? '📁' : t === 'L' ? '🔗' : '📄' }

function sourceColor (s) {
  if (!s) return 'info'
  if (s.startsWith('DIRECT')) return 'success'
  if (s.startsWith('USER_GROUP')) return 'warning'
  if (s.startsWith('DEPT')) return 'primary'
  if (s.startsWith('COMPANY')) return 'info'
  return 'info'
}
function sourceLabel (s) {
  if (s.startsWith('DIRECT')) return '직접부여'
  if (s.startsWith('USER_GROUP')) return 'UG경유 ' + (s.split(':')[1] || '')
  if (s.startsWith('DEPT')) return '부서상속 ' + (s.split(':')[1] || '')
  if (s.startsWith('COMPANY')) return '회사상속 ' + (s.split(':')[1] || '')
  return s
}

watch(() => app.systemCd, () => loadAll())
watch(treeFilter, v => treeRef.value && treeRef.value.filter(v))
function filterNode (val, data) {
  if (!val) return true
  return ((data.menu_nm || '') + (data.menu_cd || '')).toLowerCase().includes(val.toLowerCase())
}

onMounted(() => loadAll())

async function loadAll () {
  await loadSubjects()
  flatMenus.value = await Master.menus(app.systemCd)
  actionDefs.value = await Master.actions(app.systemCd)
  buildTree()
}

async function loadSubjects () {
  selected.value = []
  selectedMenu.value = null
  allPermsBySubject.value = {}
  effective.value = {}
  if (subjectType.value === 'C') {
    const cs = await Master.companies()
    subjects.value = cs.map(c => ({ id: c.companyCd, label: `${c.companyCd} ${c.companyNm}` }))
  } else if (subjectType.value === 'D') {
    const cs = await Master.companies()
    const all = []
    for (const c of cs) {
      const ds = await Master.depts(c.companyCd)
      ds.forEach(d => all.push({ id: d.deptId, label: `${d.deptNm}`, sub: `${c.companyCd}/${d.deptId}`, companyCd: c.companyCd }))
    }
    subjects.value = all
  } else if (subjectType.value === 'U') {
    const us = await Master.users()
    subjects.value = us.map(u => ({ id: u.userId, label: u.userNm, sub: `${u.userId} · ${u.companyCd}/${u.deptId}`, companyCd: u.companyCd, deptId: u.deptId }))
  } else if (subjectType.value === 'CG') {
    const gs = await api.get('/groups/company').then(r => r.data)
    subjects.value = gs.map(g => ({ id: String(g.companyGroupId), label: g.groupNm }))
  } else if (subjectType.value === 'DG') {
    const gs = await api.get('/groups/dept').then(r => r.data)
    subjects.value = gs.map(g => ({ id: String(g.deptGroupId), label: g.groupNm, sub: g.companyCd, companyCd: g.companyCd }))
  } else if (subjectType.value === 'UG') {
    const gs = await api.get('/groups/user').then(r => r.data)
    // count members per group
    const withCounts = await Promise.all(gs.map(async g => {
      const members = await api.get(`/groups/user/${g.userGroupId}/members`).then(r => r.data).catch(() => [])
      return { id: String(g.userGroupId), label: g.groupNm, companyCd: g.companyCd, count: members.length }
    }))
    subjects.value = withCounts
  }
}

function buildTree () {
  const map = new Map()
  flatMenus.value.forEach(m => map.set(m.menuId, {
    menu_id: m.menuId, menu_cd: m.menuCd, menu_nm: m.menuNm, menu_type: m.menuType,
    parent_menu_id: m.parentMenuId, raw: m, children: []
  }))
  const roots = []
  flatMenus.value.forEach(m => {
    const node = map.get(m.menuId)
    if (m.parentMenuId && map.get(m.parentMenuId)) map.get(m.parentMenuId).children.push(node)
    else roots.push(node)
  })
  tree.value = roots
}

function onMenuClick (n) {
  selectedMenu.value = n
  recomputeActionRows()
}

const actionRows = ref([])
const folderLeafCount = ref(0)
const hasDirectGrant = computed(() => actionRows.value.some(r => r.sources.some(s => s.source.startsWith('DIRECT'))))

function recomputeActionRows () {
  if (!selectedMenu.value || !selected.value.length) { actionRows.value = []; return }
  const rows = actionDefs.value.map(a => {
    let sources = []
    if (selected.value.length === 1) {
      const s = selected.value[0]
      const direct = (allPermsBySubject.value[s.id] || [])
        .filter(p => p.targetId === selectedMenu.value.menu_id && p.actionCd === a.actionCd)
        .map(p => ({ source: 'DIRECT_' + subjectType.value, perm_id: p.permId, via_folder: false }))
      sources = direct
      // for U-level: pull effective sources for this leaf+action
      if (subjectType.value === 'U' && effective.value[s.id] && selectedMenu.value.menu_type === 'M') {
        const eff = effective.value[s.id].menus.find(m => m.menu_id === selectedMenu.value.menu_id)
        if (eff && eff.actions[a.actionCd]) sources = eff.actions[a.actionCd]
      }
    }
    return { actionCd: a.actionCd, actionNm: a.actionNm, sources, checked: false }
  })
  actionRows.value = rows
  folderLeafCount.value = selectedMenu.value.menu_type === 'F' ? countLeaves(selectedMenu.value) : 0
}
function countLeaves (n) {
  if (n.menu_type === 'M') return 1
  return (n.children || []).reduce((s, c) => s + countLeaves(c), 0)
}

async function grant () {
  const checked = actionRows.value.filter(r => r.checked)
  if (!checked.length || !selected.value.length) return
  let total = 0
  for (const subj of selected.value) {
    const companyCd = subj.companyCd || (subjectType.value === 'C' ? subj.id : 'CO01')
    for (const r of checked) {
      await Master.grant({
        systemCd: app.systemCd, companyCd,
        subjectType: subjectType.value, subjectId: subj.id,
        targetType: 'M', targetId: selectedMenu.value.menu_id,
        actionCd: r.actionCd
      })
      total++
    }
  }
  ElMessage.success(`${total}건 부여 (${selected.value.length} 주체 × ${checked.length} 액션) — 수 초 내 캐시 반영`)
  for (const subj of selected.value) await loadPermsFor(subj)
  recomputeActionRows()
}

async function revoke () {
  await ElMessageBox.confirm('직접 부여된 권한만 해제됩니다. 진행하시겠어요?', '확인', { type: 'warning' })
  let removed = 0
  for (const r of actionRows.value) {
    for (const s of r.sources) {
      if (s.source.startsWith('DIRECT')) {
        await Master.revoke(s.perm_id)
        removed++
      }
    }
  }
  ElMessage.success(`${removed}건 해제`)
  for (const subj of selected.value) await loadPermsFor(subj)
  recomputeActionRows()
}

function saveAll () {
  // 선택된 액션이 있으면 grant 실행
  if (actionRows.value.some(r => r.checked)) grant()
  else ElMessage.info('변경할 액션을 체크하세요')
}

function resetSelection () {
  selected.value = []
  selectedMenu.value = null
  actionRows.value = []
}
</script>

<style scoped>
.topbar { display:flex; align-items:center; gap:10px; padding: 8px 0; }
.subject-list { max-height: 440px; overflow: auto; margin-top: 8px; }
.sub-row {
  display:flex; align-items:center; gap:10px;
  padding: 8px 6px; border-radius: 6px; cursor: pointer;
  border-bottom: 1px solid #f3f4f6; transition: background 0.15s;
}
.sub-row:hover { background: #f5f7fa; }
.sub-row.active { background: #e6f0ff; border-left: 3px solid #1677ff; }
.sub-row .row-body { flex: 1; min-width: 0; }
.sub-row .nm { font-weight: 600; font-size: 13px; }
.sub-row .sub { color: #94a3b8; font-size: 11px; margin-top: 2px; }
.sub-row .cnt { color: #64748b; font-size: 12px; padding: 2px 8px; background: #f1f5f9; border-radius: 10px; }
.selected-summary { margin-top: 10px; }
.selected-summary :deep(.el-card__header) { padding: 8px 12px; display:flex; align-items:center; }
.selected-summary :deep(.el-card__body) { padding: 8px 12px; }
.selected-list { list-style: none; margin: 0; padding: 0; }
.selected-list li { padding: 3px 0; font-size: 13px; }
.selected-list .cnt { color: #94a3b8; font-size: 11px; }
.menu-breadcrumb { padding: 8px 10px; background: #f8fafc; border-radius: 6px; }
.menu-breadcrumb .ic { font-size: 16px; margin-right: 4px; }
.menu-breadcrumb .sub { color: #94a3b8; font-size: 12px; margin-top: 2px; }
.path { color: #94a3b8; font-size: 12px; margin-left: 8px; font-weight: normal; }
.hint { padding: 30px; color: #94a3b8; text-align: center; }
.hint.sm { padding: 8px; }
.node { display:inline-flex; align-items:center; gap: 4px; }
.node .ic { font-size: 13px; }
.src-detail { padding: 4px 0; font-size: 12px; }
.src-line { display:inline-block; margin-left: 4px; }
.folder-hint { color: #f59e0b; margin-left: 4px; font-size: 11px; }
.footer-btns { text-align: right; margin-top: 8px; display:flex; gap: 8px; justify-content:flex-end; }

/* 그룹 타입: 같은 아이콘 2개를 살짝 겹쳐서 "여러 명/여러 개" 의미를 시각화 */
.dual-icon { position: relative; display: inline-block; width: 18px; height: 18px; }
.dual-icon .el-icon { position: absolute; font-size: 13px; }
.dual-icon .dual-back  { top: -2px; left: -3px; opacity: 0.55; }
.dual-icon .dual-front { bottom: -2px; right: -3px; }
</style>
