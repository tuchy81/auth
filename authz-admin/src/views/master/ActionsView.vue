<template>
  <el-card>
    <template #header>
      <div style="display:flex; align-items:center; justify-content:space-between;">
        <b>액션 마스터 (시스템: {{ app.systemCd }})</b>
        <el-button type="primary" size="small" @click="showAdd = true"><el-icon><Plus /></el-icon>액션 추가</el-button>
      </div>
    </template>
    <el-table :data="rows" stripe size="small">
      <el-table-column prop="actionCd" label="코드" width="120" />
      <el-table-column prop="actionNm" label="이름" />
      <el-table-column prop="sortOrder" label="정렬" width="100" />
      <el-table-column label="관리" width="220">
        <template #default="{ row }">
          <el-button size="small" link @click="edit(row)">수정</el-button>
          <el-button size="small" link type="danger" @click="del(row)">삭제</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showAdd" title="액션 추가/수정" width="480px" @close="reset()">
      <el-form label-width="100px">
        <el-form-item label="코드"><el-input v-model="form.actionCd" :disabled="form._editing" /></el-form-item>
        <el-form-item label="이름"><el-input v-model="form.actionNm" /></el-form-item>
        <el-form-item label="정렬 순서"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdd = false">취소</el-button>
        <el-button type="primary" @click="save()">저장</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { Master } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAppStore } from '@/store'
const app = useAppStore()
const rows = ref([])
const showAdd = ref(false)
const form = reactive({ actionCd: '', actionNm: '', sortOrder: 0, _editing: false })

onMounted(load)
watch(() => app.systemCd, load)

async function load () { rows.value = await Master.actions(app.systemCd) }
function reset () { form.actionCd=''; form.actionNm=''; form.sortOrder=0; form._editing=false }
function edit (row) { Object.assign(form, row, { _editing: true }); showAdd.value = true }
async function save () {
  if (!form.actionCd || !form.actionNm) return ElMessage.error('코드/이름 필수')
  if (form._editing) await Master.updateAction(app.systemCd, form.actionCd, { actionCd: form.actionCd, actionNm: form.actionNm, sortOrder: form.sortOrder })
  else await Master.saveAction(app.systemCd, { actionCd: form.actionCd, actionNm: form.actionNm, sortOrder: form.sortOrder })
  ElMessage.success('저장됨')
  showAdd.value = false
  await load()
}
async function del (row) {
  await ElMessageBox.confirm(`'${row.actionCd}' 액션을 삭제할까요?`, '확인', { type: 'warning' })
  await Master.deleteAction(app.systemCd, row.actionCd)
  ElMessage.success('삭제됨')
  await load()
}
</script>
