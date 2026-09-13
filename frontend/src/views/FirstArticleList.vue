<template>
  <div>
    <div class="page-container">
      <div class="page-title">首件尺寸确认单</div>

      <div class="toolbar">
        <div class="toolbar-left">
          <el-select
            v-model="filterEquipmentId"
            placeholder="按机台筛选"
            clearable
            filterable
            style="width: 220px"
            @change="handleSearch"
            @clear="handleSearch"
          >
            <el-option
              v-for="eq in equipmentOptions"
              :key="eq.id"
              :label="`${eq.code} ${eq.name}`"
              :value="eq.id"
            />
          </el-select>
          <el-select
            v-model="filterStatus"
            placeholder="按放行结果筛选"
            clearable
            style="width: 180px"
            @change="handleSearch"
            @clear="handleSearch"
          >
            <el-option label="待签放" value="PENDING" />
            <el-option label="已放行" value="RELEASED" />
            <el-option label="已退回再量" value="RETURNED" />
          </el-select>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            <span>搜索</span>
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            <span>重置</span>
          </el-button>
        </div>
        <div class="toolbar-right">
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>
            <span>新建首件确认单</span>
          </el-button>
        </div>
      </div>

      <el-alert
        title="换模放量前必须先开首件确认单：量差超线的单不能放行量产，只能退回再量；签放/退回均留签字人与时间"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />

      <el-table
        :data="list"
        v-loading="loading"
        border
        @row-click="openDetail"
      >
        <el-table-column prop="formNo" label="单号" min-width="150" />
        <el-table-column label="机台" min-width="140">
          <template #default="{ row }">
            {{ row.equipmentCode }} {{ row.equipmentName }}
          </template>
        </el-table-column>
        <el-table-column label="模具批次" min-width="160">
          <template #default="{ row }">
            {{ row.batchNo }}（{{ row.moldModel }}）
          </template>
        </el-table-column>
        <el-table-column label="实测 长×宽×高 (mm)" width="180" align="center">
          <template #default="{ row }">
            {{ row.measuredLength }} × {{ row.measuredWidth }} × {{ row.measuredHeight }}
          </template>
        </el-table-column>
        <el-table-column label="量差 长/宽/高 (mm)" width="190" align="center">
          <template #default="{ row }">
            <span :class="{ 'deviation-over': row.outOfTolerance }">
              {{ formatDeviation(row.lengthDeviation) }} /
              {{ formatDeviation(row.widthDeviation) }} /
              {{ formatDeviation(row.heightDeviation) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="量差判定" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.outOfTolerance" type="danger" effect="dark" size="small">超线</el-tag>
            <el-tag v-else type="success" effect="plain" size="small">合格</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="放行结果" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="dark" size="small">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="签字信息" min-width="170">
          <template #default="{ row }">
            <template v-if="row.status === 'RELEASED'">
              {{ row.releaseSigner }} · {{ formatTime(row.releaseTime) }}
            </template>
            <template v-else-if="row.status === 'RETURNED'">
              {{ row.returnOperator }} · {{ formatTime(row.returnTime) }}
            </template>
            <span v-else style="color: #94a3b8">待签放</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click.stop="openDetail(row)">
              详情
            </el-button>
            <el-button
              v-if="row.status === 'PENDING' && !row.outOfTolerance"
              size="small"
              type="success"
              link
              @click.stop="openReleaseDialog(row)"
            >
              签放
            </el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              size="small"
              type="warning"
              link
              @click.stop="openReturnDialog(row)"
            >
              退回再量
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-if="!loading && list.length === 0"
        description="暂无首件确认单，换模放量前请先开单"
        :image-size="80"
      />

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </div>

    <!-- 新建首件确认单 -->
    <el-dialog
      v-model="createDialogVisible"
      title="新建首件尺寸确认单"
      width="720px"
      destroy-on-close
      @open="handleCreateOpen"
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createFormRules"
        label-width="110px"
      >
        <el-form-item label="机台" prop="equipmentId">
          <el-select
            v-model="createForm.equipmentId"
            placeholder="选择要放量的机台"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="eq in equipmentOptions"
              :key="eq.id"
              :label="`${eq.code} ${eq.name}`"
              :value="eq.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模具批次">
          <el-alert
            v-if="selectedEquipment && selectedEquipment.currentBatchNo"
            :closable="false"
            show-icon
            type="success"
            style="width: 100%"
          >
            <template #title>
              当前批次：<b>{{ selectedEquipment.currentBatchNo }}</b>
              <span style="margin-left: 8px">型号：<b>{{ selectedEquipment.currentMoldModel }}</b></span>
            </template>
          </el-alert>
          <el-alert
            v-else-if="selectedEquipment"
            :closable="false"
            show-icon
            type="error"
            title="该机换模后尚未登记当前模具批次，不能开首件确认单，请先在「设备配套清单」完成换模登记"
            style="width: 100%"
          />
          <span v-else class="form-tip">选择机台后自动带出当前模具批次</span>
        </el-form-item>
        <div class="dim-grid">
          <el-form-item label="标准长(mm)" prop="standardLength">
            <el-input-number v-model="createForm.standardLength" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="标准宽(mm)" prop="standardWidth">
            <el-input-number v-model="createForm.standardWidth" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="标准高(mm)" prop="standardHeight">
            <el-input-number v-model="createForm.standardHeight" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="公差(±mm)" prop="tolerance">
            <el-input-number v-model="createForm.tolerance" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="实测长(mm)" prop="measuredLength">
            <el-input-number v-model="createForm.measuredLength" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="实测宽(mm)" prop="measuredWidth">
            <el-input-number v-model="createForm.measuredWidth" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="实测高(mm)" prop="measuredHeight">
            <el-input-number v-model="createForm.measuredHeight" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
        </div>
        <el-form-item v-if="previewDeviations" label="量差预览">
          <div style="width: 100%">
            <el-tag
              v-for="item in previewDeviations"
              :key="item.label"
              :type="item.over ? 'danger' : 'success'"
              :effect="item.over ? 'dark' : 'plain'"
              size="small"
              style="margin-right: 8px"
            >
              {{ item.label }} {{ formatDeviation(item.value) }}mm
            </el-tag>
            <el-alert
              v-if="previewOutOfTolerance"
              :closable="false"
              show-icon
              type="error"
              title="量差超线：该单保存后不能放行量产，只能退回再量"
              style="margin-top: 8px"
            />
            <el-alert
              v-else
              :closable="false"
              show-icon
              type="success"
              title="量差合格：保存后可由签放人签字放行"
              style="margin-top: 8px"
            />
          </div>
        </el-form-item>
        <el-form-item label="开单人" prop="operator">
          <el-input v-model="createForm.operator" placeholder="调度姓名（必填）" clearable maxlength="100" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" placeholder="选填" clearable maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="createSubmitting"
          :disabled="!selectedEquipment || !selectedEquipment.currentBatchNo"
          @click="submitCreate"
        >
          保存确认单
        </el-button>
      </template>
    </el-dialog>

    <!-- 签放 -->
    <el-dialog
      v-model="releaseDialogVisible"
      title="首件确认单签放"
      width="480px"
      destroy-on-close
    >
      <template v-if="activeRecord">
        <el-alert
          :closable="false"
          show-icon
          type="success"
          style="margin-bottom: 14px"
        >
          <template #title>
            单号 {{ activeRecord.formNo }} · {{ activeRecord.equipmentCode }} · 批次 {{ activeRecord.batchNo }}，
            量差合格（公差 ±{{ activeRecord.tolerance }}mm），签放后即可放量生产
          </template>
        </el-alert>
        <el-form label-width="90px">
          <el-form-item label="签放人" required>
            <el-input v-model="releaseSigner" placeholder="签字人姓名（必填）" clearable maxlength="100" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="releaseDialogVisible = false">取消</el-button>
        <el-button type="success" :loading="releaseSubmitting" @click="submitRelease">
          签字放行
        </el-button>
      </template>
    </el-dialog>

    <!-- 退回再量 -->
    <el-dialog
      v-model="returnDialogVisible"
      title="首件确认单退回再量"
      width="480px"
      destroy-on-close
    >
      <template v-if="activeRecord">
        <el-alert
          v-if="activeRecord.outOfTolerance"
          :closable="false"
          show-icon
          type="error"
          title="该单量差超线，不能放行量产，退回后请重新测量并另开新单"
          style="margin-bottom: 14px"
        />
        <el-alert
          v-else
          :closable="false"
          show-icon
          type="warning"
          title="退回后该单不可再签放，重新测量需另开新单"
          style="margin-bottom: 14px"
        />
        <el-form label-width="90px">
          <el-form-item label="退回人" required>
            <el-input v-model="returnOperator" placeholder="操作人姓名（必填）" clearable maxlength="100" />
          </el-form-item>
          <el-form-item label="退回原因">
            <el-input v-model="returnReason" placeholder="选填，如：长量差超线" clearable maxlength="500" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="returnDialogVisible = false">取消</el-button>
        <el-button type="warning" :loading="returnSubmitting" @click="submitReturn">
          确认退回
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情：谁签的、几点签的 -->
    <el-dialog
      v-model="detailDialogVisible"
      title="首件尺寸确认单详情"
      width="680px"
      destroy-on-close
    >
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-alert
            v-if="detail.outOfTolerance"
            :closable="false"
            show-icon
            type="error"
            title="量差超线：该单不能放行量产"
            style="margin-bottom: 14px"
          />
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="单号">{{ detail.formNo }}</el-descriptions-item>
            <el-descriptions-item label="放行结果">
              <el-tag :type="statusTagType(detail.status)" effect="dark" size="small">
                {{ statusText(detail.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="机台">
              {{ detail.equipmentCode }} {{ detail.equipmentName }}
            </el-descriptions-item>
            <el-descriptions-item label="模具批次">
              {{ detail.batchNo }}（{{ detail.moldModel }}）
            </el-descriptions-item>
            <el-descriptions-item label="标准尺寸(mm)">
              {{ detail.standardLength }} × {{ detail.standardWidth }} × {{ detail.standardHeight }}
            </el-descriptions-item>
            <el-descriptions-item label="公差">±{{ detail.tolerance }} mm</el-descriptions-item>
            <el-descriptions-item label="实测尺寸(mm)">
              {{ detail.measuredLength }} × {{ detail.measuredWidth }} × {{ detail.measuredHeight }}
            </el-descriptions-item>
            <el-descriptions-item label="量差 长/宽/高(mm)">
              <span :class="{ 'deviation-over': detail.outOfTolerance }">
                {{ formatDeviation(detail.lengthDeviation) }} /
                {{ formatDeviation(detail.widthDeviation) }} /
                {{ formatDeviation(detail.heightDeviation) }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="开单人">{{ detail.operator }}</el-descriptions-item>
            <el-descriptions-item label="开单时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
            <template v-if="detail.status === 'RELEASED'">
              <el-descriptions-item label="签放人">{{ detail.releaseSigner }}</el-descriptions-item>
              <el-descriptions-item label="签放时间">{{ formatTime(detail.releaseTime) }}</el-descriptions-item>
            </template>
            <template v-if="detail.status === 'RETURNED'">
              <el-descriptions-item label="退回人">{{ detail.returnOperator }}</el-descriptions-item>
              <el-descriptions-item label="退回时间">{{ formatTime(detail.returnTime) }}</el-descriptions-item>
              <el-descriptions-item label="退回原因" :span="2">
                {{ detail.returnReason || '-' }}
              </el-descriptions-item>
            </template>
            <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
          </el-descriptions>
        </template>
      </div>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { getAllEquipment } from '@/api/equipment'
import {
  createFirstArticle,
  getFirstArticleList,
  getFirstArticleDetail,
  releaseFirstArticle,
  returnFirstArticle
} from '@/api/firstArticle'
import type { Equipment, FirstArticleRecord, FirstArticleStatus } from '@/types'

const loading = ref(false)
const list = ref<FirstArticleRecord[]>([])
const equipmentOptions = ref<Equipment[]>([])
const filterEquipmentId = ref<number | null>(null)
const filterStatus = ref<FirstArticleStatus | null>(null)
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const pad = (n: number) => String(n).padStart(2, '0')
const formatTime = (value?: string | null) => {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 量差带符号显示，如 +0.12 / -0.05 */
const formatDeviation = (value?: number | null) => {
  if (value == null) return '-'
  const fixed = value.toFixed(2)
  return value > 0 ? `+${fixed}` : fixed
}

const statusText = (status: FirstArticleStatus) => {
  if (status === 'RELEASED') return '已放行'
  if (status === 'RETURNED') return '已退回再量'
  return '待签放'
}

const statusTagType = (status: FirstArticleStatus) => {
  if (status === 'RELEASED') return 'success'
  if (status === 'RETURNED') return 'info'
  return 'warning'
}

const fetchEquipmentOptions = async () => {
  try {
    const res = await getAllEquipment()
    equipmentOptions.value = res.data || []
  } catch (error) {
    console.error('获取机台列表失败:', error)
  }
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getFirstArticleList({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      equipmentId: filterEquipmentId.value ?? undefined,
      status: filterStatus.value ?? undefined
    })
    if (res.data) {
      list.value = res.data.list
      pagination.total = res.data.total
    }
  } catch (error) {
    console.error('获取首件确认单列表失败:', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  fetchList()
}

const handleReset = () => {
  filterEquipmentId.value = null
  filterStatus.value = null
  pagination.pageNum = 1
  fetchList()
}

// ============ 新建确认单 ============
const createDialogVisible = ref(false)
const createSubmitting = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({
  equipmentId: null as number | null,
  standardLength: null as number | null,
  standardWidth: null as number | null,
  standardHeight: null as number | null,
  tolerance: null as number | null,
  measuredLength: null as number | null,
  measuredWidth: null as number | null,
  measuredHeight: null as number | null,
  operator: '',
  remark: ''
})

const createFormRules: FormRules = {
  equipmentId: [{ required: true, message: '请选择机台', trigger: 'change' }],
  standardLength: [{ required: true, message: '请填写标准长', trigger: 'blur' }],
  standardWidth: [{ required: true, message: '请填写标准宽', trigger: 'blur' }],
  standardHeight: [{ required: true, message: '请填写标准高', trigger: 'blur' }],
  tolerance: [{ required: true, message: '请填写公差', trigger: 'blur' }],
  measuredLength: [{ required: true, message: '请填写实测长', trigger: 'blur' }],
  measuredWidth: [{ required: true, message: '请填写实测宽', trigger: 'blur' }],
  measuredHeight: [{ required: true, message: '请填写实测高', trigger: 'blur' }],
  operator: [{ required: true, message: '请填写开单人（调度姓名）', trigger: 'blur' }]
}

const selectedEquipment = computed<Equipment | null>(() => {
  if (createForm.equipmentId == null) return null
  return equipmentOptions.value.find((eq) => eq.id === createForm.equipmentId) || null
})

const round2 = (value: number) => Math.round(value * 100) / 100

/** 实时量差预览：实测 - 标准（带符号），任一 |量差| > 公差 即超线 */
const previewDeviations = computed(() => {
  const f = createForm
  if (
    f.standardLength == null || f.standardWidth == null || f.standardHeight == null ||
    f.tolerance == null ||
    f.measuredLength == null || f.measuredWidth == null || f.measuredHeight == null
  ) {
    return null
  }
  const items = [
    { label: '长', value: round2(f.measuredLength - f.standardLength) },
    { label: '宽', value: round2(f.measuredWidth - f.standardWidth) },
    { label: '高', value: round2(f.measuredHeight - f.standardHeight) }
  ]
  return items.map((item) => ({ ...item, over: Math.abs(item.value) > f.tolerance! }))
})

const previewOutOfTolerance = computed(() => {
  return (previewDeviations.value || []).some((item) => item.over)
})

const resetCreateForm = () => {
  createForm.equipmentId = null
  createForm.standardLength = null
  createForm.standardWidth = null
  createForm.standardHeight = null
  createForm.tolerance = null
  createForm.measuredLength = null
  createForm.measuredWidth = null
  createForm.measuredHeight = null
  createForm.operator = ''
  createForm.remark = ''
  createFormRef.value?.clearValidate()
}

const openCreateDialog = () => {
  createDialogVisible.value = true
}

const handleCreateOpen = () => {
  resetCreateForm()
  fetchEquipmentOptions()
}

const submitCreate = async () => {
  if (!createFormRef.value) return
  await createFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (!selectedEquipment.value?.currentBatchNo) {
      ElMessage.error('该机尚未登记当前模具批次，不能开首件确认单')
      return
    }
    createSubmitting.value = true
    try {
      await createFirstArticle({
        equipmentId: createForm.equipmentId!,
        standardLength: createForm.standardLength!,
        standardWidth: createForm.standardWidth!,
        standardHeight: createForm.standardHeight!,
        tolerance: createForm.tolerance!,
        measuredLength: createForm.measuredLength!,
        measuredWidth: createForm.measuredWidth!,
        measuredHeight: createForm.measuredHeight!,
        operator: createForm.operator.trim(),
        remark: createForm.remark.trim() || null
      })
      ElMessage.success(
        previewOutOfTolerance.value
          ? '确认单已保存：量差超线，不能放行，请退回再量'
          : '确认单已保存：量差合格，待签放'
      )
      createDialogVisible.value = false
      fetchList()
    } catch (error) {
      console.error('保存首件确认单失败:', error)
    } finally {
      createSubmitting.value = false
    }
  })
}

// ============ 签放 / 退回再量 ============
const activeRecord = ref<FirstArticleRecord | null>(null)
const releaseDialogVisible = ref(false)
const releaseSubmitting = ref(false)
const releaseSigner = ref('')
const returnDialogVisible = ref(false)
const returnSubmitting = ref(false)
const returnOperator = ref('')
const returnReason = ref('')

const openReleaseDialog = (row: FirstArticleRecord) => {
  if (row.outOfTolerance) {
    ElMessage.error('量差超线的单不能放行量产，只能退回再量')
    return
  }
  activeRecord.value = row
  releaseSigner.value = ''
  releaseDialogVisible.value = true
}

const submitRelease = async () => {
  if (!activeRecord.value) return
  if (!releaseSigner.value.trim()) {
    ElMessage.error('请填写签放人')
    return
  }
  releaseSubmitting.value = true
  try {
    await releaseFirstArticle(activeRecord.value.id, { signer: releaseSigner.value.trim() })
    ElMessage.success('已签字放行，可放量生产')
    releaseDialogVisible.value = false
    fetchList()
  } catch (error) {
    console.error('签放失败:', error)
  } finally {
    releaseSubmitting.value = false
  }
}

const openReturnDialog = (row: FirstArticleRecord) => {
  activeRecord.value = row
  returnOperator.value = ''
  returnReason.value = row.outOfTolerance ? '量差超线，退回再量' : ''
  returnDialogVisible.value = true
}

const submitReturn = async () => {
  if (!activeRecord.value) return
  if (!returnOperator.value.trim()) {
    ElMessage.error('请填写退回人')
    return
  }
  returnSubmitting.value = true
  try {
    await returnFirstArticle(activeRecord.value.id, {
      operator: returnOperator.value.trim(),
      reason: returnReason.value.trim() || null
    })
    ElMessage.success('已退回再量，重新测量请另开新单')
    returnDialogVisible.value = false
    fetchList()
  } catch (error) {
    console.error('退回失败:', error)
  } finally {
    returnSubmitting.value = false
  }
}

// ============ 详情 ============
const detailDialogVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<FirstArticleRecord | null>(null)

const openDetail = async (row: FirstArticleRecord) => {
  detailDialogVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const res = await getFirstArticleDetail(row.id)
    detail.value = res.data
  } catch (error) {
    console.error('获取确认单详情失败:', error)
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  fetchEquipmentOptions()
  fetchList()
})
</script>

<style scoped>
.dim-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.form-tip {
  font-size: 12px;
  color: #94a3b8;
}

.deviation-over {
  color: #dc2626;
  font-weight: 600;
}
</style>
