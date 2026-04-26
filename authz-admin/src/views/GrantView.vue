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
      <div style="margin-left:auto;">
        <el-button @click="resetSelection">초기화</el-button>
        <el-button type="primary" @click="bulkDlg.open()"><el-icon><Lightning /></el-icon>일괄 부여</el-button>
      </div>
    </div>

    <el-row :gutter="14" style="margin-top:10px;">
      <!-- A: 주체 선택 -->
      <el-col :span="6">
        <el-card>
          <template #header><b>A. 주체 선택</b></template>
          <el-radio-group v-model="subjectType" size="small" @change="loadSubjects">
            <el-radio-button value="C">회사</el-radio-button>
            <el-radio-button value="D">부서</el-radio-button>
            <el-radio-button value="U">사용자</el-radio-button>
            <el-radio-button value="CG">CG</el-radio-button>
            <el-radio-button value="DG">DG</el-radio-button>
            <el-radio-button value="UG">UG</el-radio-button>
          </el-radio-group>
          <el-input v-model="search" placeholder="검색" size="small" style="margin-top:8px" clearable />
          <div class="subject-list">
            <div v-for="s in filteredSubjects" :key="s.id"
                 class="sub-item" :class="{ active: selected?.id === s.id }"
                 @click="select(s)">
              <div class="sub-row">
                <el-tag :type="subjectTagType(subjectType)" size="small">{{ subjectType }}</el-tag>
                <b>{{ s.label }}</b>
                <span v-if="s.sub" class="sub">{{ s.sub }}</span>
              </div>
            </div>
          </div>
          <div v-if="selected" class="selected-info">
            <b>선택된 주체:</b> {{ selected.label }}
            <div v-if="selected.companyCd" class="sub">소속 {{ selected.companyCd }}</div>
          </div>
        </el-card>
      </el-col>

      <!-- B: 메뉴 트리 -->
      <el-col :span="10">
        <el-card>
          <template #header><b>B. 메뉴 / 메뉴그룹 (TARGET)</b></template>
          <el-input v-model="treeFilter" placeholder="메뉴 검색" size="small" style="margin-bottom:8px" />
          <el-tree
            ref="treeRef" :data="tree"
            :props="{ label: 'menu_nm', children: 'children' }"
            node-key="menu_id" highlight-current
            :filter-node-method="filterNode"
            @node-click="onMenuClick">
            <template #default="{ data }">
              <span class="node">
                <span class="ic">{{ icon(data.menu_type) }}</span>
                <span>{{ data.menu_nm }}</span>
                <el-tag size="small" :type="data.menu_type==='F'?'info':data.menu_type==='L'?'warning':''" effect="plain">{{ data.menu_type }}</el-tag>
                <el-tag v-if="hasAnyPerm(data.menu_id)" size="small" type="success">부여됨</el-tag>
              </span>
            </template>
          </el-tree>
        </el-card>
      </el-col>

      <!-- C: 액션 & 부여 상태 -->
      <el-col :span="8">
        <el-card>
          <template #header>
            <b>C. 액션 & 부여 상태</b>
            <span v-if="selectedMenu" class="path">[{{ selectedMenu.menu_type }}] {{ selectedMenu.menu_nm }}</span>
          </template>
          <div v-if="!selected || !selectedMenu" class="hint">좌측에서 주체와 메뉴를 모두 선택하세요</div>
          <div v-else>
            <el-alert v-if="selectedMenu.menu_type === 'F'" type="info" :closable="false">
              폴더 메뉴: 권한 부여 시 자손 리프 <b>{{ folderLeafCount }}개</b>로 자동 전개됩니다.
            </el-alert>
            <el-table :data="actionRows" size="small" :show-header="true">
              <el-table-column label="" width="40">
                <template #default="{ row }">
                  <el-checkbox v-model="row.checked" />
                </template>
              </el-table-column>
              <el-table-column prop="actionCd" label="코드" width="60"><template #default="{ row }"><b>{{ row.actionCd }}</b></template></el-table-column>
              <el-table-column prop="actionNm" label="이름" width="80" />
              <el-table-column label="부여 상태">
                <template #default="{ row }">
                  <el-tag v-if="!row.sources.length" size="small" type="info" effect="plain">미부여</el-tag>
                  <el-tag v-for="s in row.sources" :key="s.source"
                          size="small" :type="sourceColor(s.source)" effect="plain"
                          style="margin-right:3px;">
                    {{ sourceLabel(s.source) }}
                    <el-icon v-if="s.via_folder" size="10" style="margin-left:2px;">📁</el-icon>
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
            <el-divider>권한 출처 상세</el-divider>
            <div v-for="r in actionRows.filter(x => x.sources.length)" :key="r.actionCd" class="src-detail">
              <b>{{ r.actionCd }}:</b>
              <div v-for="s in r.sources" :key="s.source" class="src-line">
                <el-tag size="small" :type="sourceColor(s.source)">{{ sourceLabel(s.source) }}</el-tag>
                <span v-if="s.via_folder" class="folder-hint">📁 폴더 상속</span>
                <span class="perm-id">perm:{{ s.perm_id }}</span>
              </div>
            </div>
            <div v-if="!actionRows.some(x => x.sources.length)" class="hint sm">현재 부여 없음</div>
            <el-divider />
            <div class="footer-btns">
              <el-button type="primary" @click="grant" :disabled="!actionRows.some(r=>r.checked)">부여 적용</el-button>
              <el-button type="warning" @click="revoke" :disabled="!actionRows.some(r=>r.sources.some(s=>s.source==='DIRECT_'+subjectType+(subjectType==='U'?'_USER':'')))">부여 해제</el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 일괄 부여 모달 -->
    <BulkGrantDialog ref="bulkDlg" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { Lightning } from '@element-plus/icons-vue'
