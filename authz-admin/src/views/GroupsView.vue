<template>
  <el-card>
    <template #header>
      <div style="display:flex; align-items:center; justify-content: space-between;">
        <el-tabs v-model="tab" style="margin:0;" @tab-change="loadGroups">
          <el-tab-pane label="회사 그룹 (CG)" name="cg" />
          <el-tab-pane label="부서 그룹 (DG)" name="dg" />
          <el-tab-pane label="사용자 그룹 (UG)" name="ug" />
        </el-tabs>
        <el-button type="primary" size="small" @click="newGroup"><el-icon><Plus /></el-icon>신규 그룹</el-button>
      </div>
    </template>
    <el-row :gutter="14">
      <el-col :span="8">
        <el-input v-model="search" placeholder="그룹명 검색" size="small" clearable />
        <div class="g-list">
          <div v-for="g in filteredGroups" :key="gid(g)" class="g-item"
               :class="{active: gid(selected) === gid(g)}" @click="select(g)">
            <b>{{ g.groupNm }}</b>
            <div class="sub">{{ g.companyCd || '-' }} · ID {{ gid(g) }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="16">
        <div v-if="!selected" class="hint">좌측에서 그룹을 선택하거나 신규 등록하세요</div>
        <div v-else>
          <el-form inline>
            <el-form-item label="그룹명"><el-input v-model="selected.groupNm" /></el-form-item>
            <el-form-item v-if="tab === 'dg' || tab === 'ug'" label="회사">
              <el-select v-model="selected.companyCd" filterable>
                <el-option v-for="c in companies" :key="c.companyCd" :label="`${c.companyCd} ${c.companyNm}`" :value="c.companyCd" />
              </el-select>
            </el-form-item>
            <el-form-item><el-button type="primary" size="small" @click="save">저장</el-button></el-form-item>
            <el-form-item><el-button type="danger" size="small" @click="del">삭제</el-button></el-form-item>
          </el-form>

          <el-tabs v-model="detailTab">
            <el-tab-pane label="멤버" name="members">
              <div style="display:flex; gap:6px; align-items:center; margin-bottom:6px;">
                <el-button size="small" type="primary" @click="openAddDialog"><el-icon><Plus /></el-icon>멤버 추가</el-button>
                <el-button size="small" v-if="tab==='ug'" @click="csvDlg = true">📁 CSV 가져오기</el-button>
                <el-tag size="small" type="info" effect="plain">총 {{ members.length }}명</el-tag>
              </div>
              <el-table :data="members" size="small" max-height="400">
                <el-table-column v-for="c in memberCols" :key="c.key" :prop="c.key" :label="c.label" :width="c.width" />
                <el-table-column label="" width="80">
                  <template #default="{ row }">
                    <el-button size="small" link type="danger" @click="removeMember(row)">제거</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="부여된 권한" name="perms">
              <el-table :data="perms" size="small" max-height="400">
                <el-table-column prop="targetId" label="메뉴ID" width="100" />
                <el-table-column prop="actionCd" label="액션" width="80" />
                <el-table-column prop="createdAt" label="부여일" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="변경이력" name="audit">
              <div v-if="!auditData">
                <el-button size="small" @click="loadAudit">불러오기</el-button>
              </div>
              <div v-else>
                <h4 style="margin: 4px 0;">멤버 변경 (Outbox 이벤트, 최근 {{ auditData.outbox_events.length }}건)</h4>
                <el-table :data="auditData.outbox_events" size="small" max-height="240">
                  <el-table-column prop="seq" label="seq" width="80" />
                  <el-table-column prop="eventType" label="이벤트" width="180" />
                  <el-table-column prop="createdAt" label="시각" width="180" />
                  <el-table-column label="상세">
                    <template #default="{ row }">
                      <pre style="font-size:11px;margin:0">{{ JSON.stringify(row.payload) }}</pre>
                    </template>
                  </el-table-column>
                </el-table>
                <h4 style="margin: 12px 0 4px;">권한 변경 (Audit Log, 최근 {{ auditData.permission_audit.length }}건)</h4>
                <el-table :data="auditData.permission_audit" size="small" max-height="240">
                  <el-table-column prop="occurredAt" label="시각" width="180" />
                  <el-table-column prop="actorId" label="행위자" width="140" />
                  <el-table-column prop="action" label="작업" width="110" />
                  <el-table-column prop="targetId" label="메뉴ID" width="100" />
                  <el-table-column prop="actionCd" label="액션" width="80" />
                </el-table>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </el-col>
    </el-row>

    <!-- 신규 그룹 다이얼로그 -->
    <el-dialog v-model="showNewDlg" title="신규 그룹" width="500px">
      <el-form label-width="100px">
        <el-form-item label="그룹명"><el-input v-model="newG.groupNm" /></el-form-item>
        <el-form-item v-if="tab !== 'cg'" label="회사">
          <el-select v-model="newG.companyCd" filterable>
            <el-option v-for="c in companies" :key="c.companyCd" :label="`${c.companyCd} ${c.companyNm}`" :value="c.companyCd" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showNewDlg = false">취소</el-button>
        <el-button type="primary" @click="createGroup">생성</el-button>
      </template>
    </el-dialog>

    <!-- 멤버 추가 다이얼로그 — 타입별 UI -->
    <el-dialog v-model="addMemberDlg" :title="addDlgTitle" :width="addDlgWidth" @close="resetAddState">
      <el-input v-model="addSearch" :placeholder="searchPlaceholder" size="small" :prefix-icon="Search" clearable style="margin-bottom:8px;" />

      <!-- CG: 회사 체크박스 리스트 -->
      <div v-if="tab === 'cg'" class="member-pool">
        <el-checkbox-group v-model="cgPicked">
          <div v-for="c in filteredCompanies" :key="c.companyCd" class="member-row" :class="{ disabled: cgAlreadyMember(c.companyCd) }">
            <el-checkbox :value="c.companyCd" :disabled="cgAlreadyMember(c.companyCd)">
              <el-icon style="vertical-align:middle; color:#3b82f6;"><OfficeBuilding /></el-icon>
              <b style="margin-left:6px;">{{ c.companyCd }}</b> {{ c.companyNm }}
              <el-tag v-if="cgAlreadyMember(c.companyCd)" size="small" type="info" style="margin-left:6px;">이미 멤버</el-tag>
            </el-checkbox>
          </div>
        </el-checkbox-group>
        <div v-if="!filteredCompanies.length" class="hint sm">검색 결과 없음</div>
      </div>

      <!-- DG: 회사 → 부서 트리 -->
      <div v-else-if="tab === 'dg'" class="member-pool">
        <el-tree
          ref="dgTreeRef"
          :data="deptTree"
          show-checkbox
          node-key="key"
          :default-expand-all="true"
          :filter-node-method="treeFilter"
          :default-checked-keys="dgPreCheckedKeys">
          <template #default="{ data }">
            <span class="tree-node">
              <el-icon v-if="data.type === 'company'" style="color:#3b82f6;"><OfficeBuilding /></el-icon>
              <el-icon v-else style="color:#10b981;"><HomeFilled /></el-icon>
              <span :class="{ 'already-member': data.alreadyMember }">{{ data.label }}</span>
              <el-tag v-if="data.alreadyMember" size="small" type="info" style="margin-left:6px;">이미 멤버</el-tag>
            </span>
          </template>
        </el-tree>
      </div>

      <!-- UG: 회사 → 부서 → 사용자 트리 -->
      <div v-else class="member-pool">
        <el-tree
          ref="ugTreeRef"
          :data="userTree"
          show-checkbox
          node-key="key"
          :default-expand-level="1"
          :filter-node-method="treeFilter"
          :default-checked-keys="ugPreCheckedKeys">
          <template #default="{ data }">
            <span class="tree-node">
              <el-icon v-if="data.type === 'company'" style="color:#3b82f6;"><OfficeBuilding /></el-icon>
              <el-icon v-else-if="data.type === 'dept'" style="color:#10b981;"><HomeFilled /></el-icon>
              <el-icon v-else style="color:#f59e0b;"><User /></el-icon>
              <span :class="{ 'already-member': data.alreadyMember }">{{ data.label }}</span>
              <el-tag v-if="data.type !== 'company' && data.type !== 'dept' && data.alreadyMember" size="small" type="info" style="margin-left:6px;">이미 멤버</el-tag>
            </span>
          </template>
        </el-tree>
      </div>

      <div class="add-footer-info">
        <el-tag size="small" type="success">신규 추가 대상: {{ pickedCount }}건</el-tag>
        <el-tag size="small" type="info" effect="plain">상위 노드 체크 시 자손 모두 자동 선택</el-tag>
      </div>

      <template #footer>
        <el-button @click="addMemberDlg = false">취소</el-button>
        <el-button type="primary" :disabled="!pickedCount" @click="applyAddMembers">추가 ({{ pickedCount }})</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="csvDlg" title="CSV 가져오기 (UG 멤버 일괄 등록)" width="600px">
      <p>한 줄에 user_id 하나씩, 빈 줄·`#` 시작 줄은 무시됩니다.</p>
      <el-input v-model="csvText" type="textarea" :rows="10" placeholder="U00001
U00002" />
      <template #footer>
        <el-button @click="csvDlg = false">취소</el-button>
        <el-button type="primary" @click="importCsv">가져오기</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { Plus, Search, OfficeBuilding, HomeFilled, User } from '@element-plus/icons-vue'
import { Master } from '@/api'
import api from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAppStore } from '@/store'

