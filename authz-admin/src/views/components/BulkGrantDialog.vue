<template>
  <el-dialog v-model="visible" title="일괄 권한 부여" width="780px" @close="reset()">
    <!-- step 1: subject -->
    <el-card shadow="never" class="step">
      <template #header><b>① 부여 대상 주체</b></template>
      <el-radio-group v-model="form.subjectType" size="default" @change="reloadSubjects">
        <el-radio-button value="C">회사</el-radio-button>
        <el-radio-button value="D">부서</el-radio-button>
        <el-radio-button value="U">사용자</el-radio-button>
        <el-radio-button value="CG">회사그룹</el-radio-button>
        <el-radio-button value="DG">부서그룹</el-radio-button>
        <el-radio-button value="UG">사용자그룹</el-radio-button>
      </el-radio-group>
      <el-select v-model="form.subjectId" filterable placeholder="대상 선택" style="margin-top:8px; width:100%;" @change="recompute">
        <el-option v-for="s in subjects" :key="s.id" :label="s.label" :value="s.id" />
      </el-select>
    </el-card>

    <!-- step 2: menus -->
    <el-card shadow="never" class="step">
      <template #header>
        <b>② 부여 대상 메뉴</b>
        <span style="margin-left:10px; color:#6b7280;">{{ form.targetMenuIds.length }}개 선택됨</span>
      </template>
      <el-radio-group v-model="targetMode" size="small" @change="recompute">
        <el-radio-button value="single">개별 메뉴 선택</el-radio-button>
        <el-radio-button value="folder">폴더 선택 (자손 자동 전개)</el-radio-button>
      </el-radio-group>
      <div class="tree-wrap">
        <el-tree :data="tree" :props="{ label: 'menu_nm', children: 'children' }"
                 node-key="menu_id" show-checkbox check-strictly
                 @check-change="onTreeCheck" />
      </div>
    </el-card>

    <!-- step 3: actions -->
    <el-card shadow="never" class="step">
      <template #header><b>③ 부여 액션</b></template>
      <el-checkbox-group v-model="form.actions" @change="recompute">
        <el-checkbox v-for="a in actionDefs" :key="a.actionCd" :value="a.actionCd">
          <b>{{ a.actionCd }}</b> · {{ a.actionNm }}
        </el-checkbox>
      </el-checkbox-group>
    </el-card>

    <!-- step 4: validity -->
    <el-card shadow="never" class="step">
      <template #header><b>④ 유효 기간 (선택)</b></template>
      <el-date-picker v-model="form.validFrom" type="datetime" placeholder="시작" style="margin-right:8px" />
      <el-date-picker v-model="form.validTo" type="datetime" placeholder="종료" />
    </el-card>

    <!-- impact preview -->
    <el-alert v-if="impact" type="warning" :closable="false" class="impact">
      <template #title>⚠ 영향 범위 사전 확인</template>
      <table class="imp-tbl">
        <tr><td>부여 건수</td><td><b>{{ impact.grant_count }}건</b><span class="hint">(메뉴 {{ impact.leaf_count }} × 액션 {{ impact.action_count }})</span></td></tr>
        <tr><td>영향 사용자</td><td><b>{{ impact.affected_users }}명</b></td></tr>
        <tr><td>신규 허용 API 수</td><td><b>{{ impact.new_api_count }}개</b></td></tr>
        <tr><td>캐시 갱신 규모</td><td><code>perm:api:U:* — {{ impact.cache_rebuild_keys }}건 rebuild</code></td></tr>
        <tr><td>예상 처리 시간</td><td>약 {{ Math.round(impact.est_ms / 1000) }}초</td></tr>
      </table>
    </el-alert>

    <template #footer>
      <el-button @click="visible = false">취소</el-button>
      <el-button @click="recompute">미리보기</el-button>
      <el-button type="primary" :disabled="!canApply" @click="apply">부여 실행</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, defineExpose, watch } from 'vue'
import { Master } from '@/api'
import api from '@/api'
import { ElMessage } from 'element-plus'
import { useAppStore } from '@/store'

const app = useAppStore()
const visible = ref(false)
const subjects = ref([])
const tree = ref([])
const actionDefs = ref([])
const targetMode = ref('single')
const impact = ref(null)
const form = reactive({
  subjectType: 'UG', subjectId: null, targetMenuIds: [],
  actions: [], validFrom: null, validTo: null
})

defineExpose({ open })

async function open () {
  visible.value = true
  reset()
  await loadEverything()
}

