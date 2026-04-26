<template>
  <el-row :gutter="14">
    <!-- 좌측 메뉴 트리 -->
    <el-col :span="8">
      <el-card>
        <template #header>
          <div style="display:flex; align-items:center; justify-content:space-between;">
            <b>메뉴 트리</b>
            <div>
              <el-button size="small" type="primary" @click="newDlg.open(null)"><el-icon><Plus /></el-icon>신규</el-button>
            </div>
          </div>
        </template>
        <el-input v-model="filterText" placeholder="메뉴명 / 코드 검색" size="small" clearable />
        <div class="legend">
          <span>📁 Folder</span><span>📄 Menu</span><span>🔗 Link</span>
        </div>
        <el-tree
          ref="treeRef" :data="tree"
          node-key="menu_id" :expand-on-click-node="false"
          :filter-node-method="filterNode"
          :props="{ label: 'menu_nm', children: 'children' }"
          highlight-current
          @node-click="select">
          <template #default="{ data }">
            <span class="node">
              <span class="ic">{{ icon(data.menu_type) }}</span>
              <span>{{ data.menu_nm }}</span>
              <el-tag size="small" effect="plain" style="margin-left:4px;">{{ data.menu_type }}</el-tag>
              <el-tag v-if="data.is_visible !== 'Y'" size="small" type="info" style="margin-left:4px;">숨김</el-tag>
              <span class="actions">
                <el-button v-if="data.menu_type === 'F'" link size="small" @click.stop="newDlg.open(data)">+</el-button>
              </span>
            </span>
          </template>
        </el-tree>
      </el-card>
    </el-col>

    <!-- 우측 상세 -->
    <el-col :span="16">
      <el-card v-if="selected">
        <template #header>
          <div style="display:flex; align-items:center; justify-content:space-between;">
            <div>
              <span class="menu-icon">{{ icon(selected.menu_type) }}</span>
              <b>{{ selected.menu_nm }}</b>
              <el-tag size="small" effect="plain" style="margin-left:8px;">{{ selected.menu_type }}</el-tag>
              <span class="path">menu_id={{ selected.menu_id }} · {{ selected.menu_cd || '(코드 없음)' }}</span>
            </div>
            <div>
              <el-button type="danger" size="small" @click="delMenu">삭제</el-button>
              <el-button type="primary" size="small" @click="saveMenu">저장</el-button>
            </div>
          </div>
        </template>

        <el-tabs v-model="tab">
          <!-- 기본정보 -->
          <el-tab-pane label="기본정보" name="basic">
            <el-form label-width="120px" style="max-width:580px;">
              <el-form-item label="메뉴 ID"><el-input :model-value="form.menuId" disabled /></el-form-item>
              <el-form-item label="유형">
                <el-radio-group v-model="form.menuType" disabled>
                  <el-radio-button value="F">Folder</el-radio-button>
                  <el-radio-button value="M">Menu</el-radio-button>
                  <el-radio-button value="L">Link</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="메뉴 코드"><el-input v-model="form.menuCd" /></el-form-item>
              <el-form-item label="메뉴명 (한글)"><el-input v-model="form.menuNm" /></el-form-item>
              <el-form-item label="메뉴명 (영문)"><el-input v-model="form.menuNmEn" /></el-form-item>
              <el-form-item label="설명"><el-input v-model="form.menuDesc" type="textarea" :rows="2" /></el-form-item>
              <el-form-item label="아이콘"><el-input v-model="form.icon" placeholder="folder / file / external-link …" /></el-form-item>
              <el-form-item label="정렬 순서"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
              <el-form-item label="표시 여부">
                <el-switch v-model="form.isVisible" active-value="Y" inactive-value="N" />
              </el-form-item>
              <el-form-item label="기본 메뉴">
                <el-switch v-model="form.isDefault" active-value="Y" inactive-value="N" />
              </el-form-item>
              <el-form-item label="상태">
                <el-radio-group v-model="form.status">
                  <el-radio-button value="A">활성</el-radio-button>
                  <el-radio-button value="I">비활성</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="유효기간">
                <el-date-picker v-model="form.effectiveFrom" value-format="YYYY-MM-DD" type="date" placeholder="시작" />
                ~
                <el-date-picker v-model="form.effectiveTo" value-format="YYYY-MM-DD" type="date" placeholder="종료" />
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <!-- 구현체 메타 -->
          <el-tab-pane label="구현체 메타" name="impl" :disabled="form.menuType === 'F'">
            <div v-if="form.menuType === 'F'" class="hint">폴더 유형은 구현체 메타가 없습니다.</div>
            <el-form v-else label-width="140px" style="max-width:660px;">
              <el-divider content-position="left">Vue Router 정보</el-divider>
              <el-form-item label="Route Path"><el-input v-model="impl.routePath" /></el-form-item>
              <el-form-item label="Route Name"><el-input v-model="impl.routeName" /></el-form-item>
              <el-form-item label="Component Path"><el-input v-model="impl.componentPath" /></el-form-item>
              <el-form-item label="Component Name"><el-input v-model="impl.componentName" /></el-form-item>

              <el-divider content-position="left">Route Meta (JSON)</el-divider>
              <el-form-item label="Route Meta">
                <el-input v-model="impl._routeMetaText" type="textarea" :rows="4" placeholder='{"requiresAuth": true, "title": "..."}' />
              </el-form-item>
              <el-form-item label="Route Params">
                <el-input v-model="impl._routeParamsText" type="textarea" :rows="2" placeholder='{"id": "string"}' />
              </el-form-item>
              <el-form-item label="Route Query">
                <el-input v-model="impl._routeQueryText" type="textarea" :rows="2" placeholder='{"tab": "string"}' />
              </el-form-item>

              <el-divider content-position="left">화면 옵션</el-divider>
              <el-form-item label="레이아웃 사용">
                <el-switch v-model="impl.hasLayout" active-value="Y" inactive-value="N" />
              </el-form-item>
              <el-form-item label="Full Screen">
                <el-switch v-model="impl.isFullScreen" active-value="Y" inactive-value="N" />
              </el-form-item>
              <el-form-item label="모달">
                <el-switch v-model="impl.isModal" active-value="Y" inactive-value="N" />
              </el-form-item>
              <el-form-item label="모바일 지원">
                <el-switch v-model="impl.mobileSupported" active-value="Y" inactive-value="N" />
              </el-form-item>
              <el-form-item label="모바일 라우트"><el-input v-model="impl.mobileRoutePath" /></el-form-item>

              <el-divider v-if="form.menuType==='L'" content-position="left">외부 링크</el-divider>
              <el-form-item v-if="form.menuType==='L'" label="External URL"><el-input v-model="impl.externalUrl" /></el-form-item>
              <el-form-item v-if="form.menuType==='L'" label="Open Target">
                <el-radio-group v-model="impl.openTarget">
                  <el-radio-button value="_self">_self</el-radio-button>
                  <el-radio-button value="_blank">_blank</el-radio-button>
                </el-radio-group>
              </el-form-item>

              <el-form-item>
                <el-button type="primary" @click="saveImpl">구현체 메타 저장</el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <!-- 액션-API 매핑 -->
          <el-tab-pane label="액션-API 매핑" name="mapping" :disabled="form.menuType !== 'M'">
            <div v-if="form.menuType !== 'M'" class="hint">리프 메뉴(Menu)에서만 액션-API 매핑이 가능합니다.</div>
            <div v-else>
              <div class="actions-toggle">
                <span class="lbl">활성 액션:</span>
                <el-checkbox v-for="a in actionDefs" :key="a.actionCd" :model-value="enabledActions.includes(a.actionCd)"
                             @change="(v) => toggleAction(a.actionCd, v)">
                  <b>{{ a.actionCd }}</b> {{ a.actionNm }}
                </el-checkbox>
              </div>

              <el-divider />

              <div v-for="a in actionDefs" :key="a.actionCd" v-show="enabledActions.includes(a.actionCd)" class="action-block">
                <div class="action-head">
                  <b>{{ a.actionCd }} - {{ a.actionNm }}</b>
                  <el-tag v-if="!(mappings[a.actionCd] || []).length" type="warning" size="small">⚠ 매핑 0건</el-tag>
                  <el-tag v-else type="success" size="small">{{ (mappings[a.actionCd] || []).length }}건 매핑</el-tag>
                  <el-button size="small" type="primary" link @click="mapDlg.open(a.actionCd)">+ API 추가</el-button>
                </div>
                <el-table :data="mappings[a.actionCd] || []" size="small">
                  <el-table-column prop="http_method" label="Method" width="90">
                    <template #default="{ row }">
                      <el-tag :type="methodColor(row.http_method)" size="small">{{ row.http_method }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="url_pattern" label="URL Pattern" />
                  <el-table-column prop="service_nm" label="서비스" width="140" />
                  <el-table-column label="" width="80">
                    <template #default="{ row }">
                      <el-button size="small" link type="danger" @click="unmap(a.actionCd, row.api_id)">삭제</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </div>
          </el-tab-pane>

          <!-- 변경이력 -->
          <el-tab-pane label="변경이력" name="history">
            <el-table :data="history" size="small">
              <el-table-column prop="occurredAt" label="시각" width="180" />
              <el-table-column prop="action" label="작업" width="180" />
              <el-table-column label="상세">
                <template #default="{ row }">
                  <pre style="font-size:11px; margin:0">{{ JSON.stringify(row.detail) }}</pre>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </el-card>
      <el-empty v-else description="좌측에서 메뉴를 선택하세요" />
    </el-col>
  </el-row>

  <!-- 신규 메뉴 다이얼로그 -->
  <el-dialog v-model="newDlg.visible" title="신규 메뉴 추가" width="640px" @close="newDlg.reset()">
    <div v-if="newDlg.parent" class="bread">부모 메뉴: {{ newDlg.parent.menu_nm }}</div>
    <el-divider content-position="left">메뉴 유형 선택</el-divider>
    <div class="type-cards">
      <div v-for="t in [{cd:'F',ic:'📁',nm:'Folder',desc:'하위 메뉴를 묶는 중간 노드'},{cd:'M',ic:'📄',nm:'Menu',desc:'실제 화면(리프), 액션-API 매핑 가능'},{cd:'L',ic:'🔗',nm:'Link',desc:'외부 링크 (다른 시스템 등)'}]"
           :key="t.cd" class="type-card" :class="{active: newDlg.form.menuType===t.cd}"
           @click="newDlg.form.menuType = t.cd">
        <div class="ic">{{ t.ic }}</div>
        <div class="nm">{{ t.nm }}</div>
        <div class="desc">{{ t.desc }}</div>
      </div>
    </div>
    <el-divider content-position="left">기본 정보</el-divider>
    <el-form label-width="120px">
      <el-form-item label="메뉴명 (한글)"><el-input v-model="newDlg.form.menuNm" /></el-form-item>
      <el-form-item label="메뉴명 (영문)"><el-input v-model="newDlg.form.menuNmEn" /></el-form-item>
      <el-form-item label="메뉴 코드"><el-input v-model="newDlg.form.menuCd" /></el-form-item>
      <el-form-item label="아이콘"><el-input v-model="newDlg.form.icon" /></el-form-item>
      <el-form-item label="정렬 순서"><el-input-number v-model="newDlg.form.sortOrder" :min="0" /></el-form-item>
    </el-form>
    <template v-if="newDlg.form.menuType === 'M'">
      <el-divider content-position="left">구현체 메타 (Menu)</el-divider>
      <el-form label-width="120px">
        <el-form-item label="Route Path"><el-input v-model="newDlg.form._routePath" /></el-form-item>
        <el-form-item label="Route Name"><el-input v-model="newDlg.form._routeName" /></el-form-item>
        <el-form-item label="Component Path"><el-input v-model="newDlg.form._componentPath" /></el-form-item>
      </el-form>
    </template>
    <template v-if="newDlg.form.menuType === 'L'">
      <el-divider content-position="left">외부 링크 (Link)</el-divider>
      <el-form label-width="120px">
        <el-form-item label="External URL"><el-input v-model="newDlg.form._externalUrl" /></el-form-item>
        <el-form-item label="Open Target">
          <el-radio-group v-model="newDlg.form._openTarget">
            <el-radio-button value="_self">_self</el-radio-button>
            <el-radio-button value="_blank">_blank</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
    </template>
    <template #footer>
      <el-button @click="newDlg.visible=false">취소</el-button>
      <el-button type="primary" @click="newDlg.create()">메뉴 생성</el-button>
    </template>
  </el-dialog>

  <!-- API 매핑 다이얼로그 -->
  <el-dialog v-model="mapDlg.visible" title="API 매핑 추가" width="900px">
    <div class="bread">매핑 대상: 메뉴 <b>{{ selected?.menu_nm }}</b> · 액션 <b>{{ mapDlg.actionCd }}</b></div>
    <el-form inline style="margin-top:8px;">
      <el-form-item label="검색 prefix">
        <el-input v-model="mapDlg.prefix" placeholder="/api/purchase" @change="mapDlg.load()" style="width:240px" />
      </el-form-item>
      <el-form-item label="Method 필터">
        <el-select v-model="mapDlg.methodFilter" placeholder="(전체)" clearable style="width:120px" @change="mapDlg.load()">
          <el-option v-for="m in ['GET','POST','PUT','DELETE','PATCH']" :key="m" :label="m" :value="m" />
        </el-select>
      </el-form-item>
      <el-form-item><el-checkbox v-model="mapDlg.unmappedOnly" @change="mapDlg.load()">미매핑 API만</el-checkbox></el-form-item>
    </el-form>
    <el-alert type="warning" :closable="false">자동 추천: 메뉴 URL과 prefix 일치하는 API는 노란색으로 표시됩니다.</el-alert>
    <el-table :data="mapDlg.filteredCandidates" size="small" height="380" @selection-change="mapDlg.onSel">
      <el-table-column type="selection" width="40" />
      <el-table-column prop="http_method" label="Method" width="80">
        <template #default="{ row }"><el-tag :type="methodColor(row.http_method)" size="small">{{ row.http_method }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="url_pattern" label="URL Pattern">
        <template #default="{ row }">
          <span :class="{ recommended: row.recommended }">{{ row.url_pattern }}</span>
          <el-tag v-if="row.recommended" type="warning" size="small" style="margin-left:6px;">추천</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="service_nm" label="서비스" width="120" />
      <el-table-column label="상태" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.already_mapped" size="small">매핑됨</el-tag>
          <el-tag v-else size="small" type="success">미매핑</el-tag>
        </template>
      </el-table-column>
    </el-table>
    <div class="meta">선택된 API: {{ mapDlg.picked.length }}개</div>
    <template #footer>
      <el-button @click="mapDlg.visible=false">취소</el-button>
      <el-button type="primary" @click="mapDlg.apply()" :disabled="!mapDlg.picked.length">매핑 적용</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { Master, Audit } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAppStore } from '@/store'
