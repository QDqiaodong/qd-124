<template>
  <div class="page-container">
    <div class="page-title">支架档案批量导入</div>

    <!-- 档案统计：导入成功后刷新未绑定数量 -->
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

    <!-- 步骤 1：上传并校验 -->
    <div class="import-card">
      <div class="import-card-title">
        <span class="step-badge">1</span>
        上传文件并校验
      </div>

      <div class="upload-row">
        <el-upload
          ref="uploadRef"
          v-model:file-list="fileList"
          drag
          :auto-upload="false"
          :limit="1"
          accept=".csv,.xlsx,.xls"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          :on-exceed="handleExceed"
          class="import-upload"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">
            将 CSV / Excel 文件拖到此处，或<em>点击选择</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              仅支持 .csv、.xlsx、.xls，单次最多 {{ maxRows }} 行；必填列：支架名称、支架型号、长(mm)、宽(mm)
            </div>
          </template>
        </el-upload>

        <div class="upload-side">
          <el-button type="primary" plain :icon="Download" @click="downloadTemplate">
            下载导入模板
          </el-button>
          <div class="rule-box">
            <div class="rule-title">
              <el-icon><InfoFilled /></el-icon>
              校验规则
            </div>
            <div class="rule-item">1. 名称、型号、长、宽均为必填项</div>
            <div class="rule-item">2. 长宽尺寸范围 {{ minDimension }} ~ {{ maxDimension }} mm</div>
            <div class="rule-item">3. 文件内同一型号只允许出现一次</div>
            <div class="rule-item">4. 型号已存在于档案时会提示，导入时自动跳过</div>
          </div>
        </div>
      </div>

      <div class="action-row">
        <el-button
          type="primary"
          :icon="Search"
          :loading="previewLoading"
          :disabled="!selectedFile"
          @click="handlePreview"
        >
          开始校验
        </el-button>
        <el-button
          v-if="preview"
          :icon="RefreshLeft"
          :disabled="importing"
          @click="resetAll"
        >
          重新选择文件
        </el-button>
      </div>
    </div>

    <!-- 步骤 2：校验结果预览 -->
    <div v-if="preview" class="import-card">
      <div class="import-card-title">
        <span class="step-badge">2</span>
        校验结果预览（共 {{ preview.totalCount }} 行）
      </div>

      <el-alert
        :closable="false"
        class="preview-alert"
        :type="preview.invalidCount > 0 ? 'warning' : 'success'"
        show-icon
      >
        <template #title>
          可导入
          <b class="cnt-valid">{{ preview.validCount }}</b> 行，
          型号已存在
          <b class="cnt-duplicate">{{ preview.duplicateCount }}</b> 行（导入时跳过），
          校验失败
          <b class="cnt-invalid">{{ preview.invalidCount }}</b> 行（不会写入）
        </template>
      </el-alert>

      <div class="preview-toolbar">
        <el-radio-group v-model="statusFilter" size="small">
          <el-radio-button value="ALL">全部 {{ preview.totalCount }}</el-radio-button>
          <el-radio-button value="VALID">可导入 {{ preview.validCount }}</el-radio-button>
          <el-radio-button value="DUPLICATE">已存在 {{ preview.duplicateCount }}</el-radio-button>
          <el-radio-button value="INVALID">校验失败 {{ preview.invalidCount }}</el-radio-button>
        </el-radio-group>
      </div>

      <el-table :data="pagedRows" stripe border max-height="420" style="width: 100%">
        <el-table-column prop="rowNum" label="行号" width="70" align="center" />
        <el-table-column prop="name" label="支架名称" min-width="140">
          <template #default="{ row }">{{ row.name ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="model" label="型号" min-width="120">
          <template #default="{ row }">{{ row.model ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="length" label="长(mm)" width="100" align="center">
          <template #default="{ row }">{{ row.length ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="width" label="宽(mm)" width="100" align="center">
          <template #default="{ row }">{{ row.width ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'VALID'" type="success" size="small">可导入</el-tag>
            <el-tag v-else-if="row.status === 'DUPLICATE'" type="warning" size="small">已存在</el-tag>
            <el-tag v-else type="danger" size="small">校验失败</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="220">
          <template #default="{ row }">
            <span v-if="row.reason" :class="row.status === 'DUPLICATE' ? 'reason-dup' : 'reason-err'">
              {{ row.reason }}
            </span>
            <span v-else style="color: #94a3b8">-</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container" v-if="filteredRows.length > pageSize">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="filteredRows.length"
          layout="total, prev, pager, next"
          small
          background
        />
      </div>

      <div class="action-row">
        <el-button
          type="primary"
          size="large"
          :icon="CircleCheck"
          :loading="importing"
          :disabled="preview.validCount === 0"
          @click="handleConfirm"
        >
          确认导入 {{ preview.validCount }} 行
        </el-button>
        <span v-if="preview.validCount === 0" class="no-valid-tip">
          没有可导入的数据行，请修正文件后重新校验
        </span>
      </div>
    </div>

    <!-- 步骤 3：导入结果 -->
    <div v-if="importResult" class="import-card">
      <div class="import-card-title">
        <span class="step-badge">3</span>
        导入结果
      </div>

      <div class="result-cards">
        <div class="result-card success">
          <div class="result-value">{{ importResult.successCount }}</div>
          <div class="result-label">成功（新增）</div>
        </div>
        <div class="result-card failed">
          <div class="result-value">{{ importResult.failedCount }}</div>
          <div class="result-label">失败（未写入）</div>
        </div>
        <div class="result-card skipped">
          <div class="result-value">{{ importResult.skippedCount }}</div>
          <div class="result-label">跳过（型号已存在）</div>
        </div>
        <div class="result-card total">
          <div class="result-value">{{ importResult.totalCount }}</div>
          <div class="result-label">文件总行数</div>
        </div>
      </div>

      <el-alert
        :closable="false"
        class="preview-alert"
        :type="importResult.failedCount === 0 ? 'success' : 'warning'"
        show-icon
        :title="resultMessage"
      />

      <template v-if="importResult.failedRows.length > 0">
        <div class="failed-header">
          <span>未入库明细（失败 + 跳过，共 {{ importResult.failedRows.length }} 行）</span>
          <el-button type="primary" plain size="small" :icon="Download" @click="downloadFailedRows">
            下载失败行
          </el-button>
        </div>
        <el-table :data="importResult.failedRows" stripe border max-height="320" style="width: 100%">
          <el-table-column prop="rowNum" label="原文件行号" width="100" align="center" />
          <el-table-column prop="name" label="支架名称" min-width="140">
            <template #default="{ row }">{{ row.name ?? '-' }}</template>
          </el-table-column>
          <el-table-column prop="model" label="型号" min-width="120">
            <template #default="{ row }">{{ row.model ?? '-' }}</template>
          </el-table-column>
          <el-table-column prop="length" label="长(mm)" width="100" align="center">
            <template #default="{ row }">{{ row.length ?? '-' }}</template>
          </el-table-column>
          <el-table-column prop="width" label="宽(mm)" width="100" align="center">
            <template #default="{ row }">{{ row.width ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="原因" min-width="220">
            <template #default="{ row }">
              <span :class="row.status === 'DUPLICATE' ? 'reason-dup' : 'reason-err'">
                {{ row.reason }}
              </span>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <div class="action-row">
        <el-button type="primary" :icon="RefreshLeft" @click="resetAll">继续导入下一个文件</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, genFileId, type UploadInstance, type UploadRawFile, type UploadFile, type UploadFiles } from 'element-plus'
