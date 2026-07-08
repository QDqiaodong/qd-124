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
  createTime?: string
  updateTime?: string
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
