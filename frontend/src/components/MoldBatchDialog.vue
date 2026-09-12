<template>
  <el-dialog
    v-model="visible"
    :title="`换模批次登记 - ${equipment?.code || ''} ${equipment?.name || ''}`"
    width="780px"
    destroy-on-close
    @open="handleOpen"
  >
    <!-- 当前批次横幅 -->
    <el-alert
      v-if="equipment?.moldBatchReady"
      :closable="false"
      show-icon
      type="success"
      style="margin-bottom: 14px"
    >
      <template #title>
        当前模具批次：<b>{{ equipment.currentBatchNo }}</b>
        <span style="margin-left: 8px">型号：<b>{{ equipment.currentMoldModel }}</b></span>
        <span v-if="equipment.currentBatchChangeTime" style="margin-left: 8px; color: #64748b">
          换模时间 {{ formatTime(equipment.currentBatchChangeTime) }}
        </span>
      </template>
    </el-alert>
    <el-alert
      v-else-if="hasAllowedMolds"
      :closable="false"
      show-icon
      type="error"
      title="该机尚未登记当前模具批次：批量挂接与换线改挂已被拦截，请先登记当前批次"
      style="margin-bottom: 14px"
    />
    <el-alert
      v-else
      :closable="false"
      show-icon
      type="warning"
      title="该机尚未维护允许模具型号清单：请先在「配套规则」中配置，再登记批次"
      style="margin-bottom: 14px"
    />

    <!-- 登记表单 -->
    <div class="mold-form-title">
      <el-icon><EditPen /></el-icon>
      换模后登记当前批次
    </div>
    <el-form
      ref="formRef"
      :model="form"
      :rules="formRules"
      label-width="96px"
      :disabled="!hasAllowedMolds"
    >
      <div class="mold-form-grid">
        <el-form-item label="模具批次号" prop="batchNo">
          <el-input v-model="form.batchNo" placeholder="如 MB-20260912-01" clearable maxlength="100" />
        </el-form-item>
        <el-form-item label="模具型号" prop="moldModel">
          <el-select
            v-model="form.moldModel"
            placeholder="必须在允许清单内"
            style="width: 100%"
            filterable
          >
            <el-option
              v-for="m in equipment?.allowedMoldModels || []"
              :key="m"
              :label="m"
              :value="m"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="换模时间" prop="changeTime">
          <el-date-picker
            v-model="form.changeTime"
            type="datetime"
            placeholder="默认当前时间"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="form.operator" placeholder="选填" clearable maxlength="100" />
        </el-form-item>
      </div>
      <el-form-item label="备注">
        <el-input v-model="form.remark" placeholder="选填" clearable maxlength="500" />
      </el-form-item>
      <div style="text-align: right">
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="!hasAllowedMolds"
          @click="handleSubmit"
        >
          <el-icon><CircleCheck /></el-icon>
          <span style="margin-left: 4px">写下当前批次</span>
        </el-button>
      </div>
    </el-form>

    <!-- 历史换模记录 -->
    <div class="mold-history-title">
      <el-icon><Clock /></el-icon>
      历史换模记录（按设备倒序，仅最新一条为当前放行依据）
    </div>
    <el-table :data="history" v-loading="historyLoading" size="small" border max-height="280">
      <el-table-column label="批次" min-width="150">
        <template #default="{ row }">
          <span>{{ row.batchNo }}</span>
          <el-tag v-if="row.current" type="success" effect="dark" size="small" style="margin-left: 6px">
            当前批次
          </el-tag>
          <el-tag v-else type="info" effect="plain" size="small" style="margin-left: 6px">历史</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="moldModel" label="模具型号" min-width="110" />
      <el-table-column label="换模时间" min-width="150">
        <template #default="{ row }">{{ formatTime(row.changeTime) }}</template>
      </el-table-column>
      <el-table-column prop="operator" label="操作人" min-width="90">
        <template #default="{ row }">{{ row.operator || '-' }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140">
        <template #default="{ row }">{{ row.remark || '-' }}</template>
      </el-table-column>
    </el-table>
    <el-empty
      v-if="!historyLoading && history.length === 0"
      description="暂无换模记录"
      :image-size="60"
    />
    <div class="mold-pagination" v-if="total > pagination.pageSize">
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
import { EditPen, CircleCheck, Clock } from '@element-plus/icons-vue'
import { registerMoldBatch, getMoldBatchHistory } from '@/api/equipment'
import type { Equipment, MoldBatchRecord } from '@/types'

const props = defineProps<{
  modelValue: boolean
  equipment: Equipment | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'registered'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const hasAllowedMolds = computed(() => (props.equipment?.allowedMoldModels?.length || 0) > 0)

const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive({
  batchNo: '',
  moldModel: '',
  changeTime: '' as string | '',
  operator: '',
  remark: ''
})

const formRules: FormRules = {
  batchNo: [{ required: true, message: '请输入模具批次号', trigger: 'blur' }],
  moldModel: [{ required: true, message: '请选择模具型号（须在允许清单内）', trigger: 'change' }]
}

const history = ref<MoldBatchRecord[]>([])
const historyLoading = ref(false)
const total = ref(0)
const pagination = reactive({ pageNum: 1, pageSize: 8 })

const pad = (n: number) => String(n).padStart(2, '0')
const formatTime = (value?: string | null) => {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const resetForm = () => {
  form.batchNo = ''
  form.moldModel = ''
  form.changeTime = ''
  form.operator = ''
  form.remark = ''
  formRef.value?.clearValidate()
}

const fetchHistory = async () => {
  if (!props.equipment) return
  historyLoading.value = true
  try {
    const res = await getMoldBatchHistory(props.equipment.id, {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })
    history.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (error) {
    console.error('获取换模历史失败:', error)
  } finally {
    historyLoading.value = false
  }
}

const handleOpen = () => {
  resetForm()
  pagination.pageNum = 1
  fetchHistory()
}

const handleSubmit = async () => {
  if (!props.equipment || !formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await registerMoldBatch(props.equipment!.id, {
        batchNo: form.batchNo.trim(),
        moldModel: form.moldModel,
        changeTime: form.changeTime || null,
        operator: form.operator.trim() || null,
        remark: form.remark.trim() || null
      })
      ElMessage.success('当前模具批次已写下，批量挂接/换线改挂已按此批次放行')
      resetForm()
      pagination.pageNum = 1
      await fetchHistory()
      emit('registered')
    } catch (error) {
      console.error('登记模具批次失败:', error)
    } finally {
      submitting.value = false
    }
  })
}
</script>

<style scoped>
.mold-form-title,
.mold-history-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin: 4px 0 12px;
}

.mold-history-title {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px dashed #e2e8f0;
}

.mold-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.mold-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}
</style>
