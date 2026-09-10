import request from '@/utils/request'
import type {
  ApiResponse,
  BatchBindRequest,
  BindCheckResult,
  BindConfirmResult
} from '@/types'

export function bindBracketToEquipment(
  bracketId: number,
  equipmentId: number
): Promise<ApiResponse<BindConfirmResult>> {
  return request({
    url: '/binding/bind',
    method: 'post',
    data: { bracketId, equipmentId }
  })
}

export function unbindBracket(bracketId: number): Promise<ApiResponse<void>> {
  return request({
    url: `/binding/unbind/${bracketId}`,
    method: 'post'
  })
}

export function batchBindBrackets(data: BatchBindRequest): Promise<ApiResponse<BindConfirmResult>> {
  return request({
    url: '/binding/batch-bind',
    method: 'post',
    data
  })
}

/** 单个绑定前配套规则校验 */
export function checkBind(
  bracketId: number,
  equipmentId: number
): Promise<ApiResponse<BindCheckResult>> {
  return request({
    url: '/binding/check',
    method: 'post',
    data: { bracketId, equipmentId }
  })
}

/** 批量绑定前配套规则校验 */
export function checkBatchBind(data: BatchBindRequest): Promise<ApiResponse<BindCheckResult>> {
  return request({
    url: '/binding/batch-check',
    method: 'post',
    data
  })
}

/** 单个绑定确认：仅绑定校验通过项 */
export function confirmBind(
  bracketId: number,
  equipmentId: number
): Promise<ApiResponse<BindConfirmResult>> {
  return request({
    url: '/binding/confirm',
    method: 'post',
    data: { bracketId, equipmentId }
  })
}

/** 批量绑定确认：仅绑定校验通过项 */
export function confirmBatchBind(data: BatchBindRequest): Promise<ApiResponse<BindConfirmResult>> {
  return request({
    url: '/binding/batch-confirm',
    method: 'post',
    data
  })
}
