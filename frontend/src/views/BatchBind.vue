<template>
  <div class="batch-bind-container">
    <div class="batch-bind-left">
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px">
        <div class="page-title" style="margin-bottom: 0">选择支架</div>
        <el-radio-group v-model="filterStatus" @change="handleFilterChange">
          <el-radio-button :value="undefined">全部</el-radio-button>
          <el-radio-button :value="0">未绑定</el-radio-button>
        </el-radio-group>
      </div>

      <div class="toolbar" style="margin-bottom: 12px">
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
      </div>

      <el-table
        ref="tableRef"
        :data="bracketList"
        v-loading="loading"
        stripe
        border
        style="flex: 1; overflow: auto"
        height="100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="model" label="型号" min-width="120" />
        <el-table-column prop="length" label="长(mm)" width="90" align="center" />
        <el-table-column prop="width" label="宽(mm)" width="90" align="center" />
        <el-table-column label="绑定状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.equipmentId" type="success" effect="light" size="small">
              已绑定
            </el-tag>
            <el-tag v-else type="warning" effect="plain" size="small">未绑定</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="返修状态" min-width="150">
          <template #default="{ row }">
            <el-tag v-if="row.repairStatus === 'REPAIRING'" type="warning" effect="dark" size="small">
              返修中·未回库
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
        <el-table-column label="绑定设备" min-width="140">
          <template #default="{ row }">
            <span v-if="row.equipmentName">{{ row.equipmentName }}</span>
            <span v-else style="color: #94a3b8">-</span>
          </template>
        </el-table-column>
      </el-table>

      <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 12px">
        <div style="font-size: 14px; color: #64748b">
          已选 <span style="color: #1E40AF; font-weight: 600">{{ selectedBrackets.length }}</span> 项
        </div>
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchBracketList"
          @current-change="fetchBracketList"
          small
        />
      </div>
    </div>

    <div class="batch-bind-right">
      <div class="page-title" style="margin-bottom: 20px">批量绑定操作</div>

      <div class="selected-count-box">
        <div class="count-label">已选择支架数量</div>
        <div class="count-value">{{ selectedBrackets.length }}</div>
      </div>

      <div class="action-section">
        <div class="section-label">操作</div>
        <el-button type="danger" plain style="width: 100%" @click="clearSelection" :disabled="selectedBrackets.length === 0">
          <el-icon><Delete /></el-icon>
          <span>清空已选</span>
        </el-button>
      </div>

      <div class="action-section">
        <div class="section-label">目标设备</div>
        <el-select
          v-model="targetEquipmentId"
          placeholder="请选择目标设备"
          style="width: 100%"
          filterable
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
      </div>

      <div v-if="targetEquipment" class="target-rule-box">
        <!-- 换模批次放行状态：未写批次/型号不在允许清单时拦截批量挂接 -->
        <div class="mold-gate" :class="targetEquipment.moldBatchReady ? 'mold-gate-ok' : 'mold-gate-blocked'">
          <div class="target-rule-title">
            <el-icon><CircleCheck v-if="targetEquipment.moldBatchReady" /><Warning v-else /></el-icon>
            当前模具批次（放行依据）
          </div>
          <template v-if="targetEquipment.moldBatchReady">
            <div class="target-rule-row">
              批次号：<b>{{ targetEquipment.currentBatchNo }}</b>
              <span style="margin-left: 8px">模具型号：<b>{{ targetEquipment.currentMoldModel }}</b></span>
            </div>
          </template>
          <div v-else class="target-rule-row" style="color: #dc2626; font-weight: 600">
            该机未登记有效当前批次（未写批次或批次型号不在允许清单），批量挂接将被拦截，请先到设备配套清单做换模登记
          </div>
          <div class="target-rule-row" style="font-size: 12px; color: #64748b">
            允许模具型号：{{ targetEquipment.allowedMoldModels && targetEquipment.allowedMoldModels.length
              ? targetEquipment.allowedMoldModels.join('、')
              : '未配置（无法登记批次）' }}
          </div>
        </div>

        <div class="target-rule-title" style="margin-top: 10px">
          <el-icon><InfoFilled /></el-icon>
          目标设备配套规则
        </div>
        <template v-if="targetEquipment.ruleConfigured">
          <div class="target-rule-row">
            最大支架数量：
            <b>{{ targetEquipment.maxBrackets != null ? targetEquipment.maxBrackets + ' 个' : '不限' }}</b>
            <span style="color: #94a3b8; margin-left: 6px">
              （当前已占用 {{ targetEquipment.bracketCount || 0 }} 个）
            </span>
          </div>
          <div class="target-rule-row">
            允许型号：{{ targetEquipment.allowedModels && targetEquipment.allowedModels.length ? targetEquipment.allowedModels.join('、') : '不限' }}
          </div>
          <div class="target-rule-row">
            长度范围：{{ formatRange(targetEquipment.minLength, targetEquipment.maxLength) }}
          </div>
          <div class="target-rule-row">
            宽度范围：{{ formatRange(targetEquipment.minWidth, targetEquipment.maxWidth) }}
          </div>
        </template>
        <div v-else class="target-rule-row" style="color: #d97706">
          该设备未配置配套规则，绑定时不做限制
        </div>
      </div>

      <el-button
        type="primary"
        size="large"
        style="width: 100%; margin-top: auto"
        :loading="submitting"
        :disabled="selectedBrackets.length === 0 || !targetEquipmentId"
        @click="handleBatchCheck"
      >
        <el-icon><CircleCheck /></el-icon>
        <span>校验并批量绑定</span>
      </el-button>

      <div style="margin-top: 16px; padding: 12px; background: #f8fafc; border-radius: 6px; font-size: 12px; color: #64748b; line-height: 1.6">
        <div style="font-weight: 600; margin-bottom: 6px; color: #334155">
          <el-icon style="vertical-align: -2px"><InfoFilled /></el-icon>
          操作说明
        </div>
        <div>1. 左侧表格勾选需要绑定的支架</div>
        <div>2. 选择目标设备</div>
        <div>3. 先按设备配套规则逐项校验，展示通过项与冲突原因</div>
        <div>4. 确认后仅绑定通过项，冲突项保持原绑定不变</div>
        <div style="color: #b45309">
          5. 返修支架必须写回库结论与检验人：返修中未回库或结论不合格的支架预检直接拦截并写明原因
        </div>
      </div>
    </div>

    <BindCheckDialog
      v-model="checkDialogVisible"
      :result="checkResult"
      title="批量绑定规则校验"
      :confirm-loading="confirmLoading"
      @confirm="handleCheckConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Search,
  Refresh,
  Delete,
  InfoFilled,
  CircleCheck,
  Warning
} from '@element-plus/icons-vue'
import { getBracketList } from '@/api/bracket'
import { getAllEquipment } from '@/api/equipment'
import { checkBatchBind, confirmBatchBind } from '@/api/binding'
import type { Bracket, Equipment, BindCheckResult } from '@/types'
import BindCheckDialog from '@/components/BindCheckDialog.vue'