const app = useAppStore()
const tab = ref('ug')
const detailTab = ref('members')
const search = ref('')
const groups = ref([])
const selected = ref(null)
const members = ref([])
const perms = ref([])
const companies = ref([])
const users = ref([])
const showNewDlg = ref(false)
const newG = reactive({ groupNm: '', companyCd: null })
const csvDlg = ref(false)
const csvText = ref('')

// add-member dialog state
const addMemberDlg = ref(false)
const addSearch = ref('')
const cgPicked = ref([])             // company codes to add
const dgTreeRef = ref(null)
const ugTreeRef = ref(null)
const dgPreCheckedKeys = ref([])
const ugPreCheckedKeys = ref([])

const filteredGroups = computed(() => {
  const s = search.value.toLowerCase()
  return groups.value.filter(g => !s || ((g.groupNm||'') + (g.companyCd||'')).toLowerCase().includes(s))
})

const memberCols = computed(() => {
  if (tab.value === 'cg') return [{ key: 'companyCd', label: '회사', width: 200 }]
  if (tab.value === 'dg') return [{ key: 'companyCd', label: '회사', width: 120 }, { key: 'deptId', label: '부서ID', width: 200 }]
  return [{ key: 'userId', label: '사용자ID', width: 200 }]
})

const addDlgTitle = computed(() => ({
  cg: '회사 그룹 — 회사 추가',
  dg: '부서 그룹 — 부서 추가',
  ug: '사용자 그룹 — 사용자 추가'
})[tab.value] || '멤버 추가')
const addDlgWidth = computed(() => tab.value === 'cg' ? '520px' : '700px')
const searchPlaceholder = computed(() => ({
  cg: '회사명 / 코드 검색',
  dg: '회사 / 부서명 검색',
  ug: '회사 / 부서 / 사용자 검색'
})[tab.value])

