<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="760px"
    destroy-on-close
    @close="handleClose"
  >
    <div class="rule-summary" v-if="result">
      <div class="rule-summary-item">
        <span class="summary-label">目标设备</span>
        <span class="summary-value">{{ result.equipmentCode }} - {{ result.equipmentName }}</span>
      </div>
      <div class="rule-summary-item">
        <span class="summary-label">当前占用</span>
        <span class="summary-value">
          {{ result.currentCount }}<template v-if="result.maxBrackets != null"> / {{ result.maxBrackets }}</template><span v-else>（不限容量）</span> 个
        </span>
      </div>
      <div class="rule-summary-item" v-if="result.maxBrackets != null">
        <span class="summary-label">剩余容量</span>
        <span class="summary-value">{{ result.availableSlots }} 个</span>
      </div>
    </div>

    <el-alert
      :title="`校验通过 ${result?.passedItems.length || 0} 项，冲突 ${result?.conflicts.length || 0} 项`"
      :type="(result?.conflicts.length || 0) > 0 ? 'warning' : 'success'"
      :closable="false"
      show-icon
      style="margin: 12px 0"
    >
      <span v-if="(result?.conflicts.length || 0) > 0">
        确认后仅绑定 {{ result?.passedItems.length || 0 }} 个通过项，冲突项保持原绑定不变
      </span>
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
      <el-table-column label="校验结果" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.passed" type="success" effect="light" size="small">通过</el-tag>
          <el-tag v-else type="danger" effect="light" size="small">冲突</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="原因" min-width="220">
        <template #default="{ row }">
          <span v-if="row.passed" style="color: #16a34a">符合配套规则，可绑定</span>
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
        确认绑定（{{ result?.passedItems.length || 0 }} 项）
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { BindCheckResult } from '@/types'

const props = defineProps<{
  modelValue: boolean
  result: BindCheckResult | null
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

const title = computed(() => props.title || '绑定规则校验结果')

const handleConfirm = () => {
  emit('confirm')
}

const handleClose = () => {
  emit('update:modelValue', false)
}
</script>

<style scoped>
.rule-summary {
  display: flex;
  gap: 32px;
  padding: 12px 16px;
  background: #f8fafc;
  border-radius: 6px;
}

.rule-summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.summary-label {
  font-size: 12px;
  color: #64748b;
}

.summary-value {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
}
</style>
