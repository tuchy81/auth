<template>
  <div class="systems-page">
    <el-row :gutter="14">
      <!-- 좌측 시스템 목록 -->
      <el-col :span="6">
        <el-card>
          <template #header>
            <div style="display:flex; align-items:center; justify-content:space-between;">
              <b>시스템 목록</b>
              <el-button type="primary" size="small" @click="newSystem"><el-icon><Plus /></el-icon>신규</el-button>
            </div>
          </template>
          <el-input v-model="search" placeholder="시스템명 / 코드 검색" size="small" style="margin-bottom:8px" />
          <div class="sys-list">
            <div v-for="s in filtered" :key="s.systemCd"
                 class="sys-item" :class="{active: selected?.systemCd === s.systemCd}"
                 @click="select(s)">
              <div class="row1">
                <el-tag :type="statusType(s.status)" size="small">{{ s.systemCd }}</el-tag>
                <span class="nm">{{ s.systemNm }}</span>
              </div>
              <div class="row2">
                {{ s.systemType || '-' }} · {{ s.ownerCompanyCd || '-' }}
                <span v-if="apiCounts[s.systemCd] != null" class="api-cnt">· {{ apiCounts[s.systemCd] }} API</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 우측 상세 -->
      <el-col :span="18">
        <el-card v-if="selected || isNew">
          <template #header>
            <div style="display:flex; align-items:center; justify-content:space-between;">
              <div>
                <el-tag :type="statusType(form.status)" size="default">{{ form.status || '?' }}</el-tag>
                <b style="margin-left:8px;">{{ form.systemNm || '신규 시스템' }}</b>
                <span v-if="form.systemCd" class="cd">[{{ form.systemCd }}]</span>
              </div>
              <div>
                <el-button @click="cancel">취소</el-button>
                <el-button type="danger" v-if="!isNew" @click="del">삭제</el-button>
                <el-button type="primary" @click="save">저장</el-button>
              </div>
            </div>
          </template>

          <!-- 통계 바 -->
          <el-row :gutter="10" v-if="!isNew" class="stats-bar">
            <el-col :span="4"><el-card shadow="never"><el-statistic :value="stats.menus ?? 0" title="메뉴" /></el-card></el-col>
            <el-col :span="4"><el-card shadow="never">
              <el-statistic :value="stats.apis ?? 0" title="API" />
              <div class="hint">매핑 {{ stats.api_mapped }} / 미매핑 {{ stats.api_unmapped }}</div>
            </el-card></el-col>
            <el-col :span="4"><el-card shadow="never"><el-statistic :value="stats.permissions ?? 0" title="권한 부여" /></el-card></el-col>
            <el-col :span="4"><el-card shadow="never"><el-statistic :value="stats.active_users ?? 0" title="활성 사용자" /></el-card></el-col>
            <el-col :span="4"><el-card shadow="never">
              <div class="el-statistic">
                <div class="el-statistic__head">샤딩 전략</div>
                <div class="el-statistic__content"><span class="el-statistic__number" style="font-size:14px;">{{ stats.shard_strategy || '-' }}</span></div>
              </div>
            </el-card></el-col>
            <el-col :span="4"><el-card shadow="never"><el-statistic :value="stats.cache_keys ?? 0" title="캐시 키" /></el-card></el-col>
          </el-row>

          <el-tabs v-model="tab">
            <!-- 기본정보 -->
            <el-tab-pane label="기본정보" name="basic">
              <el-form label-width="120px" style="max-width:680px;">
                <el-form-item label="시스템 코드"><el-input v-model="form.systemCd" :disabled="!isNew" /></el-form-item>
                <el-form-item label="시스템명 (한글)"><el-input v-model="form.systemNm" /></el-form-item>
                <el-form-item label="시스템명 (영문)"><el-input v-model="form.systemNmEn" /></el-form-item>
                <el-form-item label="설명"><el-input v-model="form.description" type="textarea" rows="2" /></el-form-item>
              </el-form>
            </el-tab-pane>

            <!-- 소유 책임 -->
            <el-tab-pane label="소유·책임" name="owner">
              <el-form label-width="120px" style="max-width:680px;">
                <el-form-item label="소유 회사">
                  <el-select v-model="form.ownerCompanyCd" filterable placeholder="회사 선택">
                    <el-option v-for="c in companies" :key="c.companyCd" :label="`${c.companyCd} ${c.companyNm}`" :value="c.companyCd" />
                  </el-select>
                </el-form-item>
                <el-form-item label="사업부"><el-input v-model="form.ownerDivision" placeholder="조선사업부" /></el-form-item>
                <el-form-item label="운영 부서"><el-input v-model="form.ownerDeptId" /></el-form-item>
                <el-form-item label="시스템 책임자"><el-input v-model="form.ownerUserId" /></el-form-item>
              </el-form>
            </el-tab-pane>

            <!-- 기술 메타 -->
            <el-tab-pane label="기술 메타" name="tech">
              <el-form label-width="120px" style="max-width:680px;">
                <el-form-item label="시스템 유형">
                  <el-select v-model="form.systemType">
                    <el-option v-for="t in ['ERP','MES','PLM','PORTAL','HR','ETC']" :key="t" :label="t" :value="t" />
                  </el-select>
                </el-form-item>
                <el-form-item label="비즈니스 분류"><el-input v-model="form.systemCategory" /></el-form-item>
                <el-form-item label="Base URL"><el-input v-model="form.baseUrl" placeholder="https://erp.hd.co.kr" /></el-form-item>
                <el-form-item label="Frontend">
                  <el-select v-model="form.frontendType">
                    <el-option v-for="t in ['VUE','REACT','JSP','THYMELEAF','ETC']" :key="t" :label="t" :value="t" />
                  </el-select>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <!-- 운영 -->
            <el-tab-pane label="운영" name="ops">
              <el-form label-width="120px" style="max-width:680px;">
                <el-form-item label="상태">
                  <el-radio-group v-model="form.status">
                    <el-radio-button value="A">활성 (A)</el-radio-button>
                    <el-radio-button value="P">준비 (P)</el-radio-button>
                    <el-radio-button value="I">비활성 (I)</el-radio-button>
                    <el-radio-button value="D">폐기 (D)</el-radio-button>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="오픈일"><el-date-picker v-model="form.goLiveDate" value-format="YYYY-MM-DD" type="date" /></el-form-item>
                <el-form-item label="폐기 예정일"><el-date-picker v-model="form.endOfLifeDate" value-format="YYYY-MM-DD" type="date" /></el-form-item>
              </el-form>
            </el-tab-pane>

            <!-- 추가 속성 -->
            <el-tab-pane label="추가 속성" name="attrs" :disabled="isNew">
              <el-table :data="attrs" size="small">
                <el-table-column prop="attrKey" label="키" width="220" />
                <el-table-column prop="attrValue" label="값" />
                <el-table-column label="관리" width="80">
                  <template #default="{ row }">
                    <el-button size="small" type="danger" link @click="delAttr(row.attrKey)">삭제</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-form inline style="margin-top:10px;">
                <el-form-item label="키"><el-input v-model="newAttr.key" placeholder="sla_tier" /></el-form-item>
                <el-form-item label="값"><el-input v-model="newAttr.value" placeholder="gold" /></el-form-item>
                <el-form-item><el-button type="primary" size="small" @click="addAttr">+ 속성 추가</el-button></el-form-item>
              </el-form>
            </el-tab-pane>

            <!-- 변경이력 -->
            <el-tab-pane label="변경이력" name="history" :disabled="isNew">
              <el-table :data="history" size="small">
                <el-table-column prop="occurredAt" label="시각" width="180" />
                <el-table-column prop="actorId" label="행위자" width="140" />
                <el-table-column prop="action" label="작업" width="140" />
                <el-table-column label="상세">
                  <template #default="{ row }">
                    <pre style="font-size:11px; margin:0">{{ JSON.stringify(row.detail) }}</pre>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </el-card>
        <el-empty v-else description="좌측에서 시스템을 선택하거나 신규 등록하세요" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { Master, Audit } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAppStore } from '@/store'