const app = useAppStore()

const filterText = ref('')
const treeRef = ref(null)
const tab = ref('basic')
const tree = ref([])
const flatMenus = ref([])
const selected = ref(null)
const form = ref({})
const impl = ref({})
const enabledActions = ref([])
const actionDefs = ref([])
const mappings = ref({})
const history = ref([])

function icon (t) { return t === 'F' ? '📁' : t === 'L' ? '🔗' : '📄' }
function methodColor (m) {
  return ({ GET:'success', POST:'warning', PUT:'primary', PATCH:'primary', DELETE:'danger' })[m] || 'info'
}

watch(() => app.systemCd, () => loadAll())
onMounted(() => loadAll())
watch(filterText, v => treeRef.value && treeRef.value.filter(v))
function filterNode (value, data) {
  if (!value) return true
  return ((data.menu_nm || '') + (data.menu_cd || '')).toLowerCase().includes(value.toLowerCase())
}

async function loadAll () {
  flatMenus.value = await Master.menus(app.systemCd)
  actionDefs.value = await Master.actions(app.systemCd)
  buildTree()
  selected.value = null
}
function buildTree () {
  const map = new Map()
  flatMenus.value.forEach(m => map.set(m.menuId, {
    menu_id: m.menuId, menu_cd: m.menuCd, menu_nm: m.menuNm, menu_type: m.menuType,
    is_visible: m.isVisible, parent_menu_id: m.parentMenuId, raw: m, children: []
  }))
  const roots = []
  flatMenus.value.forEach(m => {
    const node = map.get(m.menuId)
    if (m.parentMenuId && map.get(m.parentMenuId)) map.get(m.parentMenuId).children.push(node)
    else roots.push(node)
  })
  tree.value = roots
}

