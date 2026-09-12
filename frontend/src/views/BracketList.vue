<template>
  <div class="page-container">
    <div class="page-title">支架档案管理</div>

    <div class="stats-cards">
      <div class="stat-card total">
        <div class="stat-icon">
          <el-icon><Grid /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-label">支架总数</div>
          <div class="stat-value">{{ stats.total }}</div>
        </div>
      </div>
      <div class="stat-card bound">
        <div class="stat-icon">
          <el-icon><Link /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-label">已绑定</div>
          <div class="stat-value">{{ stats.bound }}</div>
        </div>
      </div>
      <div class="stat-card unbound">
        <div class="stat-icon">
          <el-icon><Close /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-label">未绑定</div>
          <div class="stat-value">{{ stats.unbound }}</div>
        </div>
      </div>
    </div>

    <div class="toolbar">
      <div class="toolbar-left">
        <el-input
          v-model="searchName"
          placeholder="搜索支架名称"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-input
          v-model="searchModel"
          placeholder="搜索支架型号"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
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
          <span>新增支架</span>
        </el-button>
      </div>
    </div>

    <div class="table-container">
      <el-table :data="bracketList" v-loading="loading" stripe border style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="model" label="型号" min-width="120" />
        <el-table-column prop="length" label="长(mm)" width="100" align="center" />
        <el-table-column prop="width" label="宽(mm)" width="100" align="center" />
        <el-table-column label="绑定设备" min-width="160">
          <template #default="{ row }">
            <el-tag v-if="row.equipmentName" type="success" effect="light">
              {{ row.equipmentName }}
            </el-tag>
            <el-tag v-else type="info" effect="plain">未绑定</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="返修状态" min-width="170">
          <template #default="{ row }">
            <el-tag v-if="row.repairStatus === 'REPAIRING'" type="warning" effect="dark" size="small">
              返修中（未回库）
            </el-tag>
            <el-tag v-else-if="row.repairStatus === 'RETURNED_UNQUALIFIED'" type="danger" effect="dark" size="small">
              回库不合格
            </el-tag>
            <el-tag v-else-if="row.repairStatus === 'RETURNED_QUALIFIED'" type="success" effect="light" size="small">
              已回库·合格
            </el-tag>
            <span v-else style="color: #94a3b8">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="380" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openEditDialog(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button size="small" type="danger" link @click="handleDelete(row)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
            <el-button
              v-if="!row.equipmentId"
              size="small"
              type="success"
              link
              @click="openBindDialog(row)"
            >
              <el-icon><Link /></el-icon>
              绑定
            </el-button>
            <el-button
              v-else
              size="small"
              type="warning"
              link
              @click="handleUnbind(row)"
            >
              <el-icon><Close /></el-icon>
              解绑
            </el-button>
            <el-button size="small" type="warning" link @click="openRepairDialog(row)">
              <el-icon><Tools /></el-icon>
              返修
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="pagination-container">
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="fetchBracketList"
        @current-change="fetchBracketList"
      />
    </div>

    <el-dialog
      v-model="formDialogVisible"
      :title="formDialogTitle"
      width="500px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="80px"
      >
        <el-form-item label="名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入支架名称" />
        </el-form-item>
        <el-form-item label="型号" prop="model">
          <el-input
            v-model="formData.model"
            placeholder="请输入支架型号"
            list="bracket-model-suggestions"
          />
          <datalist id="bracket-model-suggestions">
            <option v-for="model in modelSuggestions" :key="model" :value="model" />
          </datalist>
        </el-form-item>
        <el-form-item label="长(mm)" prop="length">
          <el-input-number v-model="formData.length" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="宽(mm)" prop="width">
          <el-input-number v-model="formData.width" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formSubmitting" @click="handleFormSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="bindDialogVisible"
      title="绑定设备"
      width="450px"
      destroy-on-close
    >
      <el-form label-width="80px">
        <el-form-item label="支架名称">
          <span>{{ currentBindBracket?.name }}</span>
        </el-form-item>
        <el-form-item label="选择设备" prop="equipmentId">
          <el-select
            v-model="selectedEquipmentId"
            placeholder="请选择目标设备"
            style="width: 100%"
            @change="handleEquipmentChange"
          >
            <el-option
              v-for="eq in equipmentList"
              :key="eq.id"
              :label="`${eq.code} - ${eq.name}`"
              :value="eq.id"
            >
              <div class="equipment-option">
                <span>{{ eq.code }} - {{ eq.name }}</span>
                <span class="equipment-option-rule">
                  {{ eq.ruleConfigured ? `容量 ${eq.bracketCount || 0}${eq.maxBrackets != null ? '/' + eq.maxBrackets : '/∞'}` : '未配置规则' }}
                </span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <div v-if="selectedEquipment" class="bind-rule-tip">
        <template v-if="selectedEquipment.ruleConfigured">
          <el-tag size="small" type="info" effect="plain">
            最大数量：{{ selectedEquipment.maxBrackets != null ? selectedEquipment.maxBrackets + ' 个' : '不限' }}
          </el-tag>
          <el-tag size="small" type="info" effect="plain">
            允许型号：{{ selectedEquipment.allowedModels && selectedEquipment.allowedModels.length ? selectedEquipment.allowedModels.join('、') : '不限' }}
          </el-tag>
          <el-tag size="small" type="info" effect="plain">
            长度：{{ formatRange(selectedEquipment.minLength, selectedEquipment.maxLength) }}
          </el-tag>
          <el-tag size="small" type="info" effect="plain">
            宽度：{{ formatRange(selectedEquipment.minWidth, selectedEquipment.maxWidth) }}
          </el-tag>
        </template>
        <el-tag v-else size="small" type="warning" effect="plain">该设备未配置规则，绑定不做限制</el-tag>
      </div>
      <template #footer>
        <el-button @click="bindDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="bindSubmitting" @click="handleBind">
          校验并绑定
        </el-button>
      </template>
    </el-dialog>

    <BindCheckDialog
      v-model="checkDialogVisible"
      :result="checkResult"
      :confirm-loading="confirmLoading"
      @confirm="handleCheckConfirm"
    />

    <BracketRepairDialog
      v-model="repairDialogVisible"
      :bracket="currentRepairBracket"
      @changed="handleRepairChanged"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  Search,
  Refresh,
  Plus,
  Edit,
  Delete,
  Link,
  Close,
  Grid,
  Tools
} from '@element-plus/icons-vue'
import {
  getBracketList,
  createBracket,
  updateBracket,
  deleteBracket,
  getBracketStats
} from '@/api/bracket'
import { getAllEquipment } from '@/api/equipment'
import { unbindBracket, checkBind, confirmBind } from '@/api/binding'
import { useModelSuggestions } from '@/composables/useModelSuggestions'
import type { Bracket, Equipment, BindCheckResult } from '@/types'
import BindCheckDialog from '@/components/BindCheckDialog.vue'
import BracketRepairDialog from '@/components/BracketRepairDialog.vue'

