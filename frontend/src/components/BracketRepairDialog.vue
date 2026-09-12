<template>
  <el-dialog
    v-model="visible"
    :title="`返修管理 - ${bracket ? bracket.name : ''}（${bracket ? bracket.model : ''}）`"
    width="820px"
    destroy-on-close
    @open="handleOpen"
  >
    <!-- 当前返修状态横幅 -->
    <el-alert
      v-if="!bracket || bracket.equipmentId"
      :closable="false"
      show-icon
      type="info"
      title="仅解绑（未绑定）后的支架可标记返修；当前支架仍挂在设备上，请先解绑"
      style="margin-bottom: 14px"
    />
    <el-alert
      v-else-if="!bracket.repairStatus"
      :closable="false"
      show-icon
      type="success"
      title="该支架无返修记录，可正常参与批量挂接/换线改挂"
      style="margin-bottom: 14px"
    />
    <el-alert
      v-else-if="bracket.repairStatus === 'RETURNED_QUALIFIED'"
      :closable="false"
      show-icon
      type="success"
      style="margin-bottom: 14px"
    >
      <template #title>
        最近一次返修（<b>{{ bracket.currentRepairNo }}</b>）已检验合格回库
        <span v-if="bracket.inspector" style="margin-left: 8px">检验人：<b>{{ bracket.inspector }}</b></span>
        <span style="margin-left: 8px; color: #16a34a">可再次批量挂接/改挂</span>
      </template>
    </el-alert>
    <el-alert
      v-else-if="bracket.repairStatus === 'RETURNED_UNQUALIFIED'"
      :closable="false"
      show-icon
      type="error"
      style="margin-bottom: 14px"
    >
      <template #title>
        最近一次返修（<b>{{ bracket.currentRepairNo }}</b>）回库结论为<b>不合格</b>
        <span v-if="bracket.inspector" style="margin-left: 8px">检验人：<b>{{ bracket.inspector }}</b></span>
        <span style="margin-left: 8px">批量挂接/改挂已被拦截；如重新上线请再次送修并检验合格</span>
      </template>
    </el-alert>
    <el-alert
      v-else
      :closable="false"
      show-icon
      type="warning"
      style="margin-bottom: 14px"
    >
      <template #title>
        返修单 <b>{{ bracket.currentRepairNo }}</b> 返修中：尚未写回库结论与检验人，
        批量挂接/换线改挂将被预检拦截
      </template>
    </el-alert>

    <!-- 返修中：回库登记表单 -->
    <template v-if="canReturn">
      <div class="repair-form-title">
        <el-icon><CircleCheck /></el-icon>
        回库检验登记（结论与检验人必填）
      </div>
      <el-form
        ref="returnFormRef"
        :model="returnForm"
        :rules="returnRules"
        label-width="96px"
      >
        <div class="repair-form-grid">
          <el-form-item label="返修单号">
            <el-input :model-value="bracket?.currentRepairNo || ''" disabled />
          </el-form-item>
          <el-form-item label="回库结论" prop="returnResult">
            <el-radio-group v-model="returnForm.returnResult">
              <el-radio :value="true">合格（可再次挂接）</el-radio>
              <el-radio :value="false">不合格（继续拦截）</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="检验人" prop="inspector">
            <el-input v-model="returnForm.inspector" placeholder="必填" clearable maxlength="100" />
          </el-form-item>
          <el-form-item label="回库时间" prop="returnTime">
            <el-date-picker
              v-model="returnForm.returnTime"
              type="datetime"
              placeholder="默认当前时间"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DDTHH:mm:ss"
              style="width: 100%"
            />
          </el-form-item>
        </div>
        <el-form-item label="回库备注">
          <el-input v-model="returnForm.returnRemark" placeholder="选填" clearable maxlength="500" />
        </el-form-item>
        <div style="text-align: right">
          <el-button type="primary" :loading="submitting" @click="handleReturnSubmit">
            <el-icon><CircleCheck /></el-icon>
            <span style="margin-left: 4px">写下回库结论</span>
          </el-button>
        </div>
      </el-form>
    </template>

    <!-- 未绑定且无在途返修：送修登记表单 -->
    <template v-else-if="canCreate">
      <div class="repair-form-title">
        <el-icon><EditPen /></el-icon>
        标记返修（送修登记，此时尚未回库）
      </div>
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-width="96px"
      >
        <div class="repair-form-grid">
          <el-form-item label="返修单号" prop="repairNo">
            <el-input v-model="createForm.repairNo" placeholder="如 RP-20260912-01" clearable maxlength="100" />
          </el-form-item>
          <el-form-item label="送修时间" prop="repairTime">
            <el-date-picker
              v-model="createForm.repairTime"
              type="datetime"
              placeholder="默认当前时间"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DDTHH:mm:ss"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="送修人">
            <el-input v-model="createForm.repairOperator" placeholder="选填" clearable maxlength="100" />
          </el-form-item>
        </div>
        <el-form-item label="送修原因">
          <el-input v-model="createForm.repairReason" placeholder="故障描述，选填" clearable maxlength="500" />
        </el-form-item>
        <div style="text-align: right">
          <el-button type="warning" :loading="submitting" @click="handleCreateSubmit">
            <el-icon><Warning /></el-icon>
            <span style="margin-left: 4px">标记为返修</span>
          </el-button>
        </div>
      </el-form>
    </template>

    <!-- 历史返修单 -->
    <div class="repair-history-title">
      <el-icon><Clock /></el-icon>
      返修单记录（按支架倒序，仅最新一张为当前放行依据）
    </div>
    <el-table :data="history" v-loading="historyLoading" size="small" border max-height="300">
      <el-table-column label="返修单号" min-width="150">
        <template #default="{ row }">
          <span>{{ row.repairNo }}</span>
          <el-tag v-if="row.current" type="success" effect="dark" size="small" style="margin-left: 6px">
            当前
          </el-tag>
          <el-tag v-else type="info" effect="plain" size="small" style="margin-left: 6px">历史</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态/回库结论" width="130" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'REPAIRING'" type="warning" effect="plain" size="small">返修中</el-tag>
          <el-tag v-else-if="row.status === 'RETURNED_QUALIFIED'" type="success" effect="light" size="small">
            合格回库
          </el-tag>
          <el-tag v-else type="danger" effect="light" size="small">不合格</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="送修时间" min-width="150">
        <template #default="{ row }">{{ formatTime(row.repairTime) }}</template>
      </el-table-column>
      <el-table-column label="回库时间" min-width="150">
        <template #default="{ row }">{{ row.returnTime ? formatTime(row.returnTime) : '-' }}</template>
      </el-table-column>
      <el-table-column prop="inspector" label="检验人" min-width="90">
        <template #default="{ row }">{{ row.inspector || '-' }}</template>
      </el-table-column>
      <el-table-column prop="repairReason" label="送修原因" min-width="140">
        <template #default="{ row }">{{ row.repairReason || '-' }}</template>
      </el-table-column>
      <el-table-column prop="returnRemark" label="回库备注" min-width="140">
        <template #default="{ row }">{{ row.returnRemark || '-' }}</template>
      </el-table-column>
    </el-table>
    <el-empty
      v-if="!historyLoading && history.length === 0"
      description="暂无返修记录"
      :image-size="60"
    />
    <div class="repair-pagination" v-if="total > pagination.pageSize">
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="total"
        layout="total, prev, pager, next"
        small
        @current-change="fetchHistory"
      />
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { EditPen, CircleCheck, Clock, Warning } from '@element-plus/icons-vue'
import {
  createBracketRepair,
  returnBracketRepair,
  getBracketRepairHistory
} from '@/api/bracketRepair'
import type { Bracket, BracketRepairRecord } from '@/types'

