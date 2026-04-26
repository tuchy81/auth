<template>
  <div>
    <el-card>
      <template #header>
        <div style="display:flex; align-items:center; gap:10px;">
          <el-icon><Monitor /></el-icon>
          <b>전체 서비스 헬스 대시보드</b>
          <el-tag size="small" effect="plain">{{ data?.timestamp ? data.timestamp.slice(0,19).replace('T',' ') : '-' }}</el-tag>
          <div style="margin-left:auto; display:flex; gap:8px; align-items:center;">
            <el-switch v-model="autoRefresh" active-text="5초마다 자동 갱신" />
            <el-button :loading="loading" @click="load" type="primary"><el-icon><Refresh /></el-icon>새로고침</el-button>
          </div>
        </div>
      </template>

      <el-empty v-if="!data && !loading" description="새로고침 버튼을 누르세요" />
      <el-skeleton v-else-if="loading && !data" :rows="6" animated />

      <div v-if="data">
        <!-- 1) 외부 서비스 상태 -->
        <h3 class="sec">🩺 서비스 상태</h3>
        <el-row :gutter="14">
          <el-col v-for="s in data.services.list" :key="s.name" :span="6">
            <el-card shadow="never" :class="['svc', s.status === 'UP' ? 'up' : 'down']">
              <div class="svc-head">
                <el-tag :type="s.status === 'UP' ? 'success' : 'danger'" effect="dark">{{ s.status }}</el-tag>
                <b>{{ s.name }}</b>
              </div>
              <div class="svc-meta">
                <div>HTTP {{ s.http_code }} · {{ s.latency_ms }}ms</div>
                <div class="dim">{{ s.url }}</div>
                <div v-if="s.error" class="err">{{ s.error }}</div>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 2) 핵심 KPI 4개 -->
        <h3 class="sec">📊 핵심 지표</h3>
        <el-row :gutter="14">
          <el-col :span="6"><el-card shadow="never">
            <el-statistic :value="data.cache_l1.hit_ratio_pct" :precision="2" title="L1 (Caffeine) Hit Ratio" :value-style="{ color: hitColor(data.cache_l1.hit_ratio_pct) }">
              <template #suffix>%</template>
            </el-statistic>
            <div class="sub">{{ data.cache_l1.hit_count.toLocaleString() }} hits / {{ data.cache_l1.miss_count.toLocaleString() }} miss</div>
          </el-card></el-col>
          <el-col :span="6"><el-card shadow="never">
            <el-statistic :value="data.redis.hit_ratio_pct ?? 0" :precision="2" title="L2 (Redis) Hit Ratio" :value-style="{ color: hitColor(data.redis.hit_ratio_pct) }">
              <template #suffix>%</template>
            </el-statistic>
            <div class="sub">{{ (data.redis.keyspace_hits || 0).toLocaleString() }} hits / {{ (data.redis.keyspace_misses || 0).toLocaleString() }} miss</div>
          </el-card></el-col>
          <el-col :span="6"><el-card shadow="never">
            <el-statistic :value="data.sync_worker.unprocessed_events" title="Sync Worker Lag" :value-style="{ color: data.sync_worker.unprocessed_events > 100 ? '#ef4444' : '#16a34a' }">
              <template #suffix>건</template>
            </el-statistic>
            <div class="sub">{{ data.sync_worker.alert }}</div>
          </el-card></el-col>
          <el-col :span="6"><el-card shadow="never">
            <el-statistic :value="data.redis.perm_keys ?? 0" title="Redis 권한 키" />
            <div class="sub">메모리 {{ data.redis.used_memory_mb ?? '-' }} MB</div>
          </el-card></el-col>
        </el-row>

        <!-- 3) DB / Cache / Redis 상세 -->
        <el-row :gutter="14" style="margin-top:14px;">
          <el-col :span="8">
            <el-card shadow="never">
              <template #header><b>🗄 PostgreSQL</b></template>
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="상태"><el-tag :type="data.database.status === 'UP' ? 'success' : 'danger'">{{ data.database.status }}</el-tag></el-descriptions-item>
                <el-descriptions-item label="TB_SYSTEM">{{ data.database.tb_system }}</el-descriptions-item>
                <el-descriptions-item label="TB_API">{{ data.database.tb_api.toLocaleString() }}</el-descriptions-item>
                <el-descriptions-item label="TB_MENU">{{ data.database.tb_menu }}</el-descriptions-item>
                <el-descriptions-item label="TB_USER">{{ data.database.tb_user }}</el-descriptions-item>
                <el-descriptions-item label="TB_PERMISSION">{{ data.database.tb_permission.toLocaleString() }}</el-descriptions-item>
                <el-descriptions-item label="TB_MENU_ACTION_API">{{ data.database.tb_menu_action_api.toLocaleString() }}</el-descriptions-item>
                <el-descriptions-item label="TB_PERM_CHANGE_LOG">{{ data.database.tb_change_log.toLocaleString() }}</el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never">
              <template #header><b>⚡ Caffeine L1</b></template>
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="현재 entry">{{ data.cache_l1.estimated_size.toLocaleString() }}</el-descriptions-item>
                <el-descriptions-item label="hit_count">{{ data.cache_l1.hit_count.toLocaleString() }}</el-descriptions-item>
                <el-descriptions-item label="miss_count">{{ data.cache_l1.miss_count.toLocaleString() }}</el-descriptions-item>
                <el-descriptions-item label="hit_ratio">
                  <b :style="{color: hitColor(data.cache_l1.hit_ratio_pct)}">{{ data.cache_l1.hit_ratio_pct }}%</b>
                  <span class="dim"> (목표 ≥ 80%)</span>
                </el-descriptions-item>
                <el-descriptions-item label="evictions">{{ data.cache_l1.eviction_count.toLocaleString() }}</el-descriptions-item>
                <el-descriptions-item label="load_failures">{{ data.cache_l1.load_failures.toLocaleString() }}</el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never">
              <template #header><b>🟥 Redis L2</b></template>
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="상태"><el-tag :type="data.redis.status === 'UP' ? 'success' : 'danger'">{{ data.redis.status }}</el-tag></el-descriptions-item>
                <el-descriptions-item label="권한 키 (perm:*)">{{ (data.redis.perm_keys || 0).toLocaleString() }}</el-descriptions-item>
                <el-descriptions-item label="warm-up 키">{{ data.redis.warmup_keys || 0 }}</el-descriptions-item>
                <el-descriptions-item label="keyspace hits">{{ (data.redis.keyspace_hits || 0).toLocaleString() }}</el-descriptions-item>
                <el-descriptions-item label="keyspace misses">{{ (data.redis.keyspace_misses || 0).toLocaleString() }}</el-descriptions-item>
                <el-descriptions-item label="hit_ratio">
                  <b :style="{color: hitColor(data.redis.hit_ratio_pct)}">{{ data.redis.hit_ratio_pct ?? 0 }}%</b>
                  <span class="dim"> (목표 ≥ 95%)</span>
                </el-descriptions-item>
                <el-descriptions-item label="memory">{{ data.redis.used_memory_mb ?? '-' }} MB</el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-col>
        </el-row>

        <!-- 4) Sync Worker 최근 이벤트 -->
        <el-row :gutter="14" style="margin-top:14px;">
          <el-col :span="12">
            <el-card shadow="never">
              <template #header><b>🔁 Sync Worker</b></template>
              <p>미처리 이벤트: <b :style="{color: data.sync_worker.unprocessed_events > 100 ? '#ef4444' : '#16a34a'}">{{ data.sync_worker.unprocessed_events }}건</b></p>
              <el-table :data="data.sync_worker.recent" size="small">
                <el-table-column prop="seq" label="seq" width="70" />
                <el-table-column prop="type" label="이벤트" />
                <el-table-column prop="processed" label="처리" width="60">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.processed === 'Y' ? 'success' : 'warning'">{{ row.processed }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="created_at" label="생성" />
              </el-table>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never">
              <template #header><b>🏢 시스템별 통계</b></template>
              <el-table :data="data.systems.rows" size="small">
                <el-table-column prop="system_cd" label="시스템" width="100" />
                <el-table-column prop="menus" label="메뉴" align="right" width="80" />
                <el-table-column prop="apis" label="API" align="right" width="80" />
                <el-table-column prop="api_mapped" label="매핑" align="right" width="80" />
                <el-table-column prop="permissions" label="권한" align="right" width="80" />
                <el-table-column prop="redis_keys" label="Redis키" align="right" />
              </el-table>
            </el-card>
          </el-col>
        </el-row>

        <!-- 5) 데이터 규모 -->
        <el-row :gutter="14" style="margin-top:14px;">
          <el-col :span="24">
            <el-card shadow="never">
              <template #header><b>📦 데이터 규모</b></template>
              <el-row :gutter="10">
                <el-col :span="3"><div class="num"><span>{{ data.data.systems }}</span><label>시스템</label></div></el-col>
                <el-col :span="3"><div class="num"><span>{{ data.data.companies }}</span><label>회사</label></div></el-col>
                <el-col :span="3"><div class="num"><span>{{ data.data.departments }}</span><label>부서</label></div></el-col>
                <el-col :span="3"><div class="num"><span>{{ data.data.users }}</span><label>사용자</label></div></el-col>
                <el-col :span="3"><div class="num"><span>{{ (data.data.apis||0).toLocaleString() }}</span><label>API</label></div></el-col>
                <el-col :span="3"><div class="num"><span>{{ data.data.menus }}</span><label>메뉴</label></div></el-col>
                <el-col :span="3"><div class="num"><span>{{ (data.data.permissions||0).toLocaleString() }}</span><label>권한</label></div></el-col>
              </el-row>
            </el-card>
          </el-col>
        </el-row>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { Monitor, Refresh } from '@element-plus/icons-vue'
