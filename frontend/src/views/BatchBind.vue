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
          />
        </el-select>
      </div>

      <el-button
        type="primary"
        size="large"
        style="width: 100%; margin-top: auto"
        :loading="submitting"
        :disabled="selectedBrackets.length === 0 || !targetEquipmentId"
        @click="handleBatchBind"
      >
        <el-icon><Link /></el-icon>
        <span>执行批量绑定</span>
      </el-button>

      <div style="margin-top: 16px; padding: 12px; background: #f8fafc; border-radius: 6px; font-size: 12px; color: #64748b; line-height: 1.6">
        <div style="font-weight: 600; margin-bottom: 6px; color: #334155">
          <el-icon style="vertical-align: -2px"><InfoFilled /></el-icon>
          操作说明
        </div>
        <div>1. 左侧表格勾选需要绑定的支架</div>
        <div>2. 选择目标设备</div>
        <div>3. 点击"执行批量绑定"完成操作</div>
        <div style="color: #f59e0b; margin-top: 6px">注意：已绑定的支架将被重新绑定到新设备</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search,
  Refresh,
  Delete,
  Link,
  InfoFilled
} from '@element-plus/icons-vue'
import { getBracketList } from '@/api/bracket'
import { getAllEquipment } from '@/api/equipment'
import { batchBindBrackets } from '@/api/binding'
import type { Bracket, Equipment } from '@/types'

const loading = ref(false)
const submitting = ref(false)
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

const handleBatchBind = () => {
  if (selectedBrackets.value.length === 0) {
    ElMessage.warning('请先选择需要绑定的支架')
    return
  }
  if (!targetEquipmentId.value) {
    ElMessage.warning('请选择目标设备')
    return
  }

  const targetEq = equipmentList.value.find((e) => e.id === targetEquipmentId.value)
  ElMessageBox.confirm(
    `确定将选中的 ${selectedBrackets.value.length} 个支架批量绑定到设备"${targetEq?.name || ''}"吗？`,
    '批量绑定确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )
    .then(async () => {
      submitting.value = true
      try {
        await batchBindBrackets({
          bracketIds: selectedBrackets.value.map((b) => b.id),
          equipmentId: targetEquipmentId.value!
        })
        ElMessage.success('批量绑定成功')
        clearSelection()
        fetchBracketList()
      } catch (error) {
        console.error('批量绑定失败:', error)
      } finally {
        submitting.value = false
      }
    })
    .catch(() => {})
}

onMounted(() => {
  fetchBracketList()
  fetchEquipmentList()
})
</script>