import {
  UploadFilled,
  Download,
  Search,
  CircleCheck,
  RefreshLeft,
  InfoFilled,
  Grid,
  Link,
  Close
} from '@element-plus/icons-vue'
import { getBracketStats } from '@/api/bracket'
import {
  isImportFileSupported,
  previewBracketImport,
  confirmBracketImport,
  getBracketImportTemplateUrl
} from '@/api/bracketImport'
import { useModelSuggestions } from '@/composables/useModelSuggestions'
import type { BracketImportPreview, BracketImportResult, BracketImportRow, BracketImportStatus } from '@/types'

// 与后端 BracketImportService 保持一致
const maxRows = 5000
const minDimension = '0.01'
const maxDimension = '10000'
const pageSize = 10

const { refreshModelSuggestions } = useModelSuggestions()

const stats = reactive({ total: 0, bound: 0, unbound: 0 })

const uploadRef = ref<UploadInstance>()
const fileList = ref<UploadFiles>([])
const selectedFile = ref<File | null>(null)
const previewLoading = ref(false)
const importing = ref(false)
const preview = ref<BracketImportPreview | null>(null)
const importResult = ref<BracketImportResult | null>(null)
const statusFilter = ref<'ALL' | BracketImportStatus>('ALL')
const currentPage = ref(1)

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