const app = useAppStore()
const search = ref('')
const systems = ref([])
const apiCounts = ref({})
const selected = ref(null)
const isNew = ref(false)
const tab = ref('basic')
const form = ref({})
const stats = ref({})
const attrs = ref([])
const newAttr = ref({ key: '', value: '' })
const companies = ref([])
const history = ref([])

const filtered = computed(() => {
  const s = search.value.toLowerCase()
  return systems.value.filter(x => !s || (x.systemCd + x.systemNm).toLowerCase().includes(s))
})

function statusType (s) {
  return ({ A: 'success', P: 'warning', I: 'info', D: 'danger' })[s] || 'info'
}

onMounted(async () => {
  await load()
  companies.value = await Master.companies()
})

async function load () {
  systems.value = await Master.systems()
  // pre-fetch api counts (one stats call per system)
  const map = {}
  await Promise.all(systems.value.map(async s => {
    try { const st = await Master.systemStats(s.systemCd); map[s.systemCd] = st.apis }
    catch { map[s.systemCd] = null }
  }))
  apiCounts.value = map
}

function newSystem () {
  isNew.value = true
  selected.value = null
  form.value = { status: 'P', frontendType: 'VUE' }
  attrs.value = []
  history.value = []
  stats.value = {}
  tab.value = 'basic'
}

