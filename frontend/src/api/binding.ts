import request from '@/utils/request'
import type { ApiResponse, BatchBindRequest } from '@/types'

export function bindBracketToEquipment(
  bracketId: number,
  equipmentId: number
): Promise<ApiResponse<void>> {
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

export function batchBindBrackets(data: BatchBindRequest): Promise<ApiResponse<void>> {
  return request({
    url: '/binding/batch-bind',
    method: 'post',
    data
  })
}
