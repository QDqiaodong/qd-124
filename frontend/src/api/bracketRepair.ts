import request from '@/utils/request'
import type {
  ApiResponse,
  PageResponse,
  BracketRepairCreateRequest,
  BracketRepairRecord,
  BracketRepairReturnRequest
} from '@/types'

/** 送修登记：解绑后的支架标记为返修，生成返修单（返修中） */
export function createBracketRepair(
  bracketId: number,
  data: BracketRepairCreateRequest
): Promise<ApiResponse<BracketRepairRecord>> {
  return request({
    url: `/bracket/${bracketId}/repairs`,
    method: 'post',
    data
  })
}

/** 回库登记：为返修单写下回库结论与检验人（二者必填） */
export function returnBracketRepair(
  bracketId: number,
  recordId: number,
  data: BracketRepairReturnRequest
): Promise<ApiResponse<BracketRepairRecord>> {
  return request({
    url: `/bracket/${bracketId}/repairs/${recordId}/return`,
    method: 'put',
    data
  })
}

/** 按支架翻返修单（送修时间新的在前，第一条为当前返修单） */
export function getBracketRepairHistory(
  bracketId: number,
  params: { pageNum: number; pageSize: number }
): Promise<ApiResponse<PageResponse<BracketRepairRecord>>> {
  return request({
    url: `/bracket/${bracketId}/repairs`,
    method: 'get',
    params
  })
}
