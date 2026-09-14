import request from '@/utils/request'
import type {
  ApiResponse,
  PageResponse,
  Equipment,
  Bracket,
  EquipmentRule,
  RuleChangeDiagnosis,
  MoldBatchRecord,
  MoldBatchRegisterRequest
} from '@/types'

export function getEquipmentList(params: {
  pageNum: number
  pageSize: number
  code?: string
  name?: string
  /** 只看挂接数量已超过容量上限的封口机（服务端过滤，刷新/翻页保持一致） */
  onlyExceeded?: boolean
}): Promise<ApiResponse<PageResponse<Equipment>>> {
  return request({
    url: '/equipment/list',
    method: 'get',
    params
  })
}

export function getAllEquipment(): Promise<ApiResponse<Equipment[]>> {
  return request({
    url: '/equipment/all',
    method: 'get'
  })
}

export function getEquipmentBrackets(equipmentId: number): Promise<ApiResponse<Bracket[]>> {
  return request({
    url: `/equipment/${equipmentId}/brackets`,
    method: 'get'
  })
}

export function getUnboundBracketCount(): Promise<ApiResponse<number>> {
  return request({
    url: '/equipment/unbound-count',
    method: 'get'
  })
}

export function updateEquipmentRule(
  equipmentId: number,
  data: EquipmentRule
): Promise<ApiResponse<Equipment>> {
  return request({
    url: `/equipment/${equipmentId}/rule`,
    method: 'put',
    data
  })
}

/**
 * 规则变更影响诊断：不落库，预览候选规则下当前已绑定支架中受影响条目及原因，
 * 区分需要人工处理的存量绑定与仅影响后续绑定的条目。
 */
export function diagnoseEquipmentRule(
  equipmentId: number,
  data: EquipmentRule
): Promise<ApiResponse<RuleChangeDiagnosis>> {
  return request({
    url: `/equipment/${equipmentId}/rule/diagnose`,
    method: 'post',
    data
  })
}

/** 换模后为封口机登记当前模具批次；批次型号必须在该机允许清单内 */
export function registerMoldBatch(
  equipmentId: number,
  data: MoldBatchRegisterRequest
): Promise<ApiResponse<MoldBatchRecord>> {
  return request({
    url: `/equipment/${equipmentId}/mold-batches`,
    method: 'post',
    data
  })
}

/** 按设备翻历史换模记录（最新一条为当前批次） */
export function getMoldBatchHistory(
  equipmentId: number,
  params: { pageNum: number; pageSize: number }
): Promise<ApiResponse<PageResponse<MoldBatchRecord>>> {
  return request({
    url: `/equipment/${equipmentId}/mold-batches`,
    method: 'get',
    params
  })
}

