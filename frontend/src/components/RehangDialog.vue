<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="800px"
    destroy-on-close
    @close="handleClose"
  >
    <div class="rehang-flow" v-if="result">
      <div class="rehang-flow-node">
        <span class="flow-label">源设备</span>
        <span class="flow-value">{{ result.sourceEquipmentCode }} - {{ result.sourceEquipmentName }}</span>
      </div>
      <el-icon class="rehang-flow-arrow"><Right /></el-icon>
      <div class="rehang-flow-node">
        <span class="flow-label">目标封口机</span>
        <span class="flow-value">{{ result.equipmentCode }} - {{ result.equipmentName }}</span>
      </div>
      <div class="rehang-flow-node">
        <span class="flow-label">目标机当前占用</span>
        <span class="flow-value">
          {{ result.currentCount }}<template v-if="result.maxBrackets != null"> / {{ result.maxBrackets }}</template><template v-else>（不限容量）</template> 个
        </span>
      </div>
      <div class="rehang-flow-node" v-if="result.maxBrackets != null">
        <span class="flow-label">剩余容量</span>
        <span class="flow-value">{{ result.availableSlots }} 个</span>
      </div>
      <div class="rehang-flow-node">
        <span class="flow-label">目标机当前模具批次</span>
        <span
          class="flow-value"
          :style="result.moldBatchGate?.passed ? 'color:#16a34a' : 'color:#dc2626'"
        >
          <template v-if="result.moldBatchGate?.passed">
            {{ result.moldBatchGate.currentBatchNo }}（{{ result.moldBatchGate.currentMoldModel }}）
          </template>
          <template v-else>未就绪·已拦截</template>
        </span>
      </div>
    </div>

    <el-alert
      v-if="result?.moldBatchGate && !result.moldBatchGate.passed"
      :title="result.moldBatchGate.reason || '目标机换模批次未就绪，整单拦截'"
      type="error"
      :closable="false"
      show-icon
      style="margin: 12px 0"
    />

    <el-alert
      :title="`预检通过 ${result?.passedItems.length || 0} 项，冲突 ${result?.conflicts.length || 0} 项`"
      :type="(result?.conflicts.length || 0) > 0 ? 'warning' : 'success'"
      :closable="false"
      show-icon
      style="margin: 12px 0"
    >
      <span v-if="(result?.conflicts.length || 0) > 0">
        确认后一次性改挂 {{ result?.passedItems.length || 0 }} 个通过项（全程保持已绑定，不会变为未绑定），冲突项仍留在源设备
      </span>
      <span v-else>确认后一次性改挂全部通过项，全程保持已绑定，不会变为未绑定</span>
    </el-alert>

    <el-table
      :data="result?.items || []"
      size="small"
      border
      max-height="320"
      style="width: 100%"
    >
      <el-table-column prop="bracketName" label="支架名称" min-width="120">
        <template #default="{ row }">
          {{ row.bracketName || `#${row.bracketId}` }}
        </template>
      </el-table-column>
      <el-table-column prop="model" label="型号" min-width="110">
        <template #default="{ row }">
          {{ row.model || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="尺寸(长×宽)" width="150" align="center">
        <template #default="{ row }">
          <template v-if="row.length != null && row.width != null">
            {{ row.length }} × {{ row.width }} mm
          </template>
          <span v-else style="color: #94a3b8">-</span>
        </template>
      </el-table-column>
      <el-table-column label="预检结果" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.passed" type="success" effect="light" size="small">可改挂</el-tag>
          <el-tag v-else type="danger" effect="light" size="small">冲突</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="原因" min-width="220">
        <template #default="{ row }">
          <span v-if="row.passed" style="color: #16a34a">符合目标机现行配套规则，可改挂</span>
          <span v-else style="color: #dc2626">{{ row.reason }}</span>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="confirmLoading"
        :disabled="(result?.passedItems.length || 0) === 0"
        @click="handleConfirm"
      >
        确认改挂（{{ result?.passedItems.length || 0 }} 项）
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Right } from '@element-plus/icons-vue'
import type { RehangCheckResult } from '@/types'

const props = defineProps<{
  modelValue: boolean
  result: RehangCheckResult | null
  title?: string
  confirmLoading?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const title = computed(() => props.title || '改挂预检结果')

const handleConfirm = () => {
  emit('confirm')
}

const handleClose = () => {
  emit('update:modelValue', false)
}
</script>

<style scoped>
.rehang-flow {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  background: #f8fafc;
  border-radius: 6px;
  flex-wrap: wrap;
}

.rehang-flow-node {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.flow-label {
  font-size: 12px;
  color: #64748b;
}

.flow-value {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
}

.rehang-flow-arrow {
  font-size: 20px;
  color: #1e40af;
}
</style>