const handleFileChange = (file: UploadFile) => {
  if (!file.raw) return
  if (!isImportFileSupported(file.raw)) {
    ElMessage.error('仅支持 .csv、.xlsx、.xls 文件')
    fileList.value = []
    selectedFile.value = null
    return
  }
  selectedFile.value = file.raw
  // 更换文件后清空上一次的预览与结果
  preview.value = null
  importResult.value = null
  statusFilter.value = 'ALL'
  currentPage.value = 1
}

const handleFileRemove = () => {
  selectedFile.value = null
  preview.value = null
  importResult.value = null
}

const handleExceed = (files: File[]) => {
  // limit=1：再次选择时替换旧文件
  uploadRef.value?.clearFiles()
  const file = files[0]
  if (file && isImportFileSupported(file)) {
    ;(file as UploadRawFile).uid = genFileId()
    uploadRef.value?.handleStart(file as UploadRawFile)
  } else {
    ElMessage.error('仅支持 .csv、.xlsx、.xls 文件')
  }
}

const handlePreview = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择要导入的文件')
    return
  }
  previewLoading.value = true
  try {
    const res = await previewBracketImport(selectedFile.value)
    if (res.data) {
      preview.value = res.data
      importResult.value = null
      statusFilter.value = 'ALL'
      currentPage.value = 1
      if (res.data.invalidCount > 0 || res.data.duplicateCount > 0) {
        ElMessage.warning(`校验完成：${res.data.validCount} 行可导入，请关注已存在与失败行`)
      } else {
        ElMessage.success(`校验完成：全部 ${res.data.validCount} 行均可导入`)
      }
    }
  } catch (error) {
    console.error('导入校验失败:', error)
  } finally {
    previewLoading.value = false
  }
}

const handleConfirm = async () => {
  if (!selectedFile.value || !preview.value || preview.value.validCount === 0) return
  const { validCount, duplicateCount, invalidCount } = preview.value
  try {
    await ElMessageBox.confirm(
      `确认导入 ${validCount} 行有效数据？` +
        (duplicateCount + invalidCount > 0
          ? `（${duplicateCount} 行型号已存在、${invalidCount} 行校验失败，均不会写入）`
          : ''),
      '导入确认',
      { confirmButtonText: '确认导入', cancelButtonText: '取消', type: 'info' }
    )
  } catch {
    return
  }

  importing.value = true
  try {
    const res = await confirmBracketImport(selectedFile.value)
    if (res.data) {
      importResult.value = res.data
      if (res.data.failedCount === 0 && res.data.skippedCount === 0) {
        ElMessage.success(`导入成功，共新增 ${res.data.successCount} 条支架档案`)
      } else {
        ElMessage.warning(
          `导入完成：成功 ${res.data.successCount}，失败 ${res.data.failedCount}，跳过 ${res.data.skippedCount}`
        )
      }
      // 只写入了通过行，刷新未绑定统计与热门型号建议
      await Promise.all([fetchStats(), refreshModelSuggestions()])
    }
  } catch (error) {
    console.error('导入失败:', error)
  } finally {
    importing.value = false
  }
}

