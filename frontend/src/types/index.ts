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
  minLength?: number | null
  maxLength?: number | null
  minWidth?: number | null
  maxWidth?: number | null
  ruleConfigured?: boolean
  capacityStatus?: 'normal' | 'full' | 'exceeded' | 'unlimited'
  createTime?: string
  updateTime?: string
}

export interface EquipmentRule {
  maxBrackets?: number | null
  allowedModels?: string
  minLength?: number | null
  maxLength?: number | null
  minWidth?: number | null
  maxWidth?: number | null
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
}

export interface BindConfirmResult {
  boundCount: number
  brackets: Bracket[]
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
