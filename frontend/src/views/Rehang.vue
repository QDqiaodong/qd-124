<template>
  <div class="batch-bind-container">
    <div class="batch-bind-left">
      <div style="margin-bottom: 16px">
        <div class="page-title" style="margin-bottom: 12px">第一步：选择源设备上的已挂支架</div>
        <el-select
          v-model="sourceEquipmentId"
          placeholder="请选择源设备（支架当前所在封口机）"
          style="width: 100%"
          filterable
          @change="handleSourceChange"
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
                已挂 {{ eq.bracketCount || 0 }} 个
              </span>
            </div>
          </el-option>
        </el-select>
      </div>

      <div class="toolbar" style="margin-bottom: 12px">
        <div class="toolbar-left">
          <el-input
            v-model="searchName"
            placeholder="搜索支架名称"
            clearable
            style="width: 200px"
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
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>
      </div>

      <el-table
        ref="tableRef"
        :data="filteredBrackets"
        v-loading="bracketLoading"
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
        <el-table-column label="当前挂载" width="100" align="center">
          <template>
            <el-tag type="success" effect="light" size="small">已挂</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 12px">
        <div style="font-size: 14px; color: #64748b">
          源设备已挂 <span style="color: #1E40AF; font-weight: 600">{{ sourceBrackets.length }}</span> 项，
          已选 <span style="color: #1E40AF; font-weight: 600">{{ selectedBrackets.length }}</span> 项
        </div>
      </div>
    </div>

    <div class="batch-bind-right">
      <div class="page-title" style="margin-bottom: 20px">第二步：改挂到目标封口机</div>

      <div class="selected-count-box">
        <div class="count-label">选择改挂支架数量</div>
        <div class="count-value">{{ selectedBrackets.length }}</div>
      </div>

      <div class="action-section">
        <el-button
          type="danger"
          plain
          style="width: 100%"
          :disabled="selectedBrackets.length === 0"
          @click="clearSelection"
        >
          <el-icon><Delete /></el-icon>
          <span>清空已选</span>
        </el-button>
      </div>

      <div class="action-section">
        <div class="section-label">目标封口机</div>
        <el-select
          v-model="targetEquipmentId"
          placeholder="请选择目标封口机"
          style="width: 100%"
          filterable
        >
          <el-option
            v-for="eq in targetEquipmentList"
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
        <div class="target-rule-title">
          <el-icon><InfoFilled /></el-icon>
          目标机现行配套规则（按此逐项预检）
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
          该设备未配置配套规则，改挂时不做型号/尺寸限制
        </div>
      </div>

      <el-button
        type="primary"
        size="large"
        style="width: 100%; margin-top: auto"
        :loading="submitting"
        :disabled="!sourceEquipmentId || selectedBrackets.length === 0 || !targetEquipmentId"
        @click="handleRehangCheck"
      >
        <el-icon><Switch /></el-icon>
        <span>改挂预检</span>
      </el-button>

      <div style="margin-top: 16px; padding: 12px; background: #f8fafc; border-radius: 6px; font-size: 12px; color: #64748b; line-height: 1.6">
        <div style="font-weight: 600; margin-bottom: 6px; color: #334155">
          <el-icon style="vertical-align: -2px"><InfoFilled /></el-icon>
          操作说明
        </div>
        <div>1. 左侧选择源设备，勾选其上已挂支架</div>
        <div>2. 选择目标封口机（不能与源设备相同）</div>
        <div>3. 按目标机现行型号、长宽与容量规则逐项预检</div>
        <div>4. 确认后一次性改挂通过项，全程保持已绑定、无未绑定中间态；冲突项仍留在源设备</div>
      </div>
    </div>

    <RehangDialog
      v-model="checkDialogVisible"
      :result="checkResult"
      title="换线改挂预检"
      :confirm-loading="confirmLoading"
      @confirm="handleCheckConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Delete, InfoFilled, Switch } from '@element-plus/icons-vue'
import { getAllEquipment, getEquipmentBrackets } from '@/api/equipment'
import { checkRehang, confirmRehang } from '@/api/binding'
import type { Bracket, Equipment, RehangCheckResult } from '@/types'
import RehangDialog from '@/components/RehangDialog.vue'

const route = useRoute()

const submitting = ref(false)
const confirmLoading = ref(false)
const bracketLoading = ref(false)
const searchName = ref('')
const searchModel = ref('')

const equipmentList = ref<Equipment[]>([])
const sourceEquipmentId = ref<number | null>(null)
const targetEquipmentId = ref<number | null>(null)

const sourceBrackets = ref<Bracket[]>([])
const selectedBrackets = ref<Bracket[]>([])

const checkDialogVisible = ref(false)
const checkResult = ref<RehangCheckResult | null>(null)

