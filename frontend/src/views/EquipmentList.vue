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
            <div class="equipment-card-meta">
              <el-tag
                :type="ruleTagType(eq)"
                effect="light"
                size="small"
              >
                {{ eq.ruleConfigured ? '规则已配置' : '未配置规则' }}
              </el-tag>
              <el-tag
                :type="capacityTagType(eq)"
                effect="plain"
                size="small"
              >
                容量 {{ eq.bracketCount || 0 }}<template v-if="eq.maxBrackets != null"> / {{ eq.maxBrackets }}</template><template v-else> / ∞</template>
              </el-tag>
              <el-tag
                v-if="eq.capacityStatus === 'exceeded'"
                type="danger"
                effect="dark"
                size="small"
              >超出容量</el-tag>
              <el-tag
                v-else-if="eq.capacityStatus === 'full'"
                type="warning"
                effect="dark"
                size="small"
              >已满</el-tag>
              <el-button
                size="small"
                type="primary"
                plain
                @click.stop="openRuleDialog(eq)"
              >
                <el-icon><Setting /></el-icon>
                <span style="margin-left: 4px">配套规则</span>
              </el-button>
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
            <div class="rule-overview" v-if="eq.ruleConfigured">
              <span class="rule-overview-label">配套规则：</span>
              <el-tag size="small" type="info" effect="plain">
                最大支架数量：{{ eq.maxBrackets != null ? eq.maxBrackets + ' 个' : '不限' }}
              </el-tag>
              <el-tag size="small" type="info" effect="plain">
                允许型号：{{ eq.allowedModels && eq.allowedModels.length ? eq.allowedModels.join('、') : '不限' }}
              </el-tag>
              <el-tag size="small" type="info" effect="plain">
                长度：{{ formatRange(eq.minLength, eq.maxLength) }}
              </el-tag>
              <el-tag size="small" type="info" effect="plain">
                宽度：{{ formatRange(eq.minWidth, eq.maxWidth) }}
              </el-tag>
            </div>
            <div class="rule-overview" v-else>
              <span class="rule-overview-label">配套规则：</span>
              <el-tag size="small" type="warning" effect="plain">未配置，绑定时不做限制</el-tag>
            </div>

            <div
              v-loading="loadingBracketId === eq.id"
              style="min-height: 80px"
            >
              <div style="display: flex; justify-content: flex-end; gap: 8px; margin-bottom: 8px">
                <el-button size="small" type="success" plain @click="openBindDialog(eq)">
                  <el-icon><Link /></el-icon>
                  <span style="margin-left: 4px">绑定未配套支架</span>
                </el-button>
                <el-button size="small" type="primary" plain @click="goRehang(eq)">
                  <el-icon><Switch /></el-icon>
                  <span style="margin-left: 4px">改挂本设备支架</span>
                </el-button>
              </div>
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
                      <el-icon><Close /></el-icon>
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

    <!-- 配套规则配置弹窗 -->
    <el-dialog
      v-model="ruleDialogVisible"
      title="设备配套规则配置"
      width="720px"
      destroy-on-close
      @close="resetDiagnosisState"
    >
      <!-- 第一步：规则表单 -->
      <template v-if="!diagnosisVisible">
        <el-alert
          title="保存前可预览变更对存量绑定的影响；新规则只约束后续绑定，不会自动解除任何已有绑定"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 16px"
        />
        <el-form
          ref="ruleFormRef"
          :model="ruleForm"
          :rules="ruleFormRules"
          label-width="120px"
        >
          <el-form-item label="最大支架数量" prop="maxBrackets">
            <el-input-number
              v-model="ruleForm.maxBrackets"
              :min="0"
              :precision="0"
              placeholder="留空不限制"
              style="width: 100%"
            />
            <div class="form-tip">不填或清空表示不限制容量</div>
          </el-form-item>
          <el-form-item label="允许型号" prop="allowedModels">
            <el-select
              v-model="ruleModelList"
              multiple
              filterable
              allow-create
              default-first-option
              placeholder="不选择表示允许所有型号"
              style="width: 100%"
            >
              <el-option
                v-for="model in modelOptions"
                :key="model"
                :label="model"
                :value="model"
              />
            </el-select>
            <div class="form-tip">可从已有型号中选择，也可直接输入新型号，多个型号取其一即可</div>
          </el-form-item>
          <el-form-item label="长度范围(mm)">
            <div class="range-row">
              <el-form-item prop="minLength" style="margin-bottom: 0; flex: 1">
                <el-input-number
                  v-model="ruleForm.minLength"
                  :min="0"
                  :precision="2"
                  placeholder="最小长度"
                  style="width: 100%"
                />
              </el-form-item>
              <span class="range-sep">~</span>
              <el-form-item prop="maxLength" style="margin-bottom: 0; flex: 1">
                <el-input-number
                  v-model="ruleForm.maxLength"
                  :min="0"
                  :precision="2"
                  placeholder="最大长度"
                  style="width: 100%"
                />
              </el-form-item>
            </div>
            <div class="form-tip">留空表示该侧不限制</div>
          </el-form-item>
          <el-form-item label="宽度范围(mm)">
            <div class="range-row">
              <el-form-item prop="minWidth" style="margin-bottom: 0; flex: 1">
                <el-input-number
                  v-model="ruleForm.minWidth"
                  :min="0"
                  :precision="2"
                  placeholder="最小宽度"
                  style="width: 100%"
                />
              </el-form-item>
              <span class="range-sep">~</span>
              <el-form-item prop="maxWidth" style="margin-bottom: 0; flex: 1">
                <el-input-number
                  v-model="ruleForm.maxWidth"
                  :min="0"
                  :precision="2"
                  placeholder="最大宽度"
                  style="width: 100%"
                />
              </el-form-item>
            </div>
            <div class="form-tip">留空表示该侧不限制</div>
          </el-form-item>
        </el-form>
      </template>

      <!-- 第二步：变更影响诊断 -->
      <div v-else v-loading="diagnosisLoading">
        <template v-if="diagnosis">
          <el-alert
            v-if="diagnosis.noImpact"
            title="候选规则与当前配置一致，当前已绑定支架不受任何影响"
            type="success"
            :closable="false"
            show-icon
            style="margin-bottom: 12px"
          />
          <el-alert
            v-else-if="diagnosis.manualCount > 0"
            :closable="false"
            show-icon
            type="warning"
            style="margin-bottom: 12px"
          >
            <template #title>
              检测到 {{ diagnosis.manualCount }} 个已绑定支架需要人工处理
              （型号/尺寸不再合规 {{ diagnosis.existingViolations.length }} 个、
              超出新容量 {{ diagnosis.capacityImpacts.length }} 个）。
              保存后规则立即对后续绑定生效，但系统不会自动解绑，请人工解绑或调整规则。
            </template>
          </el-alert>
          <el-alert
            v-else
            :title="`规则变更仅影响后续绑定：当前 ${diagnosis.currentCount} 个已绑定支架在新规则下全部合规，无需人工处理`"
            type="success"
            :closable="false"
            show-icon
            style="margin-bottom: 12px"
          />

          <div class="diagnosis-summary">
            <span class="diagnosis-summary-item">
              当前绑定 <b>{{ diagnosis.currentCount }}</b> 个
            </span>
            <span class="diagnosis-summary-item">
              新容量上限
              <b><template v-if="diagnosis.maxBrackets != null">{{ diagnosis.maxBrackets }} 个</template><template v-else>不限</template></b>
            </span>
            <span class="diagnosis-summary-item">
              需人工处理 <b :class="diagnosis.manualCount > 0 ? 'impact-danger' : ''">{{ diagnosis.manualCount }}</b> 个
            </span>
            <span class="diagnosis-summary-item">
              仅影响后续绑定 <b>{{ diagnosis.futureOnlyItems.length }}</b> 个
            </span>
          </div>

          <div v-if="manualImpactItems.length" class="impact-block">
            <div class="impact-block-title impact-danger">需要人工处理的存量绑定（{{ manualImpactItems.length }}）</div>
            <el-table :data="manualImpactItems" size="small" border max-height="240">
              <el-table-column prop="bracketName" label="支架名称" min-width="120">
                <template #default="{ row }">{{ row.bracketName || `#${row.bracketId}` }}</template>
              </el-table-column>
              <el-table-column prop="model" label="型号" min-width="100" />
              <el-table-column label="尺寸(长×宽)" width="140" align="center">
                <template #default="{ row }">
                  <template v-if="row.length != null && row.width != null">{{ row.length }} × {{ row.width }} mm</template>
                  <span v-else style="color: #94a3b8">-</span>
                </template>
              </el-table-column>
              <el-table-column label="影响类型" width="110" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.impactType === 'existing_violation'" type="danger" effect="dark" size="small">不合规</el-tag>
                  <el-tag v-else type="warning" effect="dark" size="small">容量超额</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="原因" min-width="220">
                <template #default="{ row }">
                  <span style="color: #dc2626">{{ row.reasons.join('；') }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div v-if="diagnosis.futureOnlyItems.length" class="impact-block">
            <div class="impact-block-title">仅影响后续绑定的已有支架（{{ diagnosis.futureOnlyItems.length }}）</div>
            <el-table :data="diagnosis.futureOnlyItems" size="small" border max-height="200">
              <el-table-column prop="bracketName" label="支架名称" min-width="120">
                <template #default="{ row }">{{ row.bracketName || `#${row.bracketId}` }}</template>
              </el-table-column>
              <el-table-column prop="model" label="型号" min-width="100" />
              <el-table-column label="尺寸(长×宽)" width="140" align="center">
                <template #default="{ row }">
                  {{ row.length }} × {{ row.width }} mm
                </template>
              </el-table-column>
              <el-table-column label="影响类型" width="110" align="center">
                <template #default>
                  <el-tag type="info" effect="plain" size="small">仅后续生效</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="说明" min-width="180">
                <template #default>
                  <span style="color: #16a34a">新规则下仍合规，存量绑定保持不变</span>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <el-empty
            v-if="diagnosis.currentCount === 0"
            description="当前设备暂无已绑定支架，规则变更只影响后续绑定"
            :image-size="70"
          />
        </template>
      </div>

      <template #footer>
        <template v-if="!diagnosisVisible">
          <el-button @click="ruleDialogVisible = false">取消</el-button>
          <el-button :loading="ruleSubmitting" @click="handleClearRule">清空规则</el-button>
          <el-button type="primary" :loading="diagnosisLoading" @click="handlePreviewImpact">
            预览变更影响
          </el-button>
        </template>
        <template v-else>
          <el-button @click="ruleDialogVisible = false">取消</el-button>
          <el-button :disabled="diagnosisLoading" @click="backToRuleForm">返回修改</el-button>
          <el-button
            type="primary"
            :loading="ruleSubmitting"
            :disabled="!diagnosis?.ruleChanged"
            @click="handleConfirmSave"
          >
            确认保存
          </el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 选择未绑定支架 -->
    <el-dialog
      v-model="bindDialogVisible"
      title="绑定未配套支架"
      width="640px"
      destroy-on-close
    >
      <el-table
        :data="unboundBrackets"
        v-loading="unboundLoading"
        size="small"
        border
        max-height="380"
      >
        <el-table-column prop="name" label="支架名称" min-width="120" />
        <el-table-column prop="model" label="型号" min-width="110" />
        <el-table-column label="尺寸(长×宽)" width="150" align="center">
          <template #default="{ row }">
            {{ row.length }} × {{ row.width }} mm
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" link :loading="checkingBracketId === row.id" @click="handleSingleCheck(row)">
              选择
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-if="!unboundLoading && unboundBrackets.length === 0"
        description="没有未配套的支架"
        :image-size="70"
      />
      <template #footer>
        <el-button @click="bindDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <BindCheckDialog
      v-model="checkDialogVisible"
      :result="checkResult"
      :confirm-loading="confirmLoading"
      @confirm="handleCheckConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  Search,
  Refresh,
  Monitor,
  Grid,
  ArrowDown,
  Warning,
  Setting,
  Link,
  Close,
  Switch
} from '@element-plus/icons-vue'
import {
  getEquipmentList,
  getEquipmentBrackets,
  getUnboundBracketCount,
  updateEquipmentRule,
  diagnoseEquipmentRule
} from '@/api/equipment'
import { getBracketList, getBracketModels } from '@/api/bracket'
import { unbindBracket, checkBind, confirmBind } from '@/api/binding'
import type { Equipment, Bracket, EquipmentRule, BindCheckResult, RuleChangeDiagnosis, RuleImpactItem } from '@/types'
import BindCheckDialog from '@/components/BindCheckDialog.vue'