const filteredCompanies = computed(() => {
  const s = addSearch.value.toLowerCase()
  return companies.value.filter(c => !s || (c.companyCd + c.companyNm).toLowerCase().includes(s))
})

function gid (g) { return g?.companyGroupId ?? g?.deptGroupId ?? g?.userGroupId }

// ============================================================
// 트리 데이터 빌더
// ============================================================
const memberSet = computed(() => {
  // current group's existing member identifiers
  const set = new Set()
  if (tab.value === 'cg') members.value.forEach(m => set.add(m.companyCd))
  else if (tab.value === 'dg') members.value.forEach(m => set.add(`${m.companyCd}:${m.deptId}`))
  else members.value.forEach(m => set.add(m.userId))
  return set
})

function cgAlreadyMember (cd) { return memberSet.value.has(cd) }

const deptTree = computed(() => {
  return companies.value.map(c => {
    const depts = (c._depts || []).map(d => {
      const key = `dept:${c.companyCd}:${d.deptId}`
      return {
        key, type: 'dept', label: `${d.deptId} ${d.deptNm}`,
        companyCd: c.companyCd, deptId: d.deptId,
        alreadyMember: memberSet.value.has(`${c.companyCd}:${d.deptId}`),
        disabled: memberSet.value.has(`${c.companyCd}:${d.deptId}`)
      }
    })
    return {
      key: `company:${c.companyCd}`,
      type: 'company',
      label: `${c.companyCd} ${c.companyNm}`,
      children: depts
    }
  })
})