const targetEquipmentList = computed(() =>
  equipmentList.value.filter((e) => e.id !== sourceEquipmentId.value)
)

const targetEquipment = computed(
  () => equipmentList.value.find((e) => e.id === targetEquipmentId.value) || null
)

const filteredBrackets = computed(() => {
  const nameKw = searchName.value.trim()
  const modelKw = searchModel.value.trim()
  return sourceBrackets.value.filter((b) => {
    const matchName = !nameKw || (b.name || '').includes(nameKw)
    const matchModel = !modelKw || (b.model || '').includes(modelKw)
    return matchName && matchModel
  })
})

const formatRange = (min?: number | null, max?: number | null) => {
  const minText = min != null ? min : '不限'
  const maxText = max != null ? max : '不限'
  return `${minText} ~ ${maxText} mm`
}

const fetchEquipmentList = async (selectSourceId?: number) => {
  try {
    const res = await getAllEquipment()
    equipmentList.value = res.data || []
    if (selectSourceId != null && equipmentList.value.some((e) => e.id === selectSourceId)) {
      sourceEquipmentId.value = selectSourceId
      await fetchSourceBrackets()
    }
  } catch (error) {
    console.error('获取设备列表失败:', error)
  }
}

const fetchSourceBrackets = async () => {
  if (!sourceEquipmentId.value) {
    sourceBrackets.value = []
    return
  }
  bracketLoading.value = true
  try {
    const res = await getEquipmentBrackets(sourceEquipmentId.value)
    sourceBrackets.value = res.data || []
  } catch (error) {
    console.error('获取源设备支架失败:', error)
    sourceBrackets.value = []
  } finally {
    bracketLoading.value = false
  }
}

const handleSourceChange = async () => {
  targetEquipmentId.value = null
  selectedBrackets.value = []
  searchName.value = ''
  searchModel.value = ''
  await fetchSourceBrackets()
}

const handleSelectionChange = (selection: Bracket[]) => {
  selectedBrackets.value = selection
}

const clearSelection = () => {
  selectedBrackets.value = []
}

const handleRehangCheck = async () => {
  if (!sourceEquipmentId.value) {
    ElMessage.warning('请选择源设备')
    return
  }
  if (selectedBrackets.value.length === 0) {
    ElMessage.warning('请勾选需要改挂的已挂支架')
    return
  }
  if (!targetEquipmentId.value) {
    ElMessage.warning('请选择目标封口机')
    return
  }
  if (targetEquipmentId.value === sourceEquipmentId.value) {
    ElMessage.warning('目标封口机不能与源设备相同')
    return
  }

  submitting.value = true
  try {
    const res = await checkRehang({
      sourceEquipmentId: sourceEquipmentId.value,
      targetEquipmentId: targetEquipmentId.value,
      bracketIds: selectedBrackets.value.map((b) => b.id)
    })
    checkResult.value = res.data
    checkDialogVisible.value = true
  } catch (error) {
    console.error('改挂预检失败:', error)
  } finally {
    submitting.value = false
  }
}

const handleCheckConfirm = async () => {
  if (!checkResult.value || !sourceEquipmentId.value) return
  const passedCount = checkResult.value.passedItems.length
  if (passedCount === 0) {
    ElMessage.warning('没有通过预检的支架，无法改挂')
    return
  }
  // 确认时提交本次勾选的全部支架：后端重新预检，仅改挂通过项，冲突项保留在源设备
  const bracketIds = checkResult.value.items.map((item) => item.bracketId)
  confirmLoading.value = true
  try {
    const res = await confirmRehang({
      sourceEquipmentId: checkResult.value.sourceEquipmentId,
      targetEquipmentId: checkResult.value.equipmentId,
      bracketIds
    })
    const rehungCount = res.data?.rehungCount ?? passedCount
    const conflictCount = res.data?.conflicts.length ?? checkResult.value.conflicts.length
    if (conflictCount > 0) {
      ElMessage.warning(`已改挂 ${rehungCount} 项，${conflictCount} 项冲突仍留在源设备`)
    } else {
      ElMessage.success(`改挂成功，共改挂 ${rehungCount} 项，全程未出现未绑定`)
    }
    checkDialogVisible.value = false
    selectedBrackets.value = []
    // 源设备支架清单与设备占用同步刷新；未绑定统计在档案页/设备清单重新进入时按库实时统计，改挂不改变未绑定数量
    await Promise.all([fetchSourceBrackets(), fetchEquipmentList()])
  } catch (error) {
    console.error('改挂失败:', error)
  } finally {
    confirmLoading.value = false
  }
}

onMounted(() => {
  const presetSourceId = route.query.sourceId ? Number(route.query.sourceId) : undefined
  fetchEquipmentList(presetSourceId)
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
</style>
