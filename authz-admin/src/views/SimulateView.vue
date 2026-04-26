<template>
  <el-row :gutter="14">
    <el-col :span="10">
      <el-card>
        <template #header><b>What-if 시뮬레이션</b></template>
        <el-form label-width="100px">
          <el-form-item label="시스템"><el-input :model-value="app.systemCd" disabled /></el-form-item>
          <el-form-item label="작업">
            <el-radio-group v-model="form.op">
              <el-radio-button value="grant">GRANT</el-radio-button>
              <el-radio-button value="revoke">REVOKE</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="주체 타입">
            <el-radio-group v-model="form.subject_type" size="small">
              <el-radio-button value="C">회사</el-radio-button>
              <el-radio-button value="D">부서</el-radio-button>
              <el-radio-button value="U">사용자</el-radio-button>
              <el-radio-button value="UG">UG</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="주체 ID">
            <el-select v-model="form.subject_id" filterable style="width:100%;">
              <el-option v-for="s in subjects" :key="s.id" :label="s.label" :value="s.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="대상 메뉴">
            <el-tree-select v-model="form.target_id" :data="tree" :props="{ label: 'menu_nm', children: 'children' }"
                            node-key="menu_id" check-strictly :render-after-expand="false"
                            placeholder="폴더 선택 가능 — 자손 자동 전개" filterable @change="prefetchLeaves" style="width:100%;" />
          </el-form-item>
          <el-form-item label="액션">
            <el-checkbox-group v-model="form.actions">
              <el-checkbox v-for="a in actionDefs" :key="a.actionCd" :value="a.actionCd">
                <b>{{ a.actionCd }}</b>
              </el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="run" :disabled="!canRun">시뮬레이션 실행</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card v-if="leafPreview.length" style="margin-top:14px;">
        <template #header><b>📁 폴더 자손 리프 미리보기</b></template>
        <el-alert type="info" :closable="false">
          폴더 권한 부여 시 아래 <b>{{ leafPreview.length }}개</b> 리프 메뉴로 자동 전개됩니다.
        </el-alert>
        <el-table :data="leafPreview" size="small" max-height="300">
          <el-table-column prop="menu_id" label="ID" width="60" />
          <el-table-column prop="menu_cd" label="코드" />
          <el-table-column prop="menu_nm" label="이름" />
        </el-table>
      </el-card>
    </el-col>

    <el-col :span="14">
      <el-card v-if="result">
        <template #header>
          <b>시뮬레이션 결과</b>
          <el-tag :type="result.operation === 'GRANT' ? 'success' : 'warning'" size="small" style="margin-left:8px;">{{ result.operation }}</el-tag>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="이미 부여됨?">
            <el-tag size="small" :type="result.already_granted ? 'warning' : 'info'">
              {{ result.already_granted ? '예 (no-op)' : '아니요 (신규)' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="대상 메뉴">
            {{ result.target_menu?.menu_nm }} ({{ result.target_menu?.menu_type }})
          </el-descriptions-item>
          <el-descriptions-item label="자손 리프 수">
            <b>{{ result.affected_leaf_count }}</b>
          </el-descriptions-item>
          <el-descriptions-item label="영향 사용자수">
            <b>{{ result.affected_user_count }}</b>명
          </el-descriptions-item>
          <el-descriptions-item label="액션" :span="2">{{ result.action_cd }}</el-descriptions-item>
          <el-descriptions-item label="자손 리프 ID 목록" :span="2">
            <code style="font-size:11px;">{{ Array.from(result.affected_leaves || []).join(', ') }}</code>
          </el-descriptions-item>
        </el-descriptions>
        <el-divider content-position="left">변경 전/후 비교 (감사 로그에 SIM_RUN으로 기록됨)</el-divider>
        <pre style="background:#f4f6f8; padding:8px; max-height:240px; overflow:auto;">{{ JSON.stringify(result, null, 2) }}</pre>
      </el-card>
      <el-empty v-else description="좌측에서 작업 정보를 입력하고 [시뮬레이션 실행]을 누르세요" />
    </el-col>
  </el-row>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { Authz, Master } from '@/api'
import api from '@/api'
import { useAppStore } from '@/store'

const app = useAppStore()
const form = reactive({ op: 'grant', subject_type: 'U', subject_id: 'U00001', target_id: null, actions: ['R'] })
const subjects = ref([])
const flat = ref([])
const tree = ref([])
const actionDefs = ref([])
const result = ref(null)
const leafPreview = ref([])

onMounted(async () => {
  await loadSubjects()
  await loadMenusActions()
})
watch(() => app.systemCd, async () => { await loadMenusActions() })
watch(() => form.subject_type, loadSubjects)

async function loadSubjects () {
  if (form.subject_type === 'C') {
    subjects.value = (await Master.companies()).map(c => ({ id: c.companyCd, label: `${c.companyCd} ${c.companyNm}` }))
  } else if (form.subject_type === 'D') {
    const cs = await Master.companies()
    const all = []
    for (const c of cs) {
      const ds = await Master.depts(c.companyCd)
      ds.forEach(d => all.push({ id: d.deptId, label: `${c.companyCd}/${d.deptId} ${d.deptNm}` }))
    }
    subjects.value = all
  } else if (form.subject_type === 'U') {
    subjects.value = (await Master.users()).map(u => ({ id: u.userId, label: `${u.userId} ${u.userNm}` }))
  } else if (form.subject_type === 'UG') {
    subjects.value = (await api.get('/groups/user').then(r => r.data)).map(g => ({ id: String(g.userGroupId), label: g.groupNm }))
  }
}
async function loadMenusActions () {
  flat.value = await Master.menus(app.systemCd)
  actionDefs.value = await Master.actions(app.systemCd)
  buildTree()
}
function buildTree () {
  const map = new Map()
  flat.value.forEach(m => map.set(m.menuId, { menu_id: m.menuId, menu_cd: m.menuCd, menu_nm: m.menuNm, menu_type: m.menuType, parent_menu_id: m.parentMenuId, children: [] }))
  const roots = []
  flat.value.forEach(m => {
    const node = map.get(m.menuId)
    if (m.parentMenuId && map.get(m.parentMenuId)) map.get(m.parentMenuId).children.push(node)
    else roots.push(node)
  })
  tree.value = roots
}

const canRun = computed(() => form.subject_id && form.target_id && form.actions.length)

function prefetchLeaves () {
  const node = findNode(tree.value, form.target_id)
  if (!node) { leafPreview.value = []; return }
  if (node.menu_type !== 'F') { leafPreview.value = []; return }
  const leaves = []
  const walk = n => {
    if (n.menu_type === 'M') leaves.push(n)
    else (n.children || []).forEach(walk)
  }
  walk(node)
  leafPreview.value = leaves
}
function findNode (nodes, id) {
  for (const n of nodes) {
    if (n.menu_id === id) return n
    if (n.children) {
      const f = findNode(n.children, id)
      if (f) return f
    }
  }
  return null
}

async function run () {
  const body = {
    system_cd: app.systemCd,
    subject_type: form.subject_type,
    subject_id: form.subject_id,
    target_id: form.target_id,
    action_cd: form.actions[0]
  }
  result.value = form.op === 'grant'
    ? await Authz.simulateGrant(body)
    : await Authz.simulateRevoke(body)
}
</script>