const resultMessage = computed(() => {
  if (!importResult.value) return ''
  const r = importResult.value
  if (r.successCount > 0 && r.failedCount === 0 && r.skippedCount === 0) {
    return `全部导入成功，共新增 ${r.successCount} 条支架档案`
  }
  return `成功新增 ${r.successCount} 条；${r.failedCount} 条校验失败未写入；${r.skippedCount} 条因型号已存在被跳过。可下载失败行修正后重新导入。`
})

const resetAll = () => {
  uploadRef.value?.clearFiles()
  fileList.value = []
  selectedFile.value = null
  preview.value = null
  importResult.value = null
  statusFilter.value = 'ALL'
  currentPage.value = 1
}

const downloadTemplate = () => {
  // 走 /api 代理，由后端返回带 UTF-8 BOM 的标准 CSV 模板
  const link = document.createElement('a')
  link.href = getBracketImportTemplateUrl()
  link.download = 'bracket_import_template.csv'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

const csvEscape = (value: unknown): string => {
  const text = value == null ? '' : String(value)
  if (/[",\n\r]/.test(text)) {
    return `"${text.replace(/"/g, '""')}"`
  }
  return text
}

const triggerCsvDownload = (filename: string, lines: string[][]) => {
  const content = lines.map((line) => line.map(csvEscape).join(',')).join('\r\n')
  // UTF-8 BOM，保证 Excel 直接打开中文不乱码
  const blob = new Blob(['\uFEFF' + content], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

const downloadFailedRows = () => {
  const rows: BracketImportRow[] = importResult.value?.failedRows ?? []
  if (rows.length === 0) return
  const timestamp = new Date().toISOString().slice(0, 10)
  triggerCsvDownload(`支架导入失败行_${timestamp}.csv`, [
    ['支架名称', '支架型号', '长(mm)', '宽(mm)', '未入库原因'],
    ...rows.map((r) => [
      r.name ?? '',
      r.model ?? '',
      r.length == null ? '' : String(r.length),
      r.width == null ? '' : String(r.width),
      r.reason ?? ''
    ])
  ])
}

const filteredRows = computed<BracketImportRow[]>(() => {
  if (!preview.value) return []
  if (statusFilter.value === 'ALL') return preview.value.rows
  return preview.value.rows.filter((r) => r.status === statusFilter.value)
})

const pagedRows = computed<BracketImportRow[]>(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredRows.value.slice(start, start + pageSize)
})

onMounted(() => {
  fetchStats()
})
</script>

<style scoped>
.import-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 20px;
  margin-top: 20px;
}

.import-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 16px;
}

.step-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #1e40af;
  color: #fff;
  font-size: 13px;
}

.upload-row {
  display: flex;
  gap: 20px;
  align-items: stretch;
}

.import-upload {
  flex: 1;
}

.upload-side {
  width: 280px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rule-box {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 12px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.9;
}

.rule-title {
  display: flex;
  align-items: center;
  gap: 4px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 4px;
}

.action-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
}

.no-valid-tip {
  font-size: 13px;
  color: #d97706;
}

.preview-alert {
  margin-bottom: 14px;
}

.cnt-valid {
  color: #16a34a;
}

.cnt-duplicate {
  color: #d97706;
}

.cnt-invalid {
  color: #dc2626;
}

.preview-toolbar {
  margin-bottom: 12px;
}

.reason-dup {
  color: #d97706;
}

.reason-err {
  color: #dc2626;
}

.result-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.result-card {
  background: #fff;
  border-radius: 8px;
  padding: 18px;
  text-align: center;
  border: 1px solid #e2e8f0;
}

.result-card.success {
  border-top: 3px solid #16a34a;
}

.result-card.failed {
  border-top: 3px solid #dc2626;
}

.result-card.skipped {
  border-top: 3px solid #d97706;
}

.result-card.total {
  border-top: 3px solid #1e40af;
}

.result-value {
  font-size: 28px;
  font-weight: 700;
  color: #1e293b;
}

.result-label {
  margin-top: 6px;
  font-size: 13px;
  color: #64748b;
}

.failed-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
  color: #334155;
  margin: 8px 0 10px;
}
</style>