const userTree = computed(() => {
  return companies.value.map(c => {
    const depts = (c._depts || []).map(d => {
      const usersInDept = users.value.filter(u => u.companyCd === c.companyCd && u.deptId === d.deptId)
      const userNodes = usersInDept.map(u => {
        const key = `user:${u.userId}`
        return {
          key, type: 'user', label: `${u.userId} ${u.userNm}`,
          userId: u.userId, alreadyMember: memberSet.value.has(u.userId),
          disabled: memberSet.value.has(u.userId)
        }
      })
      return {
        key: `dept:${c.companyCd}:${d.deptId}`,
        type: 'dept',
        label: `${d.deptCd || d.deptId} ${d.deptNm} (${userNodes.length}명)`,
        children: userNodes
      }
    }).filter(d => d.children.length > 0)
    return {
      key: `company:${c.companyCd}`, type: 'company',
      label: `${c.companyCd} ${c.companyNm}`, children: depts
    }
  }).filter(c => c.children.length > 0)
})

function treeFilter (val, data) {
  if (!val) return true
  return (data.label || '').toLowerCase().includes(val.toLowerCase())
}
watch(addSearch, v => {
  if (tab.value === 'dg') dgTreeRef.value?.filter(v)
  else if (tab.value === 'ug') ugTreeRef.value?.filter(v)
})

// ============================================================
// 신규 추가 대상 카운트 (탭별)
// ============================================================
const pickedCount = computed(() => {
  if (tab.value === 'cg') return cgPicked.value.filter(c => !cgAlreadyMember(c)).length
  if (tab.value === 'dg') return getCheckedNewDepts().length
  if (tab.value === 'ug') return getCheckedNewUsers().length
  return 0
})

function getCheckedNewDepts () {
  if (!dgTreeRef.value) return []
  return dgTreeRef.value.getCheckedNodes(true)   // leaf only
      .filter(n => n.type === 'dept' && !n.alreadyMember)
}
function getCheckedNewUsers () {
  if (!ugTreeRef.value) return []
  return ugTreeRef.value.getCheckedNodes(true)
      .filter(n => n.type === 'user' && !n.alreadyMember)
}

// computes update on every check/uncheck — el-tree doesn't have reactive getCheckedKeys binding.
// Use a tick-driven recount via dialog 'visible' watch + manual refreshes.
const recountTick = ref(0)
const _ = computed(() => recountTick.value && pickedCount.value)
function bumpCount () { recountTick.value++ }

// ============================================================
// Lifecycle
// ============================================================
onMounted(async () => {
  companies.value = await Master.companies()
  for (const c of companies.value) c._depts = await Master.depts(c.companyCd)
  users.value = await Master.users()
  await loadGroups()
})

watch(tab, () => loadGroups())

async function loadGroups () {
  const url = tab.value === 'cg' ? '/groups/company' : tab.value === 'dg' ? '/groups/dept' : '/groups/user'
  groups.value = await api.get(url).then(r => r.data)
  selected.value = null
  members.value = []
  perms.value = []
}

async function select (g) {
  selected.value = { ...g }
  auditData.value = null
  await loadMembers()
  await loadPerms()
}

const auditData = ref(null)
async function loadAudit () {
  const id = gid(selected.value)
  const root = tab.value === 'cg' ? 'company' : tab.value === 'dg' ? 'dept' : 'user'
  auditData.value = await api.get(`/groups/${root}/${id}/audit?size=50`).then(r => r.data).catch(() => ({ outbox_events: [], permission_audit: [] }))
}

async function loadMembers () {
  const id = gid(selected.value)
  const root = tab.value === 'cg' ? 'company' : tab.value === 'dg' ? 'dept' : 'user'
  members.value = await api.get(`/groups/${root}/${id}/members`).then(r => r.data)
}

async function loadPerms () {
  const id = gid(selected.value)
  const subjectType = tab.value === 'cg' ? 'CG' : tab.value === 'dg' ? 'DG' : 'UG'
  perms.value = await Master.permissionsBySubject({
    system_cd: app.systemCd, subject_type: subjectType, subject_id: String(id)
  }).catch(() => [])
}

function newGroup () { newG.groupNm = ''; newG.companyCd = null; showNewDlg.value = true }

