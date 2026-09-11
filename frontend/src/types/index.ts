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