async function select (n) {
  selected.value = n
  form.value = { ...n.raw }
  tab.value = 'basic'
  // load impl
  const i = await Master.getMenuImpl(n.menu_id).catch(() => null)
  impl.value = i ? {
    ...i,
    _routeMetaText: i.routeMeta ? JSON.stringify(i.routeMeta, null, 2) : '',
    _routeParamsText: i.routeParams ? JSON.stringify(i.routeParams, null, 2) : '',
    _routeQueryText: i.routeQuery ? JSON.stringify(i.routeQuery, null, 2) : ''
  } : { menuId: n.menu_id, hasLayout: 'Y', isFullScreen: 'N', isModal: 'N', mobileSupported: 'Y', _routeMetaText: '', _routeParamsText: '', _routeQueryText: '' }
  // load mappings + actions
  const acts = await Master.menuActions(n.menu_id).catch(() => [])
  enabledActions.value = acts.map(a => a.actionCd)
  const m = await Master.menuMappings(n.menu_id).catch(() => ({ by_action: {} }))
  mappings.value = m.by_action || {}
  // history
  try {
    const h = await Audit.changes({ system_cd: app.systemCd, size: 30 })
    history.value = h.content.filter(r => (r.detail || {}).menu_id == n.menu_id)
  } catch { history.value = [] }
}

