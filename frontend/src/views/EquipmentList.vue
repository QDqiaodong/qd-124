<template>
  <div>
    <div class="page-container" style="margin-bottom: 24px">
      <div class="page-title">未绑定支架统计</div>
      <div class="unbound-stat">
        <div class="unbound-icon">
          <el-icon><Warning /></el-icon>
        </div>
        <div class="unbound-info">
          <div class="unbound-label">当前未绑定支架数量</div>
          <div class="unbound-value">{{ unboundCount }} 个</div>
        </div>
      </div>
    </div>

    <div class="page-container">
      <div class="page-title">设备配套清单</div>

      <div class="toolbar">
        <div class="toolbar-left">
          <el-input
            v-model="searchCode"
            placeholder="搜索设备编号"
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
            v-model="searchName"
            placeholder="搜索设备名称"
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

      <div class="equipment-card-list" v-loading="loading">
        <div
          v-for="eq in equipmentList"
          :key="eq.id"
          class="equipment-card"
        >
          <div class="equipment-card-header" @click="toggleExpand(eq.id)">
            <div class="equipment-card-info">
              <div class="equipment-code">
                <el-icon style="vertical-align: -2px; margin-right: 6px"><Monitor /></el-icon>
                {{ eq.code }}
              </div>
              <div class="equipment-name">{{ eq.name }}</div>
            </div>
            <div style="display: flex; align-items: center; gap: 12px">
              <el-badge :value="eq.bracketCount || 0" :max="99" type="primary">
                <el-button size="small" type="primary" plain>
                  <el-icon><Grid /></el-icon>
                  <span style="margin-left: 4px">配套支架</span>
                </el-button>
              </el-badge>
              <el-icon
                :style="{
                  transition: 'transform 0.3s',
                  transform: expandedIds.has(eq.id) ? 'rotate(180deg)' : 'rotate(0deg)',
                  color: '#1E40AF',
                  fontSize: '18px'
                }"
              >
                <ArrowDown />
              </el-icon>
            </div>
          </div>
          <div
            v-show="expandedIds.has(eq.id)"
            class="equipment-card-body"
          >
            <div
              v-loading="loadingBracketId === eq.id"
              style="min-height: 80px"
            >
              <el-table
                v-if="equipmentBracketsMap.get(eq.id)?.length"
                :data="equipmentBracketsMap.get(eq.id) || []"
                size="small"
                border
              >
                <el-table-column prop="name" label="支架名称" min-width="120" />
                <el-table-column prop="model" label="型号" min-width="100" />
                <el-table-column label="尺寸(长×宽)" width="140" align="center">
                  <template #default="{ row }">
                    {{ row.length }} × {{ row.width }} mm
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="100" align="center">
                  <template #default="{ row }">
                    <el-button
                      size="small"
                      type="warning"
                      link
                      @click.stop="handleUnbind(row, eq)"
                    >
                      <el-icon><Unlink /></el-icon>
                      解绑
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty
                v-else
                description="暂无配套支架"
                :image-size="80"
              />
            </div>
          </div>
        </div>
      </div>

      <el-empty v-if="!loading && equipmentList.length === 0" description="暂无设备数据" />

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[6, 12, 24, 48]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchEquipmentList"
          @current-change="fetchEquipmentList"
        />
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
  Monitor,
  Grid,
  ArrowDown,
  Warning
} from '@element-plus/icons-vue'
import { getEquipmentList, getEquipmentBrackets, getUnboundBracketCount } from '@/api/equipment'
import { unbindBracket } from '@/api/binding'
import type { Equipment, Bracket } from '@/types'

const loading = ref(false)
const loadingBracketId = ref<number | null>(null)
const searchCode = ref('')
const searchName = ref('')
const unboundCount = ref(0)

const equipmentList = ref<Equipment[]>([])
const pagination = reactive({
  pageNum: 1,
  pageSize: 6,
  total: 0
})

const expandedIds = ref(new Set<number>())
const equipmentBracketsMap = ref(new Map<number, Bracket[]>())

const fetchUnboundCount = async () => {
  try {
    const res = await getUnboundBracketCount()
    unboundCount.value = res.data || 0
  } catch (error) {
    console.error('获取未绑定数量失败:', error)
  }
}

const fetchEquipmentList = async () => {
  loading.value = true
  try {
    const res = await getEquipmentList({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      code: searchCode.value || undefined,
      name: searchName.value || undefined
    })
    if (res.data) {
      equipmentList.value = res.data.list
      pagination.total = res.data.total
    }
  } catch (error) {
    console.error('获取设备列表失败:', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  fetchEquipmentList()
}

const handleReset = () => {
  searchCode.value = ''
  searchName.value = ''
  pagination.pageNum = 1
  fetchEquipmentList()
}

const fetchEquipmentBrackets = async (equipmentId: number) => {
  loadingBracketId.value = equipmentId
  try {
    const res = await getEquipmentBrackets(equipmentId)
    equipmentBracketsMap.value.set(equipmentId, res.data || [])
  } catch (error) {
    console.error('获取设备支架列表失败:', error)
    equipmentBracketsMap.value.set(equipmentId, [])
  } finally {
    loadingBracketId.value = null
  }
}

const toggleExpand = (equipmentId: number) => {
  if (expandedIds.value.has(equipmentId)) {
    expandedIds.value.delete(equipmentId)
  } else {
    expandedIds.value.add(equipmentId)
    if (!equipmentBracketsMap.value.has(equipmentId)) {
      fetchEquipmentBrackets(equipmentId)
    }
  }
}

const handleUnbind = (row: Bracket, eq: Equipment) => {
  ElMessageBox.confirm(
    `确定将支架"${row.name}"从设备"${eq.name}"解绑吗？`,
    '解绑确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )
    .then(async () => {
      try {
        await unbindBracket(row.id)
        ElMessage.success('解绑成功')
        fetchEquipmentBrackets(eq.id)
        fetchEquipmentList()
        fetchUnboundCount()
      } catch (error) {
        console.error('解绑失败:', error)
      }
    })
    .catch(() => {})
}

onMounted(() => {
  fetchUnboundCount()
  fetchEquipmentList()
})
</script>