const loading = ref(false)
const submitting = ref(false)
const confirmLoading = ref(false)
const searchName = ref('')
const searchModel = ref('')
const filterStatus = ref<number | undefined>(undefined)

const bracketList = ref<Bracket[]>([])
const selectedBrackets = ref<Bracket[]>([])
const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const equipmentList = ref<Equipment[]>([])
const targetEquipmentId = ref<number | null>(null)
const checkDialogVisible = ref(false)
const checkResult = ref<BindCheckResult | null>(null)

const targetEquipment = computed(
  () => equipmentList.value.find((e) => e.id === targetEquipmentId.value) || null
)

const formatRange = (min?: number | null, max?: number | null) => {
  const minText = min != null ? min : '不限'
  const maxText = max != null ? max : '不限'
  return `${minText} ~ ${maxText} mm`
}

const fetchBracketList = async () => {
  loading.value = true
  try {
    const res = await getBracketList({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      name: searchName.value || undefined,
      model: searchModel.value || undefined,
      bindStatus: filterStatus.value
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

const fetchEquipmentList = async () => {
  try {
    const res = await getAllEquipment()
    equipmentList.value = res.data || []
  } catch (error) {
    console.error('获取设备列表失败:', error)
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  fetchBracketList()
}

const handleReset = () => {
  searchName.value = ''
  searchModel.value = ''
  filterStatus.value = undefined
  pagination.pageNum = 1
  fetchBracketList()
}

const handleFilterChange = () => {
  pagination.pageNum = 1
  fetchBracketList()
}

const handleSelectionChange = (selection: Bracket[]) => {
  selectedBrackets.value = selection
}

const clearSelection = () => {
  selectedBrackets.value = []
}

const handleBatchCheck = async () => {
  if (selectedBrackets.value.length === 0) {
    ElMessage.warning('请先选择需要绑定的支架')
    return
  }
  if (!targetEquipmentId.value) {
    ElMessage.warning('请选择目标设备')
    return
  }
  // 换模批次硬联锁（前端提示，后端仍会强制拦截）：未写当前批次或型号不在允许清单时不允许批量挂接
  if (targetEquipment.value && targetEquipment.value.moldBatchReady === false) {
    ElMessage.error('目标封口机尚未登记有效当前模具批次，请先在「设备配套清单」完成换模登记')
    return
  }
  // 返修硬联锁（前端提示，后端仍会逐项强制拦截）：返修中未回库或回库不合格的支架不能批量挂接
  const blockedRepair = selectedBrackets.value.filter(
    (b) => b.repairStatus === 'REPAIRING' || b.repairStatus === 'RETURNED_UNQUALIFIED'
  )
  if (blockedRepair.length > 0) {
    const names = blockedRepair.slice(0, 3).map((b) => `「${b.name}」`).join('、')
    const suffix = blockedRepair.length > 3 ? ` 等 ${blockedRepair.length} 项` : ''
    ElMessage.error(`支架 ${names}${suffix}返修未合格回库（未写回库结论/检验人或结论不合格），不能批量挂接`)
    return
  }

  submitting.value = true
  try {
    const res = await checkBatchBind({
      bracketIds: selectedBrackets.value.map((b) => b.id),
      equipmentId: targetEquipmentId.value
    })
    checkResult.value = res.data
    checkDialogVisible.value = true
  } catch (error) {
    console.error('绑定校验失败:', error)
  } finally {
    submitting.value = false
  }
}

const handleCheckConfirm = async () => {
  if (!checkResult.value) return
  const passedIds = checkResult.value.passedItems.map((item) => item.bracketId)
  if (passedIds.length === 0) {
    ElMessage.warning('没有通过校验的支架，无法绑定')
    return
  }
  confirmLoading.value = true
  try {
    const res = await confirmBatchBind({
      bracketIds: passedIds,
      equipmentId: checkResult.value.equipmentId
    })
    const boundCount = res.data?.boundCount ?? passedIds.length
    const conflictCount = checkResult.value.conflicts.length
    if (conflictCount > 0) {
      ElMessage.warning(`已绑定 ${boundCount} 项，${conflictCount} 项冲突未绑定`)
    } else {
      ElMessage.success(`批量绑定成功，共绑定 ${boundCount} 项`)
    }
    checkDialogVisible.value = false
    selectedBrackets.value = []
    fetchBracketList()
    fetchEquipmentList()
  } catch (error) {
    console.error('批量绑定失败:', error)
  } finally {
    confirmLoading.value = false
  }
}

onMounted(() => {
  fetchBracketList()
  fetchEquipmentList()
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

.target-rule-box {
  padding: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 13px;
  color: #475569;
  line-height: 1.9;
}

.target-rule-title {
  display: flex;
  align-items: center;
  gap: 4px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 4px;
}

.target-rule-row b {
  color: #1e40af;
}

.mold-gate {
  padding: 8px 10px;
  border-radius: 6px;
  margin-bottom: 10px;
}

.mold-gate-ok {
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
}

.mold-gate-blocked {
  background: #fef2f2;
  border: 1px solid #fecaca;
}
</style>