const { modelSuggestions, refreshModelSuggestions } = useModelSuggestions()

const loading = ref(false)
const searchName = ref('')
const searchModel = ref('')

const stats = reactive({
  total: 0,
  bound: 0,
  unbound: 0
})

const bracketList = ref<Bracket[]>([])
const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const fetchStats = async () => {
  try {
    const res = await getBracketStats()
    if (res.data) {
      stats.total = res.data.total
      stats.bound = res.data.bound
      stats.unbound = res.data.unbound
    }
  } catch (error) {
    console.error('获取统计数据失败:', error)
  }
}

const fetchBracketList = async () => {
  loading.value = true
  try {
    const res = await getBracketList({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      name: searchName.value || undefined,
      model: searchModel.value || undefined
    })
    if (res.data) {
      bracketList.value = res.data.list
      pagination.total = res.data.total
    }
  } catch (error) {
    console.error('获取支架列表失败:', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  fetchBracketList()
}

const handleReset = () => {
  searchName.value = ''
  searchModel.value = ''
  pagination.pageNum = 1
  fetchBracketList()
}

const formDialogVisible = ref(false)
const formDialogTitle = ref('新增支架')
const formSubmitting = ref(false)
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const formData = reactive({
  name: '',
  model: '',
  length: 0,
  width: 0
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入支架名称', trigger: 'blur' }],
  model: [{ required: true, message: '请输入支架型号', trigger: 'blur' }],
  length: [{ required: true, message: '请输入长度', trigger: 'blur' }],
  width: [{ required: true, message: '请输入宽度', trigger: 'blur' }]
}

const openCreateDialog = () => {
  isEditing.value = false
  editingId.value = null
  formDialogTitle.value = '新增支架'
  formData.name = ''
  formData.model = ''
  formData.length = 0
  formData.width = 0
  formDialogVisible.value = true
  refreshModelSuggestions()
}

const openEditDialog = (row: Bracket) => {
  isEditing.value = true
  editingId.value = row.id
  formDialogTitle.value = '编辑支架'
  formData.name = row.name
  formData.model = row.model
  formData.length = row.length
  formData.width = row.width
  formDialogVisible.value = true
  refreshModelSuggestions()
}

const handleFormSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    formSubmitting.value = true
    try {
      if (isEditing.value && editingId.value) {
        await updateBracket(editingId.value, { ...formData })
        ElMessage.success('编辑成功')
      } else {
        await createBracket({ ...formData })
        ElMessage.success('新增成功')
      }
      formDialogVisible.value = false
      fetchBracketList()
      fetchStats()
      // 型号可能新增或改名，刷新热门型号建议缓存
      refreshModelSuggestions()
    } catch (error) {
      console.error('提交失败:', error)
    } finally {
      formSubmitting.value = false
    }
  })
}