async function saveMenu () {
  const saved = await Master.updateMenu(form.value.menuId, form.value)
  ElMessage.success('메뉴 저장됨')
  await loadAll()
  await nextTick()
  const fresh = flatMenus.value.find(x => x.menuId === saved.menuId)
  if (fresh) {
    const node = findNode(tree.value, fresh.menuId)
    if (node) await select(node)
  }
}

async function delMenu () {
  await ElMessageBox.confirm('메뉴와 그에 부여된 모든 권한·매핑이 삭제됩니다. 진행하시겠어요?', '삭제 확인', { type: 'warning' })
  await Master.deleteMenu(form.value.menuId)
  ElMessage.success('삭제됨')
  await loadAll()
}

async function saveImpl () {
  try {
    const body = {
      ...impl.value,
      routeMeta: tryJson(impl.value._routeMetaText),
      routeParams: tryJson(impl.value._routeParamsText),
      routeQuery: tryJson(impl.value._routeQueryText)
    }
    delete body._routeMetaText; delete body._routeParamsText; delete body._routeQueryText
    await Master.saveMenuImpl(form.value.menuId, body)
    ElMessage.success('구현체 메타 저장됨')
  } catch (e) { ElMessage.error('JSON 형식 확인: ' + e.message) }
}

async function toggleAction (cd, v) {
  if (v) await Master.enableMenuAction(form.value.menuId, cd)
  else await Master.disableMenuAction(form.value.menuId, cd)
  enabledActions.value = (await Master.menuActions(form.value.menuId)).map(a => a.actionCd)
  const m = await Master.menuMappings(form.value.menuId)
  mappings.value = m.by_action || {}
}