import api from '@/api'

const data = ref(null)
const loading = ref(false)
const autoRefresh = ref(false)
let timer = null

async function load () {
  loading.value = true
  try {
    data.value = await api.get('/health/dashboard', { timeout: 30_000 }).then(r => r.data)
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function hitColor (v) {
  if (v == null) return '#111827'
  if (v >= 95) return '#16a34a'
  if (v >= 80) return '#eab308'
  return '#ef4444'
}

onMounted(load)
onUnmounted(() => { if (timer) clearInterval(timer) })
watch(autoRefresh, (v) => {
  if (timer) { clearInterval(timer); timer = null }
  if (v) timer = setInterval(load, 5000)
})
</script>

<style scoped>
.sec { margin: 14px 0 8px; color: #1f2937; }
.svc { border-left: 4px solid #d1d5db; }
.svc.up { border-left-color: #16a34a; }
.svc.down { border-left-color: #ef4444; }
.svc-head { display:flex; align-items:center; gap:8px; margin-bottom:6px; }
.svc-meta { color:#6b7280; font-size:12px; }
.svc-meta .dim { color:#94a3b8; }
.svc-meta .err { color: #ef4444; }
.dim { color:#94a3b8; font-size: 11px; }
.sub { color:#94a3b8; font-size: 12px; padding-top: 4px; }
.num { background:#f8fafc; padding: 12px; border-radius: 6px; text-align:center; }
.num span { display:block; font-size: 22px; font-weight: 700; color:#111827; }
.num label { font-size: 12px; color:#6b7280; }
</style>