import { Master, Authz } from '@/api'
import api from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAppStore } from '@/store'
import BulkGrantDialog from './components/BulkGrantDialog.vue'

const app = useAppStore()
const filter = ref('all')
const subjectType = ref('U')
const search = ref('')
const treeFilter = ref('')
const subjects = ref([])
const selected = ref(null)
const tree = ref([])
const flatMenus = ref([])
const actionDefs = ref([])
const selectedMenu = ref(null)
const treeRef = ref(null)
const allPermsBySubject = ref([])  // permissions for the SELECTED subject
const effective = ref(null)
const bulkDlg = ref(null)

const filteredSubjects = computed(() => {
  const s = search.value.toLowerCase()
  return subjects.value.filter(x => !s || x.label.toLowerCase().includes(s)).slice(0, 200)
})

function icon (t) { return t === 'F' ? '📁' : t === 'L' ? '🔗' : '📄' }
function subjectTagType (t) {
  return ({ C:'success', D:'primary', U:'warning', CG:'success', DG:'primary', UG:'warning' })[t] || 'info'
}
function sourceColor (s) {
  if (!s) return 'info'
  if (s.startsWith('DIRECT')) return 'success'
  if (s.startsWith('USER_GROUP')) return 'warning'
  if (s.startsWith('DEPT')) return 'primary'
  if (s.startsWith('COMPANY')) return 'info'
  if (s.startsWith('FOLDER')) return ''
  return 'info'
}
function sourceLabel (s) {
  if (s === 'DIRECT_USER' || s === 'DIRECT_C' || s === 'DIRECT_D' || s === 'DIRECT_U' || s === 'DIRECT_UG' || s === 'DIRECT_DG' || s === 'DIRECT_CG') return '직접부여'
  if (s.startsWith('USER_GROUP')) return 'UG경유 ' + s.split(':')[1]
  if (s.startsWith('DEPT')) return '부서상속 ' + s.split(':')[1]
  if (s.startsWith('COMPANY')) return '회사상속 ' + s.split(':')[1]
  if (s === 'FOLDER') return '폴더상속'
  return s
}

watch(() => app.systemCd, () => loadAll())
watch(treeFilter, v => treeRef.value && treeRef.value.filter(v))
function filterNode (val, data) {
  if (!val) return true
  return ((data.menu_nm || '') + (data.menu_cd || '')).toLowerCase().includes(val.toLowerCase())
}

onMounted(async () => {
  await loadAll()
})

async function loadAll () {
  await loadSubjects()
  flatMenus.value = await Master.menus(app.systemCd)
  actionDefs.value = await Master.actions(app.systemCd)
  buildTree()
}

