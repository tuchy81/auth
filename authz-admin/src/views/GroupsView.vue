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
                <el-button size="small" @click="addMemberDlg = true">+ 멤버 추가</el-button>
                <el-button size="small" v-if="tab==='ug'" @click="csvDlg = true">📁 CSV 가져오기</el-button>
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
          </el-tabs>
        </div>
      </el-col>
    </el-row>

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

    <el-dialog v-model="addMemberDlg" title="멤버 추가" width="500px">
      <el-select v-if="tab === 'cg'" v-model="newMember" placeholder="회사 선택" filterable style="width:100%">
        <el-option v-for="c in companies" :key="c.companyCd" :label="`${c.companyCd} ${c.companyNm}`" :value="c.companyCd" />
      </el-select>
      <el-cascader v-else-if="tab === 'dg'" v-model="newMember" :options="deptOptions" placeholder="회사 → 부서" style="width:100%" />
      <el-select v-else v-model="newMember" placeholder="사용자 선택" filterable style="width:100%">
        <el-option v-for="u in users" :key="u.userId" :label="`${u.userId} ${u.userNm}`" :value="u.userId" />
      </el-select>
      <template #footer>
        <el-button @click="addMemberDlg = false">취소</el-button>
        <el-button type="primary" @click="addMember">추가</el-button>
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
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
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
const addMemberDlg = ref(false)
const newMember = ref(null)
const csvDlg = ref(false)
const csvText = ref('')

const filteredGroups = computed(() => {
  const s = search.value.toLowerCase()
  return groups.value.filter(g => !s || ((g.groupNm||'') + (g.companyCd||'')).toLowerCase().includes(s))
})

const memberCols = computed(() => {
  if (tab.value === 'cg') return [{ key: 'companyCd', label: '회사', width: 200 }]
  if (tab.value === 'dg') return [{ key: 'companyCd', label: '회사', width: 120 }, { key: 'deptId', label: '부서ID', width: 200 }]
  return [{ key: 'userId', label: '사용자ID', width: 200 }]
})

const deptOptions = computed(() => companies.value.map(c => ({
  value: c.companyCd, label: `${c.companyCd} ${c.companyNm}`,
  children: (c._depts || []).map(d => ({ value: d.deptId, label: `${d.deptId} ${d.deptNm}` }))
})))

function gid (g) { return g?.companyGroupId ?? g?.deptGroupId ?? g?.userGroupId }

onMounted(async () => {
  companies.value = await Master.companies()
  for (const c of companies.value) c._depts = await Master.depts(c.companyCd)
  users.value = await Master.users()
  await loadGroups()
})

async function loadGroups () {
  const url = tab.value === 'cg' ? '/groups/company' : tab.value === 'dg' ? '/groups/dept' : '/groups/user'
  groups.value = await api.get(url).then(r => r.data)
  selected.value = null
  members.value = []
  perms.value = []
}

async function select (g) {
  selected.value = { ...g }
  await loadMembers()
  await loadPerms()
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

async function addMember () {
  const id = gid(selected.value)
  if (tab.value === 'cg') await api.post(`/groups/company/${id}/members/${newMember.value}`)
  else if (tab.value === 'dg') {
    const [companyCd, deptId] = newMember.value
    await api.post(`/groups/dept/${id}/members`, { companyCd, deptId })
  } else await api.post(`/groups/user/${id}/members/${newMember.value}`)
  newMember.value = null
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
</style>
