export interface Bracket {
  id: number
  name: string
  model: string
  length: number
  width: number
  equipmentId?: number | null
  equipmentName?: string | null
  createTime?: string
  updateTime?: string
}

export interface Equipment {
  id: number
  code: string
  name: string
  bracketCount?: number
  maxBrackets?: number | null
  allowedModels?: string[]
  /** 允许在本机登记使用的模具型号清单 */
  allowedMoldModels?: string[]
  minLength?: number | null
  maxLength?: number | null
  minWidth?: number | null
  maxWidth?: number | null
  ruleConfigured?: boolean
  capacityStatus?: 'normal' | 'full' | 'exceeded' | 'unlimited'
  /** 当前模具批次 ID（最新一次换模登记），未登记时为空 */
  currentBatchId?: number | null
  /** 当前模具批次号，未登记时为空 */
  currentBatchNo?: string | null
  /** 当前模具型号，未登记时为空 */
  currentMoldModel?: string | null
  /** 当前批次换模时间 */
  currentBatchChangeTime?: string | null
  /** 换模批次放行是否就绪：已登记当前批次且型号在允许清单内 */
  moldBatchReady?: boolean
  createTime?: string
  updateTime?: string
}

export interface EquipmentRule {
  maxBrackets?: number | null
  allowedModels?: string
  /** 允许模具型号，逗号分隔；换模登记的批次模具型号必须在此清单内 */
  allowedMoldModels?: string
  minLength?: number | null
  maxLength?: number | null
  minWidth?: number | null
  maxWidth?: number | null
}

/** 换模批次登记请求 */
export interface MoldBatchRegisterRequest {
  batchNo: string
  moldModel: string
  changeTime?: string | null
  operator?: string | null
  remark?: string | null
}

/** 换模批次记录；current=true 的最新一条为当前批次，也是唯一放行依据 */
export interface MoldBatchRecord {
  id: number
  equipmentId: number
  equipmentCode?: string
  equipmentName?: string
  batchNo: string
  moldModel: string
  changeTime: string
  operator?: string | null
  remark?: string | null
  createTime?: string
  current: boolean
}

/** 换模批次放行闸门：未写批次/型号不在允许清单时 passed=false，整单拦截 */
export interface MoldBatchGate {
  passed: boolean
  reason: string | null
  currentBatchId?: number | null
  currentBatchNo?: string | null
  currentMoldModel?: string | null
}

/** 规则变更对单条已绑定支架的影响类型 */
export type RuleImpactType = 'existing_violation' | 'capacity_only' | 'future_only'

export interface RuleImpactItem {
  bracketId: number
  bracketName: string
  model: string
  length: number | null
  width: number | null
  /** existing_violation 存量不再合规需人工处理 / capacity_only 容量超额需人工处理 / future_only 仅影响后续绑定 */
  impactType: RuleImpactType
  /** 具体原因，仅影响后续绑定时为空 */
  reasons: string[]
}

export interface RuleChangeDiagnosis {
  equipmentId: number
  equipmentCode: string
  equipmentName: string
  currentCount: number
  maxBrackets: number | null
  /** 候选规则与当前已保存规则是否存在差异 */
  ruleChanged: boolean
  /** 型号/尺寸不再合规、需要人工处理的存量绑定 */
  existingViolations: RuleImpactItem[]
  /** 型号/尺寸仍合规但超出新容量上限、需要人工处理的存量绑定 */
  capacityImpacts: RuleImpactItem[]
  /** 仍符合新规则、仅后续绑定受限的已绑定支架 */
  futureOnlyItems: RuleImpactItem[]
  capacityExceededCount: number
  /** 需要人工处理的总数 */
  manualCount: number
  /** 规则无变化且无存量受影响时为 true */
  noImpact: boolean
}

export interface BindCheckItem {
  bracketId: number
  bracketName: string
  model: string
  length: number | null
  width: number | null
  passed: boolean
  reason: string | null
}

export interface BindCheckResult {
  equipmentId: number
  equipmentCode: string
  equipmentName: string
  currentCount: number
  maxBrackets: number | null
  availableSlots: number | null
  items: BindCheckItem[]
  passedItems: BindCheckItem[]
  conflicts: BindCheckItem[]
  /** 换模批次放行闸门；批量挂接/换线改挂不通过时整单拦截 */
  moldBatchGate?: MoldBatchGate
}

export interface BindConfirmResult {
  boundCount: number
  brackets: Bracket[]
}

/** 换线改挂预检结果：容量字段均针对目标封口机，另带来源设备信息 */
export interface RehangCheckResult extends BindCheckResult {
  sourceEquipmentId: number
  sourceEquipmentCode: string | null
  sourceEquipmentName: string | null
}

export interface RehangConfirmResult {
  /** 一次性改挂成功的通过项数量 */
  rehungCount: number
  rehung: Bracket[]
  /** 未通过预检、仍留在源设备上的冲突项 */
  conflicts: BindCheckItem[]
}

export interface RehangRequest {
  sourceEquipmentId: number
  targetEquipmentId: number
  bracketIds: number[]
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface PageResponse<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface BatchBindRequest {
  bracketIds: number[]
  equipmentId: number
}

export type BracketImportStatus = 'VALID' | 'DUPLICATE' | 'INVALID'

export interface BracketImportRow {
  rowNum: number
  name: string | null
  model: string | null
  length: number | null
  width: number | null
  status: BracketImportStatus
  reason: string | null
}

export interface BracketImportPreview {
  totalCount: number
  validCount: number
  duplicateCount: number
  invalidCount: number
  rows: BracketImportRow[]
}

export interface BracketImportResult {
  totalCount: number
  successCount: number
  failedCount: number
  skippedCount: number
  failedRows: BracketImportRow[]
}