async function loadSubjects () {
  selected.value = null; allPermsBySubject.value = []; selectedMenu.value = null; effective.value = null
  if (subjectType.value === 'C') {
    const cs = await Master.companies()
    subjects.value = cs.map(c => ({ id: c.companyCd, label: `${c.companyCd} ${c.companyNm}`, type: 'C' }))
  } else if (subjectType.value === 'D') {
    const cs = await Master.companies()
    const all = []
    for (const c of cs) {
      const ds = await Master.depts(c.companyCd)
      ds.forEach(d => all.push({ id: d.deptId, label: `${c.companyCd}/${d.deptId} ${d.deptNm}`, sub: d.deptNm, companyCd: c.companyCd }))
    }
    subjects.value = all
  } else if (subjectType.value === 'U') {
    const us = await Master.users()
    subjects.value = us.map(u => ({ id: u.userId, label: `${u.userId} ${u.userNm}`, sub: `${u.companyCd}/${u.deptId}`, companyCd: u.companyCd, deptId: u.deptId }))
  } else if (subjectType.value === 'CG') {
    const gs = await api.get('/groups/company').then(r => r.data)
    subjects.value = gs.map(g => ({ id: String(g.companyGroupId), label: g.groupNm }))
  } else if (subjectType.value === 'DG') {
    const gs = await api.get('/groups/dept').then(r => r.data)
    subjects.value = gs.map(g => ({ id: String(g.deptGroupId), label: g.groupNm, companyCd: g.companyCd }))
  } else if (subjectType.value === 'UG') {
    const gs = await api.get('/groups/user').then(r => r.data)
    subjects.value = gs.map(g => ({ id: String(g.userGroupId), label: g.groupNm, companyCd: g.companyCd }))
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

async function select (s) {
  selected.value = s
  // load all perms for this subject (any menu, any action)
  allPermsBySubject.value = await Master.permissionsBySubject({
    system_cd: app.systemCd, subject_type: subjectType.value, subject_id: s.id
  })
  // for U-level subject, also load effective with sources
  if (subjectType.value === 'U') {
    effective.value = await api.get('/permissions/effective/by-user', {
      params: { system_cd: app.systemCd, user_id: s.id }
    }).then(r => r.data).catch(() => null)
  } else effective.value = null
  if (selectedMenu.value) recomputeActionRows()
}

function hasAnyPerm (menuId) {
  return allPermsBySubject.value.some(p => p.targetId === menuId)
}

function onMenuClick (n) {
  selectedMenu.value = n
  recomputeActionRows()
}

const actionRows = ref([])
const folderLeafCount = ref(0)
function recomputeActionRows () {
  if (!selectedMenu.value || !selected.value) { actionRows.value = []; return }
  const rows = actionDefs.value.map(a => {
    // direct grants on this menu
    const direct = allPermsBySubject.value.filter(p => p.targetId === selectedMenu.value.menu_id && p.actionCd === a.actionCd)
        .map(p => ({ source: 'DIRECT_' + (subjectType.value === 'U' ? 'USER' : subjectType.value), perm_id: p.permId, via_folder: false }))
    let sources = direct
    // for U-level: pull effective sources for this leaf+action
    if (subjectType.value === 'U' && effective.value && selectedMenu.value.menu_type === 'M') {
      const eff = effective.value.menus.find(m => m.menu_id === selectedMenu.value.menu_id)
      if (eff && eff.actions[a.actionCd]) {
        sources = eff.actions[a.actionCd]
      }
    }
    return { actionCd: a.actionCd, actionNm: a.actionNm, sources, checked: false }
  })
  actionRows.value = rows
  // folder leaf count
  if (selectedMenu.value.menu_type === 'F') {
    folderLeafCount.value = countLeaves(selectedMenu.value)
  } else folderLeafCount.value = 0
}
function countLeaves (n) {
  if (n.menu_type === 'M') return 1
  return (n.children || []).reduce((s, c) => s + countLeaves(c), 0)
}

async function grant () {
  const checked = actionRows.value.filter(r => r.checked)
  if (!checked.length) return
  const companyCd = selected.value.companyCd || (subjectType.value === 'C' ? selected.value.id : 'CO01')
  for (const r of checked) {
    await Master.grant({
      systemCd: app.systemCd,
      companyCd,
      subjectType: subjectType.value,
      subjectId: selected.value.id,
      targetType: 'M',
      targetId: selectedMenu.value.menu_id,
      actionCd: r.actionCd
    })
  }
  ElMessage.success(`${checked.length}건 부여 (Outbox → 수 초 내 캐시 반영)`)
  await select(selected.value)
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
  await select(selected.value)
}

function resetSelection () {
  selected.value = null; selectedMenu.value = null; allPermsBySubject.value = []; effective.value = null
}
</script>

<style scoped>
.topbar { display:flex; align-items:center; gap:10px; padding: 8px 0; }
.subject-list { max-height: 480px; overflow: auto; margin-top: 6px; }
.sub-item { padding: 6px 8px; border-radius: 4px; cursor: pointer; border-bottom: 1px solid #f3f4f6; }
.sub-item:hover { background: #f5f7fa; }
.sub-item.active { background: #e6f0ff; border-left: 3px solid #1677ff; }
.sub-item .sub { color: #94a3b8; font-size: 12px; margin-left: 6px; }
.sub-row { display: flex; align-items: center; gap: 6px; }
.selected-info { margin-top: 8px; padding: 6px 8px; background: #f0f9ff; border-radius: 4px; font-size: 13px; }
.path { color: #94a3b8; font-size: 12px; margin-left: 8px; font-weight: normal; }
.hint { padding: 30px; color: #94a3b8; text-align: center; }
.hint.sm { padding: 8px; }
.node { display:inline-flex; align-items:center; gap: 4px; }
.ic { font-size: 13px; }
.src-detail { padding: 4px 0; }
.src-line { padding-left: 16px; color: #4b5563; font-size: 13px; }
.folder-hint { color: #f59e0b; margin-left: 4px; font-size: 12px; }
.perm-id { color: #94a3b8; font-size: 11px; margin-left: 6px; }
.footer-btns { text-align: right; margin-top: 8px; }
</style>