const router = useRouter()

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

const ruleTagType = (eq: Equipment) => {
  return eq.ruleConfigured ? 'success' : 'info'
}

const capacityTagType = (eq: Equipment) => {
  if (eq.capacityStatus === 'exceeded') return 'danger'
  if (eq.capacityStatus === 'full') return 'warning'
  return 'primary'
}

const formatRange = (min?: number | null, max?: number | null) => {
  const minText = min != null ? min : '不限'
  const maxText = max != null ? max : '不限'
  return `${minText} ~ ${maxText} mm`
}

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

/** 换线改挂：携带源设备进入改挂页，源设备默认选中 */
const goRehang = (eq: Equipment) => {
  router.push({ path: '/rehang', query: { sourceId: String(eq.id) } })
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
        equipmentBracketsMap.value.delete(eq.id)
        fetchEquipmentBrackets(eq.id)
        fetchEquipmentList()
        fetchUnboundCount()
      } catch (error) {
        console.error('解绑失败:', error)
      }
    })
    .catch(() => {})
}

// ============ 配套规则配置 ============
const ruleDialogVisible = ref(false)
const ruleSubmitting = ref(false)
const currentEquipment = ref<Equipment | null>(null)
const ruleFormRef = ref<FormInstance>()
const modelOptions = ref<string[]>([])
const ruleModelList = ref<string[]>([])
const ruleForm = reactive<EquipmentRule>({
  maxBrackets: null,
  allowedModels: '',
  minLength: null,
  maxLength: null,
  minWidth: null,
  maxWidth: null
})