async function select (s) {
  isNew.value = false
  selected.value = s
  form.value = { ...s }
  tab.value = 'basic'
  stats.value = await Master.systemStats(s.systemCd).catch(() => ({}))
  attrs.value = await Master.attrs(s.systemCd).catch(() => [])
  // history
  try {
    const h = await Audit.changes({ system_cd: s.systemCd, size: 50 })
    history.value = h.content.filter(r => ['SYSTEM_CHANGE','SHARD_STRATEGY_CHANGE','MENU_TREE_CHANGE','MENU_ACTION_CHANGE','MENU_ACTION_API_CHANGE'].includes(r.action))
  } catch { history.value = [] }
}

function cancel () {
  isNew.value = false
  if (systems.value.length) select(systems.value[0])
  else { selected.value = null; form.value = {} }
}

async function save () {
  if (!form.value.systemCd) return ElMessage.error('시스템 코드 필수')
  try {
    if (isNew.value) await Master.saveSystem(form.value)
    else await Master.updateSystem(form.value.systemCd, form.value)
    ElMessage.success('저장됨')
    isNew.value = false
    await load()
    const fresh = systems.value.find(x => x.systemCd === form.value.systemCd)
    if (fresh) select(fresh)
  } catch (e) { ElMessage.error(e.response?.data?.message || e.message) }
}

async function del () {
  await ElMessageBox.confirm(`${form.value.systemCd} 시스템을 삭제하시겠어요? 메타와 샤딩 컨피그가 같이 삭제됩니다.`, '확인', { type: 'warning' })
  await Master.deleteSystem(form.value.systemCd)
  ElMessage.success('삭제됨')
  await load()
  selected.value = null
  form.value = {}
}

async function addAttr () {
  if (!newAttr.value.key) return
  await Master.setAttr(form.value.systemCd, newAttr.value.key, newAttr.value.value)
  attrs.value = await Master.attrs(form.value.systemCd)
  newAttr.value = { key: '', value: '' }
}

async function delAttr (key) {
  await Master.deleteAttr(form.value.systemCd, key)
  attrs.value = await Master.attrs(form.value.systemCd)
}

watch(() => app.systemCd, async (cd) => {
  const s = systems.value.find(x => x.systemCd === cd)
  if (s) await select(s)
})
</script>

<style scoped>
.sys-list { max-height: 540px; overflow: auto; }
.sys-item { padding: 8px 6px; border-radius: 4px; cursor: pointer; border-bottom: 1px solid #eef0f2; }
.sys-item:hover { background: #f5f7fa; }
.sys-item.active { background: #e6f0ff; border-left: 3px solid #1677ff; }
.sys-item .row1 { display: flex; align-items: center; gap: 6px; }
.sys-item .nm { font-weight: 600; }
.sys-item .row2 { color: #6b7280; font-size: 12px; padding-left: 4px; margin-top: 4px; }
.sys-item .api-cnt { color: #1677ff; }
.cd { color: #6b7280; margin-left: 6px; font-weight: normal; }
.stats-bar { margin-bottom: 14px; }
.hint { color: #94a3b8; font-size: 11px; padding-top: 4px; }
</style>
