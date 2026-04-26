<template>
  <el-card>
    <template #header>
      <div style="display:flex; align-items:center; justify-content:space-between;">
        <b>API 마스터 (시스템: {{ app.systemCd }})</b>
        <el-button type="primary" size="small" @click="add()"><el-icon><Plus /></el-icon>API 등록</el-button>
      </div>
    </template>

    <!-- 상태 통계 바 -->
    <div class="stats-bar">
      <div class="stat"><div class="lbl">전체 API</div><div class="val">{{ rows.length }}</div></div>
      <div class="stat"><div class="lbl">매핑완료</div><div class="val ok">{{ mappedCount }}</div></div>
      <div class="stat"><div class="lbl">미매핑</div><div class="val warn">{{ rows.length - mappedCount }}</div></div>
      <div class="stat"><div class="lbl">사용중단</div><div class="val">{{ rows.filter(r => r.status === 'D').length }}</div></div>
    </div>

    <el-form inline style="margin-top:8px;">
      <el-form-item><el-input v-model="filter" placeholder="URL / Method / 서비스명 검색" clearable style="width:300px" /></el-form-item>
      <el-form-item><el-select v-model="methodFilter" placeholder="(전체 Method)" clearable style="width:140px">
        <el-option v-for="m in ['GET','POST','PUT','DELETE','PATCH']" :key="m" :label="m" :value="m" />
      </el-select></el-form-item>
      <el-form-item><el-checkbox v-model="unmappedOnly">미매핑만 보기</el-checkbox></el-form-item>
    </el-form>

    <el-table :data="filtered" stripe size="small" max-height="600">
      <el-table-column prop="apiId" label="ID" width="80" />
      <el-table-column prop="httpMethod" label="Method" width="80">
        <template #default="{ row }"><el-tag :type="methodColor(row.httpMethod)" size="small">{{ row.httpMethod }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="urlPattern" label="URL Pattern" />
      <el-table-column prop="urlDepth" label="depth" width="70" />
      <el-table-column prop="shardSeg" label="shard_seg" width="120" />
      <el-table-column prop="serviceNm" label="서비스" width="140" />
      <el-table-column label="매핑" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="mappedSet.has(row.apiId) ? 'success' : 'warning'">
            {{ mappedSet.has(row.apiId) ? '매핑됨' : '미매핑' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="상태" width="80" />
      <el-table-column label="관리" width="160">
        <template #default="{ row }">
          <el-button size="small" link @click="edit(row)">수정</el-button>
          <el-button size="small" link type="danger" @click="del(row)">삭제</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showDlg" :title="form.apiId ? `API 수정 #${form.apiId}` : 'API 등록'" width="640px">
      <el-form label-width="120px">
        <el-form-item label="HTTP Method">
          <el-select v-model="form.httpMethod">
            <el-option v-for="m in ['GET','POST','PUT','DELETE','PATCH']" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="URL Pattern"><el-input v-model="form.urlPattern" placeholder="/api/purchase/items/{id}" /></el-form-item>
        <el-form-item label="서비스명"><el-input v-model="form.serviceNm" /></el-form-item>
        <el-form-item label="설명"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="상태">
          <el-radio-group v-model="form.status">
            <el-radio-button value="A">활성</el-radio-button>
            <el-radio-button value="D">사용중단</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-alert v-if="form.urlPattern" type="info" :closable="false">
          ▸ url_depth = {{ depth(form.urlPattern) }}, shard_seg는 시스템 컨피그에 따라 자동 결정됩니다.
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="showDlg=false">취소</el-button>
        <el-button type="primary" @click="save">저장</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { Master } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAppStore } from '@/store'

const app = useAppStore()
const rows = ref([])
const filter = ref('')
const methodFilter = ref('')
const unmappedOnly = ref(false)
const showDlg = ref(false)
const form = reactive({})
const mappedSet = ref(new Set())

onMounted(load)
watch(() => app.systemCd, load)

const filtered = computed(() => {
  const f = filter.value.toLowerCase()
  return rows.value.filter(r =>
    (!methodFilter.value || r.httpMethod === methodFilter.value)
    && (!unmappedOnly.value || !mappedSet.value.has(r.apiId))
    && (!f || (r.urlPattern + r.httpMethod + (r.serviceNm || '')).toLowerCase().includes(f))
  )
})
const mappedCount = computed(() => mappedSet.value.size)

async function load () {
  rows.value = await Master.apis(app.systemCd)
  // build mapped set from /menus/{}/mappings is heavy; use stats endpoint on backend if needed
  // Here we approximate by querying /apis/{}/usages lazily — instead, rely on system stats
  const stats = await Master.systemStats(app.systemCd).catch(() => ({}))
  // not perfect — but mark all that have any mapping by api_mapped count + per-row check on demand
  // do per-row usage fetch only when needed for accurate mapping (skip for perf)
  // simple: fetch mapping per-row in batch
  const set = new Set()
  await Promise.all(rows.value.map(async r => {
    try {
      const u = await Master.apiUsages(r.apiId)
      if (u && u.length) set.add(r.apiId)
    } catch {}
  }))
  mappedSet.value = set
}

function add () { Object.keys(form).forEach(k => delete form[k]); form.systemCd = app.systemCd; form.httpMethod = 'GET'; form.status = 'A'; showDlg.value = true }
function edit (r) { Object.assign(form, r); showDlg.value = true }
async function save () {
  if (!form.urlPattern) return ElMessage.error('URL 필수')
  if (form.apiId) await Master.updateApi(form.apiId, form)
  else await Master.saveApi(form)
  ElMessage.success('저장됨')
  showDlg.value = false
  await load()
}
async function del (row) {
  await ElMessageBox.confirm(`API #${row.apiId} ${row.httpMethod} ${row.urlPattern} 를 삭제할까요? 메뉴-액션 매핑도 함께 삭제됩니다.`, '확인', { type:'warning' })
  await Master.deleteApi(row.apiId)
  ElMessage.success('삭제됨')
  await load()
}
function methodColor (m) {
  return ({ GET:'success', POST:'warning', PUT:'primary', PATCH:'primary', DELETE:'danger' })[m] || 'info'
}
function depth (url) {
  if (!url) return 0
  return url.split('?')[0].split('/').filter(Boolean).length
}
</script>

<style scoped>
.stats-bar { margin-bottom: 10px; }
</style>