const ruleFormRules: FormRules = {
  maxBrackets: [
    {
      validator: (_rule, value, callback) => {
        if (value != null && value < 0) {
          callback(new Error('最大支架数量不能为负数'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  minLength: [
    {
      validator: (_rule, _value, callback) => {
        if (ruleForm.minLength != null && ruleForm.maxLength != null && ruleForm.minLength > ruleForm.maxLength) {
          callback(new Error('长度下限不能大于上限'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  maxLength: [
    {
      validator: (_rule, _value, callback) => {
        if (ruleForm.minLength != null && ruleForm.maxLength != null && ruleForm.minLength > ruleForm.maxLength) {
          callback(new Error('长度上限不能小于下限'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  minWidth: [
    {
      validator: (_rule, _value, callback) => {
        if (ruleForm.minWidth != null && ruleForm.maxWidth != null && ruleForm.minWidth > ruleForm.maxWidth) {
          callback(new Error('宽度下限不能大于上限'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  maxWidth: [
    {
      validator: (_rule, _value, callback) => {
        if (ruleForm.minWidth != null && ruleForm.maxWidth != null && ruleForm.minWidth > ruleForm.maxWidth) {
          callback(new Error('宽度上限不能小于下限'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
}

const fetchModelOptions = async () => {
  try {
    // 使用热门型号接口（后端按支架档案实时维护，删除/改名后不会残留旧型号）
    const res = await getBracketModels()
    modelOptions.value = res.data || []
  } catch (error) {
    console.error('获取型号列表失败:', error)
  }
}

const openRuleDialog = (eq: Equipment) => {
  currentEquipment.value = eq
  ruleForm.maxBrackets = eq.maxBrackets ?? null
  ruleModelList.value = [...(eq.allowedModels || [])]
  ruleForm.minLength = eq.minLength ?? null
  ruleForm.maxLength = eq.maxLength ?? null
  ruleForm.minWidth = eq.minWidth ?? null
  ruleForm.maxWidth = eq.maxWidth ?? null
  resetDiagnosisState()
  fetchModelOptions()
  ruleDialogVisible.value = true
}

const buildRulePayload = (): EquipmentRule => ({
  maxBrackets: ruleForm.maxBrackets ?? null,
  allowedModels: ruleModelList.value.join(','),
  minLength: ruleForm.minLength ?? null,
  maxLength: ruleForm.maxLength ?? null,
  minWidth: ruleForm.minWidth ?? null,
  maxWidth: ruleForm.maxWidth ?? null
})

// ============ 规则变更影响诊断（预览不落库） ============
const diagnosisVisible = ref(false)
const diagnosisLoading = ref(false)
const diagnosis = ref<RuleChangeDiagnosis | null>(null)
/** 需要人工处理的存量绑定：型号/尺寸不合规 + 容量超额，按后端分类顺序合并 */
const manualImpactItems = computed<RuleImpactItem[]>(() => {
  if (!diagnosis.value) return []
  return [...diagnosis.value.existingViolations, ...diagnosis.value.capacityImpacts]
})

const resetDiagnosisState = () => {
  diagnosisVisible.value = false
  diagnosis.value = null
  diagnosisLoading.value = false
}

const backToRuleForm = () => {
  diagnosisVisible.value = false
}

/**
 * 表单校验通过后调用诊断接口：纯预览、不写库。
 */
const runDiagnosis = async (): Promise<boolean> => {
  if (!currentEquipment.value) return false
  diagnosisLoading.value = true
  try {
    const res = await diagnoseEquipmentRule(currentEquipment.value.id, buildRulePayload())
    diagnosis.value = res.data
    diagnosisVisible.value = true
    return true
  } catch (error) {
    console.error('规则变更影响诊断失败:', error)
    return false
  } finally {
    diagnosisLoading.value = false
  }
}

const handlePreviewImpact = () => {
  if (!ruleFormRef.value || !currentEquipment.value) return
  ruleFormRef.value.validate(async (valid) => {
    if (!valid) return
    await runDiagnosis()
  })
}

/** 确认保存：以诊断时的候选规则落库；保存后刷新设备与支架数据，保证三处一致 */
const handleConfirmSave = async () => {
  if (!currentEquipment.value || !diagnosis.value?.ruleChanged) return
  const equipmentId = currentEquipment.value.id
  ruleSubmitting.value = true
  try {
    await updateEquipmentRule(equipmentId, buildRulePayload())
    ElMessage.success('配套规则已保存，立即生效于后续绑定；需人工处理的存量绑定请尽快处理')
    ruleDialogVisible.value = false
    resetDiagnosisState()
    equipmentBracketsMap.value.delete(equipmentId)
    if (expandedIds.value.has(equipmentId)) {
      fetchEquipmentBrackets(equipmentId)
    }
    fetchEquipmentList()
  } catch (error) {
    console.error('保存规则失败:', error)
  } finally {
    ruleSubmitting.value = false
  }
}

const handleClearRule = () => {
  ElMessageBox.confirm('确定清空该设备的全部配套规则吗？清空后绑定将不做限制，可在保存前预览影响。', '清空规则确认', {
    confirmButtonText: '确定清空',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(async () => {
      if (!currentEquipment.value) return
      ruleForm.maxBrackets = null
      ruleModelList.value = []
      ruleForm.minLength = null
      ruleForm.maxLength = null
      ruleForm.minWidth = null
      ruleForm.maxWidth = null
      // 与正常修改走同一条「预览影响 → 确认保存」路径，不直接落库
      await runDiagnosis()
    })
    .catch(() => {})
}

// ============ 选择未配套支架并校验绑定 ============
const bindDialogVisible = ref(false)
const unboundLoading = ref(false)
const unboundBrackets = ref<Bracket[]>([])
const checkingBracketId = ref<number | null>(null)

const checkDialogVisible = ref(false)
const confirmLoading = ref(false)
const checkResult = ref<BindCheckResult | null>(null)

const openBindDialog = (eq: Equipment) => {
  currentEquipment.value = eq
  checkResult.value = null
  bindDialogVisible.value = true
  unboundBrackets.value = []
  unboundLoading.value = true
  getBracketList({ pageNum: 1, pageSize: 200, bindStatus: 0 })
    .then((res) => {
      unboundBrackets.value = res.data?.list || []
    })
    .catch((error) => {
      console.error('获取未绑定支架失败:', error)
    })
    .finally(() => {
      unboundLoading.value = false
    })
}

const handleSingleCheck = async (bracket: Bracket) => {
  if (!currentEquipment.value) return
  checkingBracketId.value = bracket.id
  try {
    const res = await checkBind(bracket.id, currentEquipment.value.id)
    checkResult.value = res.data
    bindDialogVisible.value = false
    checkDialogVisible.value = true
  } catch (error) {
    console.error('绑定校验失败:', error)
  } finally {
    checkingBracketId.value = null
  }
}

const handleCheckConfirm = async () => {
  if (!checkResult.value) return
  const equipmentId = checkResult.value.equipmentId
  const bracketId = checkResult.value.passedItems[0]?.bracketId
  if (!bracketId) return
  confirmLoading.value = true
  try {
    await confirmBind(bracketId, equipmentId)
    ElMessage.success('绑定成功')
    checkDialogVisible.value = false
    if (expandedIds.value.has(equipmentId)) {
      equipmentBracketsMap.value.delete(equipmentId)
      fetchEquipmentBrackets(equipmentId)
    }
    fetchEquipmentList()
    fetchUnboundCount()
  } catch (error) {
    console.error('绑定失败:', error)
  } finally {
    confirmLoading.value = false
  }
}

onMounted(() => {
  fetchUnboundCount()
  fetchEquipmentList()
})
</script>

<style scoped>
.equipment-card-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.rule-overview {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 12px;
  margin-bottom: 12px;
  background: #f8fafc;
  border-radius: 6px;
}

.rule-overview-label {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.form-tip {
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.4;
  margin-top: 4px;
}

.range-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.range-sep {
  color: #94a3b8;
}

.diagnosis-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  padding: 12px 16px;
  margin-bottom: 12px;
  background: #f8fafc;
  border-radius: 6px;
  font-size: 13px;
  color: #475569;
}

.diagnosis-summary-item b {
  margin-left: 4px;
  font-size: 15px;
  color: #1e293b;
}

.impact-danger {
  color: #dc2626 !important;
}

.impact-block {
  margin-bottom: 14px;
}

.impact-block-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 6px;
}
</style>