const props = defineProps<{
  modelValue: boolean
  bracket: Bracket | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'changed'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

/** 已解绑且最新返修单处于返修中：显示回库登记表单 */
const canReturn = computed(
  () => !!props.bracket && !props.bracket.equipmentId && props.bracket.repairStatus === 'REPAIRING'
)
/** 已解绑且无返修记录或最近返修单已回库（合格/不合格）：允许再次送修 */
const canCreate = computed(
  () =>
    !!props.bracket &&
    !props.bracket.equipmentId &&
    props.bracket.repairStatus !== 'REPAIRING'
)

const submitting = ref(false)
const history = ref<BracketRepairRecord[]>([])
const historyLoading = ref(false)
const total = ref(0)
const pagination = reactive({ pageNum: 1, pageSize: 8 })

const createFormRef = ref<FormInstance>()
const createForm = reactive({
  repairNo: '',
  repairReason: '',
  repairTime: '' as string | '',
  repairOperator: ''
})
const createRules: FormRules = {
  repairNo: [{ required: true, message: '请输入返修单号', trigger: 'blur' }]
}

const returnFormRef = ref<FormInstance>()
const returnForm = reactive({
  returnResult: true as boolean,
  inspector: '',
  returnTime: '' as string | '',
  returnRemark: ''
})
const returnRules: FormRules = {
  returnResult: [{ required: true, message: '请选择回库结论', trigger: 'change' }],
  inspector: [{ required: true, message: '请输入检验人', trigger: 'blur' }]
}

const pad = (n: number) => String(n).padStart(2, '0')
const formatTime = (value?: string | null) => {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const resetForms = () => {
  createForm.repairNo = ''
  createForm.repairReason = ''
  createForm.repairTime = ''
  createForm.repairOperator = ''
  returnForm.returnResult = true
  returnForm.inspector = ''
  returnForm.returnTime = ''
  returnForm.returnRemark = ''
  createFormRef.value?.clearValidate()
  returnFormRef.value?.clearValidate()
}

const fetchHistory = async () => {
  if (!props.bracket) return
  historyLoading.value = true
  try {
    const res = await getBracketRepairHistory(props.bracket.id, {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })
    history.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (error) {
    console.error('获取返修单记录失败:', error)
  } finally {
    historyLoading.value = false
  }
}

const handleOpen = () => {
  resetForms()
  pagination.pageNum = 1
  fetchHistory()
}

const handleCreateSubmit = async () => {
  if (!props.bracket || !createFormRef.value) return
  await createFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await createBracketRepair(props.bracket!.id, {
        repairNo: createForm.repairNo.trim(),
        repairReason: createForm.repairReason.trim() || null,
        repairTime: createForm.repairTime || null,
        repairOperator: createForm.repairOperator.trim() || null
      })
      ElMessage.success('已标记为返修：写回库结论与检验人前，批量挂接/改挂将被拦截')
      resetForms()
      pagination.pageNum = 1
      await fetchHistory()
      emit('changed')
    } catch (error) {
      console.error('送修登记失败:', error)
    } finally {
      submitting.value = false
    }
  })
}

const handleReturnSubmit = async () => {
  if (!props.bracket || !props.bracket.currentRepairId || !returnFormRef.value) return
  await returnFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await returnBracketRepair(props.bracket!.id, props.bracket!.currentRepairId!, {
        returnResult: returnForm.returnResult,
        inspector: returnForm.inspector.trim(),
        returnTime: returnForm.returnTime || null,
        returnRemark: returnForm.returnRemark.trim() || null
      })
      ElMessage.success(
        returnForm.returnResult
          ? '回库合格已登记，该支架可再次批量挂接/改挂'
          : '回库结论为不合格，该支架仍被禁止批量挂接/改挂'
      )
      resetForms()
      pagination.pageNum = 1
      await fetchHistory()
      emit('changed')
    } catch (error) {
      console.error('回库登记失败:', error)
    } finally {
      submitting.value = false
    }
  })
}
</script>

<style scoped>
.repair-form-title,
.repair-history-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin: 4px 0 12px;
}

.repair-history-title {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px dashed #e2e8f0;
}

.repair-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.repair-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}
</style>