function reset () {
  Object.assign(form, { subjectType: 'UG', subjectId: null, targetMenuIds: [], actions: [], validFrom: null, validTo: null })
  impact.value = null
  targetMode.value = 'single'
}

async function loadEverything () {
  await reloadSubjects()
  const flat = await Master.menus(app.systemCd)
  actionDefs.value = await Master.actions(app.systemCd)
  tree.value = buildTree(flat)
}

async function reloadSubjects () {
  if (form.subjectType === 'C') {
    subjects.value = (await Master.companies()).map(c => ({ id: c.companyCd, label: `${c.companyCd} ${c.companyNm}` }))
  } else if (form.subjectType === 'D') {
    const cs = await Master.companies()
    const all = []
    for (const c of cs) {
      const ds = await Master.depts(c.companyCd)
      ds.forEach(d => all.push({ id: d.deptId, label: `${c.companyCd}/${d.deptId} ${d.deptNm}` }))
    }
    subjects.value = all
  } else if (form.subjectType === 'U') {
    subjects.value = (await Master.users()).map(u => ({ id: u.userId, label: `${u.userId} ${u.userNm}` }))
  } else if (form.subjectType === 'CG') {
    subjects.value = (await api.get('/groups/company').then(r => r.data)).map(g => ({ id: String(g.companyGroupId), label: g.groupNm }))
  } else if (form.subjectType === 'DG') {
    subjects.value = (await api.get('/groups/dept').then(r => r.data)).map(g => ({ id: String(g.deptGroupId), label: g.groupNm }))
  } else if (form.subjectType === 'UG') {
    subjects.value = (await api.get('/groups/user').then(r => r.data)).map(g => ({ id: String(g.userGroupId), label: g.groupNm }))
  }
}

function buildTree (flat) {
  const map = new Map()
  flat.forEach(m => map.set(m.menuId, { menu_id: m.menuId, menu_nm: m.menuNm, menu_type: m.menuType, parent_menu_id: m.parentMenuId, children: [] }))
  const roots = []
  flat.forEach(m => {
    const node = map.get(m.menuId)
    if (m.parentMenuId && map.get(m.parentMenuId)) map.get(m.parentMenuId).children.push(node)
    else roots.push(node)
  })
  return roots
}

function onTreeCheck (data, checked) {
  if (checked && targetMode.value === 'single' && data.menu_type !== 'M') return
  if (checked && targetMode.value === 'folder' && data.menu_type !== 'F') return
  if (checked) {
    if (!form.targetMenuIds.includes(data.menu_id)) form.targetMenuIds.push(data.menu_id)
  } else {
    form.targetMenuIds = form.targetMenuIds.filter(id => id !== data.menu_id)
  }
  recompute()
}

const canApply = computed(() =>
  form.subjectId && form.targetMenuIds.length > 0 && form.actions.length > 0
)

async function recompute () {
  if (!canApply.value) { impact.value = null; return }
  impact.value = await api.post('/permissions/bulk/preview', {
    systemCd: app.systemCd,
    companyCd: null,
    subjectType: form.subjectType,
    subjectId: form.subjectId,
    targetMenuIds: form.targetMenuIds,
    actions: form.actions,
    validFrom: form.validFrom,
    validTo: form.validTo
  }).then(r => r.data)
}

async function apply () {
  await api.post('/permissions/bulk', {
    systemCd: app.systemCd,
    companyCd: null,
    subjectType: form.subjectType,
    subjectId: form.subjectId,
    targetMenuIds: form.targetMenuIds,
    actions: form.actions,
    validFrom: form.validFrom,
    validTo: form.validTo
  }, { headers: { 'X-User-Id': app.actor }}).then(r => r.data).then(res => {
    ElMessage.success(`적용: ${res.created}건 신규 / ${res.skipped}건 중복 스킵`)
  })
  visible.value = false
}
</script>

<style scoped>
.step { margin-bottom: 10px; }
.step :deep(.el-card__header) { padding: 10px 14px; }
.step :deep(.el-card__body) { padding: 10px 14px; }
.tree-wrap { max-height: 200px; overflow: auto; border: 1px solid #e5e7eb; border-radius: 4px; padding: 4px; margin-top: 6px; }
.impact { margin-top: 6px; }
.imp-tbl { width: 100%; border-collapse: collapse; }
.imp-tbl td { padding: 4px 8px; border-bottom: 1px dashed #f3f4f6; }
.imp-tbl td:first-child { color: #6b7280; width: 140px; }
.imp-tbl .hint { color: #94a3b8; font-size: 12px; margin-left: 6px; }
</style>