const handleDelete = (row: Bracket) => {
  ElMessageBox.confirm(`确定删除支架"${row.name}"吗？`, '删除确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(async () => {
      try {
        await deleteBracket(row.id)
        ElMessage.success('删除成功')
        fetchBracketList()
        fetchStats()
        refreshModelSuggestions()
      } catch (error) {
        console.error('删除失败:', error)
      }
    })
    .catch(() => {})
}

const bindDialogVisible = ref(false)
const bindSubmitting = ref(false)
const currentBindBracket = ref<Bracket | null>(null)
const selectedEquipmentId = ref<number | null>(null)
const equipmentList = ref<Equipment[]>([])
const checkDialogVisible = ref(false)
const checkResult = ref<BindCheckResult | null>(null)
const confirmLoading = ref(false)

const selectedEquipment = computed(
  () => equipmentList.value.find((e) => e.id === selectedEquipmentId.value) || null
)

const formatRange = (min?: number | null, max?: number | null) => {
  const minText = min != null ? min : '不限'
  const maxText = max != null ? max : '不限'
  return `${minText} ~ ${maxText} mm`
}

const fetchEquipmentList = async () => {
  try {
    const res = await getAllEquipment()
    equipmentList.value = res.data || []
  } catch (error) {
    console.error('获取设备列表失败:', error)
  }
}

const openBindDialog = (row: Bracket) => {
  currentBindBracket.value = row
  selectedEquipmentId.value = null
  checkResult.value = null
  fetchEquipmentList()
  bindDialogVisible.value = true
}

const handleEquipmentChange = () => {
  // 仅切换设备，校验在点击"校验并绑定"时统一执行
}

const handleBind = async () => {
  if (!selectedEquipmentId.value || !currentBindBracket.value) {
    ElMessage.warning('请选择目标设备')
    return
  }
  bindSubmitting.value = true
  try {
    const res = await checkBind(currentBindBracket.value.id, selectedEquipmentId.value)
    checkResult.value = res.data
    bindDialogVisible.value = false
    checkDialogVisible.value = true
  } catch (error) {
    console.error('绑定校验失败:', error)
  } finally {
    bindSubmitting.value = false
  }
}

const handleCheckConfirm = async () => {
  if (!checkResult.value || !currentBindBracket.value) return
  confirmLoading.value = true
  try {
    await confirmBind(currentBindBracket.value.id, checkResult.value.equipmentId)
    ElMessage.success('绑定成功')
    checkDialogVisible.value = false
    fetchBracketList()
    fetchStats()
  } catch (error) {
    console.error('绑定失败:', error)
  } finally {
    confirmLoading.value = false
  }
}

const handleUnbind = (row: Bracket) => {
  ElMessageBox.confirm(`确定解绑支架"${row.name}"吗？`, '解绑确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(async () => {
      try {
        await unbindBracket(row.id)
        ElMessage.success('解绑成功')
        fetchBracketList()
        fetchStats()
      } catch (error) {
        console.error('解绑失败:', error)
      }
    })
    .catch(() => {})
}

// 返修/回库：解绑后的支架可标记返修；回库结论与检验人写在返修单上，按支架可翻全部返修单
const repairDialogVisible = ref(false)
const currentRepairBracket = ref<Bracket | null>(null)

const openRepairDialog = (row: Bracket) => {
  // 弹窗内表单/历史以最新数据为准
  currentRepairBracket.value = { ...row }
  repairDialogVisible.value = true
}

const handleRepairChanged = async () => {
  // 送修/回库成功后刷新列表与统计，档案页返修状态与未绑定数量保持一致
  await fetchBracketList()
  fetchStats()
  const current = bracketList.value.find((b) => b.id === currentRepairBracket.value?.id)
  if (current) {
    currentRepairBracket.value = { ...current }
  }
}

onMounted(() => {
  fetchStats()
  fetchBracketList()
})
</script>

<style scoped>
.equipment-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 8px;
}

.equipment-option-rule {
  font-size: 12px;
  color: #94a3b8;
}

.bind-rule-tip {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: -8px;
  padding-left: 80px;
}
</style>