async function unmap (action, apiId) {
  await Master.unmapMenuApi(form.value.menuId, action, apiId)
  const m = await Master.menuMappings(form.value.menuId)
  mappings.value = m.by_action || {}
}

function tryJson (s) {
  if (!s || !s.trim()) return null
  return JSON.parse(s)
}

function findNode (nodes, id) {
  for (const n of nodes) {
    if (n.menu_id === id) return n
    if (n.children) {
      const found = findNode(n.children, id)
      if (found) return found
    }
  }
  return null
}

// 신규 메뉴 다이얼로그
const newDlg = reactive({
  visible: false,
  parent: null,
  form: { menuType: 'M', sortOrder: 0 },
  open (parent) { this.parent = parent; this.form = { menuType: 'M', sortOrder: 0 }; this.visible = true },
  reset () { this.parent = null; this.form = { menuType: 'M', sortOrder: 0 } },
  async create () {
    if (!this.form.menuNm) return ElMessage.error('메뉴명 필수')
    const body = {
      systemCd: app.systemCd,
      parentMenuId: this.parent?.menu_id || null,
      menuType: this.form.menuType,
      menuCd: this.form.menuCd,
      menuNm: this.form.menuNm,
      menuNmEn: this.form.menuNmEn,
      icon: this.form.icon,
      sortOrder: this.form.sortOrder,
      isVisible: 'Y', isDefault: 'N', status: 'A'
    }
    const saved = await Master.saveMenu(body)
    if (this.form.menuType !== 'F') {
      const implBody = {
        menuId: saved.menuId,
        routePath: this.form._routePath,
        routeName: this.form._routeName,
        componentPath: this.form._componentPath,
        externalUrl: this.form._externalUrl,
        openTarget: this.form._openTarget || '_self',
        hasLayout: 'Y', mobileSupported: 'Y'
      }
      await Master.saveMenuImpl(saved.menuId, implBody)
    }
    ElMessage.success('메뉴 생성됨')
    this.visible = false
    await loadAll()
  }
})