async function createGroup () {
  if (!newG.groupNm) return ElMessage.error('그룹명 필수')
  const root = tab.value === 'cg' ? 'company' : tab.value === 'dg' ? 'dept' : 'user'
  await api.post(`/groups/${root}`, { groupNm: newG.groupNm, companyCd: newG.companyCd, groupType: tab.value.toUpperCase() })
  ElMessage.success('생성됨')
  showNewDlg.value = false
  await loadGroups()
}

async function save () {
  const id = gid(selected.value)
  const root = tab.value === 'cg' ? 'company' : tab.value === 'dg' ? 'dept' : 'user'
  await api.put(`/groups/${root}/${id}`, selected.value)
  ElMessage.success('저장됨')
  await loadGroups()
}

async function del () {
  await ElMessageBox.confirm('삭제하시겠어요?', '확인', { type: 'warning' })
  const id = gid(selected.value)
  const root = tab.value === 'cg' ? 'company' : tab.value === 'dg' ? 'dept' : 'user'
  await api.delete(`/groups/${root}/${id}`)
  ElMessage.success('삭제됨')
  await loadGroups()
}

// ============================================================
// 다중 멤버 추가 (Add Dialog)
// ============================================================
function openAddDialog () {
  resetAddState()
  addMemberDlg.value = true
}
function resetAddState () {
  addSearch.value = ''
  cgPicked.value = []
  dgPreCheckedKeys.value = []
  ugPreCheckedKeys.value = []
}

async function applyAddMembers () {
  const id = gid(selected.value)
  if (tab.value === 'cg') {
    const toAdd = cgPicked.value.filter(c => !cgAlreadyMember(c))
    let added = 0
    for (const cd of toAdd) {
      try { await api.post(`/groups/company/${id}/members/${cd}`); added++ } catch {}
    }
    ElMessage.success(`회사 ${added}건 추가`)
  } else if (tab.value === 'dg') {
    const newDepts = getCheckedNewDepts()
    let added = 0
    for (const d of newDepts) {
      try { await api.post(`/groups/dept/${id}/members`, { companyCd: d.companyCd, deptId: d.deptId }); added++ } catch {}
    }
    ElMessage.success(`부서 ${added}건 추가`)
  } else {
    const newUsers = getCheckedNewUsers()
    let added = 0
    for (const u of newUsers) {
      try { await api.post(`/groups/user/${id}/members/${u.userId}`); added++ } catch {}
    }
    ElMessage.success(`사용자 ${added}건 추가`)
  }
  addMemberDlg.value = false
  await loadMembers()
}

async function removeMember (row) {
  const id = gid(selected.value)
  if (tab.value === 'cg') await api.delete(`/groups/company/${id}/members/${row.companyCd}`)
  else if (tab.value === 'dg') await api.delete(`/groups/dept/${id}/members/${row.companyCd}/${row.deptId}`)
  else await api.delete(`/groups/user/${id}/members/${row.userId}`)
  await loadMembers()
}

async function importCsv () {
  const id = gid(selected.value)
  const r = await api.post(`/groups/user/${id}/members/csv`, csvText.value, { headers: { 'Content-Type': 'text/plain' } })
  ElMessage.success(`추가 ${r.data.added} / 중복 ${r.data.skipped}`)
  csvDlg.value = false
  await loadMembers()
}
</script>

<style scoped>
.g-list { max-height: 480px; overflow: auto; margin-top: 6px; }
.g-item { padding: 8px; border-radius: 4px; cursor: pointer; border-bottom: 1px solid #f3f4f6; }
.g-item:hover { background: #f5f7fa; }
.g-item.active { background: #e6f0ff; border-left: 3px solid #1677ff; }
.g-item .sub { color: #94a3b8; font-size: 12px; }
.hint { padding: 40px; color: #94a3b8; text-align: center; }
.hint.sm { padding: 8px; }
.member-pool { max-height: 380px; overflow: auto; border: 1px solid #e5e7eb; border-radius: 6px; padding: 8px; background: #fafbfc; }
.member-row { padding: 4px 6px; border-bottom: 1px solid #f3f4f6; }
.member-row.disabled { opacity: 0.6; }
.tree-node { display: inline-flex; align-items: center; gap: 4px; }
.tree-node .already-member { color: #94a3b8; text-decoration: line-through; }
.add-footer-info { display:flex; gap:8px; padding: 8px 0 0; align-items:center; }
</style>
