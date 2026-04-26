<template>
  <el-row :gutter="16">
    <el-col :span="6">
      <el-card>
        <template #header><b>주체 선택</b></template>
        <el-radio-group v-model="subjectType" size="small">
          <el-radio-button value="C">회사</el-radio-button>
          <el-radio-button value="D">부서</el-radio-button>
          <el-radio-button value="U">사용자</el-radio-button>
        </el-radio-group>
        <el-input v-model="search" placeholder="검색" size="small" style="margin-top:8px" />
        <div class="list">
          <el-tag
            v-for="s in filtered" :key="s.id"
            :type="selected?.id===s.id ? 'primary' : 'info'"
            effect="plain" size="large"
            @click="selected = s" style="cursor:pointer; margin: 3px 4px;">
            {{ s.label }}
          </el-tag>
        </div>
      </el-card>
    </el-col>

    <el-col :span="10">
      <el-card>
        <template #header>
          <b>메뉴 트리</b>
          <el-select v-model="systemCd" size="small" style="margin-left:12px; width:160px;">
            <el-option v-for="s in systems" :key="s.systemCd" :label="s.systemCd" :value="s.systemCd" />
          </el-select>
        </template>
        <el-tree :data="tree" :props="{ label: 'menu_nm', children: 'children' }" node-key="menu_id"
                 highlight-current @node-click="onMenuClick" />
      </el-card>
    </el-col>

    <el-col :span="8">
      <el-card>
        <template #header><b>액션 & 부여</b></template>
        <div v-if="selectedMenu">
          <p>대상: <b>{{ selectedMenu.menu_nm }}</b> ({{ selectedMenu.menu_type }})</p>
          <el-checkbox-group v-model="actions">
            <el-checkbox v-for="a in actionList" :key="a.actionCd" :label="a.actionCd">
              {{ a.actionCd }} - {{ a.actionNm }}
            </el-checkbox>
          </el-checkbox-group>
          <el-divider />
          <el-button type="primary" :disabled="!selected || actions.length===0" @click="grant">권한 부여</el-button>
          <el-button @click="simulate">시뮬레이션</el-button>
          <pre v-if="simResult" class="sim">{{ JSON.stringify(simResult, null, 2) }}</pre>
        </div>
        <el-empty v-else description="좌측에서 주체와 메뉴를 선택하세요" />
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { Master, Authz } from '@/api'
import { ElMessage } from 'element-plus'

const subjectType = ref('U')
const search = ref('')
const selected = ref(null)
const systems = ref([])
const systemCd = ref('ERP')
const tree = ref([])
const actionList = ref([])
const selectedMenu = ref(null)
const actions = ref([])
const simResult = ref(null)

const subjects = ref([])
async function loadSubjects () {
  if (subjectType.value === 'C') {
    const cs = await Master.companies()
    subjects.value = cs.map(c => ({ id: c.companyCd, label: `${c.companyCd} ${c.companyNm}`, type: 'C' }))
  } else if (subjectType.value === 'D') {
    const cs = await Master.companies()
    const all = []
    for (const c of cs) {
      const ds = await Master.depts(c.companyCd)
      ds.forEach(d => all.push({ id: d.deptId, label: `${d.companyCd}/${d.deptId} ${d.deptNm}`, type: 'D', companyCd: c.companyCd }))
    }
    subjects.value = all
  } else {
    const us = await Master.users()
    subjects.value = us.map(u => ({ id: u.userId, label: `${u.userId} ${u.userNm}`, type: 'U', companyCd: u.companyCd, deptId: u.deptId }))
  }
}

const filtered = computed(() => subjects.value.filter(s =>
  !search.value || s.label.toLowerCase().includes(search.value.toLowerCase())
).slice(0, 200))

onMounted(async () => {
  systems.value = await Master.systems()
  await loadSubjects()
  await loadTree()
})
watch(subjectType, loadSubjects)
watch(systemCd, async () => {
  await loadTree()
  actionList.value = await Master.actions(systemCd.value)
})

async function loadTree () {
  const menus = await Master.menus(systemCd.value)
  const map = new Map()
  menus.forEach(m => map.set(m.menuId, { menu_id: m.menuId, menu_nm: m.menuNm, menu_type: m.menuType, children: [] }))
  const roots = []
  menus.forEach(m => {
    const node = map.get(m.menuId)
    if (m.parentMenuId) map.get(m.parentMenuId)?.children.push(node)
    else roots.push(node)
  })
  tree.value = roots
  if (actionList.value.length === 0) actionList.value = await Master.actions(systemCd.value)
}

function onMenuClick (n) {
  selectedMenu.value = n
  actions.value = []
  simResult.value = null
}

async function grant () {
  if (!selected.value || !selectedMenu.value) return
  for (const act of actions.value) {
    await Master.grant({
      systemCd: systemCd.value,
      companyCd: selected.value.companyCd ?? selected.value.id,
      subjectType: selected.value.type,
      subjectId: selected.value.id,
      targetType: 'M',
      targetId: selectedMenu.value.menu_id,
      actionCd: act
    })
  }
  ElMessage.success(`${actions.value.length}건 부여 (Outbox 큐 적재 → 수 초 내 캐시 반영)`)
}

async function simulate () {
  if (!selected.value || !selectedMenu.value || actions.value.length === 0) return
  simResult.value = await Authz.simulateGrant({
    system_cd: systemCd.value,
    subject_type: selected.value.type,
    subject_id: selected.value.id,
    target_id: selectedMenu.value.menu_id,
    action_cd: actions.value[0]
  })
}
</script>

<style scoped>
.list { max-height: 480px; overflow: auto; margin-top: 6px; }
.sim { background: #f4f6f8; padding: 8px; font-size: 12px; max-height: 240px; overflow:auto; }
</style>