// API 매핑 다이얼로그
const mapDlg = reactive({
  visible: false,
  actionCd: 'R',
  prefix: '',
  methodFilter: '',
  unmappedOnly: false,
  candidates: [],
  picked: [],
  open (actionCd) {
    this.actionCd = actionCd
    this.methodFilter = methodForAction(actionCd)
    this.prefix = ''
    this.unmappedOnly = false
    this.picked = []
    this.visible = true
    this.load()
  },
  async load () {
    this.candidates = await Master.recommendApis(form.value.menuId, {
      action_cd: this.actionCd,
      system_cd: app.systemCd,
      unmapped_only: this.unmappedOnly,
      method: this.methodFilter || undefined
    })
  },
  get filteredCandidates () {
    return this.prefix
      ? this.candidates.filter(c => (c.url_pattern || '').includes(this.prefix))
      : this.candidates
  },
  onSel (rows) { this.picked = rows.filter(r => !r.already_mapped) },
  async apply () {
    for (const r of this.picked) {
      await Master.mapMenuApi(form.value.menuId, this.actionCd, r.api_id)
    }
    ElMessage.success(`${this.picked.length}건 매핑`)
    this.visible = false
    const m = await Master.menuMappings(form.value.menuId)
    mappings.value = m.by_action || {}
  }
})
function methodForAction (a) {
  return ({ R: 'GET', C: 'POST', U: 'PUT', D: 'DELETE' })[a] || ''
}
</script>

<style scoped>
.legend { padding: 6px 0; color: #6b7280; font-size: 12px; display:flex; gap:10px; }
.menu-icon { font-size: 18px; margin-right: 4px; }
.path { color:#94a3b8; font-size: 12px; margin-left: 8px; }
.node { display: inline-flex; align-items: center; gap:4px; }
.ic { font-size: 14px; }
.actions { margin-left: 6px; }
.bread { background: #f4f6f8; padding: 8px 10px; border-radius: 4px; }
.type-cards { display: flex; gap: 12px; padding: 8px 0; }
.type-card { flex:1; text-align:center; padding:14px; border:2px solid #e5e7eb; border-radius:8px; cursor:pointer; }
.type-card:hover { border-color: #93c5fd; }
.type-card.active { border-color: #1677ff; background: #eff6ff; }
.type-card .ic { font-size: 24px; }
.type-card .nm { font-weight: 700; margin-top: 6px; }
.type-card .desc { color:#6b7280; font-size: 12px; margin-top: 4px; }
.actions-toggle { background: #f8fafc; padding: 8px 12px; border-radius: 4px; display:flex; align-items:center; gap:14px; flex-wrap: wrap; }
.actions-toggle .lbl { color:#6b7280; }
.action-block { margin-bottom: 16px; }
.action-head { display:flex; align-items:center; gap:8px; margin-bottom:6px; }
.recommended { background: #fef9c3; padding: 0 4px; border-radius: 2px; }
.hint { padding: 30px; color:#94a3b8; text-align: center; }
.meta { padding: 6px 0; color: #6b7280; font-size: 12px; }
</style>
