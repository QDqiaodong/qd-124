import request from '@/utils/request'
import type {
  ApiResponse,
  PageResponse,
  FirstArticleRecord,
  FirstArticleCreateRequest,
  FirstArticleReleaseRequest,
  FirstArticleReturnRequest,
  FirstArticleStatus
} from '@/types'

/** 开单：为机台当前模具批次登记首件尺寸确认单，量差/超线由后端计算落库 */
export function createFirstArticle(
  data: FirstArticleCreateRequest
): Promise<ApiResponse<FirstArticleRecord>> {
  return request({
    url: '/first-articles',
    method: 'post',
    data
  })
}

/** 列表：按机台与放行结果组合筛选 */
export function getFirstArticleList(params: {
  pageNum: number
  pageSize: number
  equipmentId?: number
  status?: FirstArticleStatus
}): Promise<ApiResponse<PageResponse<FirstArticleRecord>>> {
  return request({
    url: '/first-articles',
    method: 'get',
    params
  })
}

/** 详情：点开查看签字人与签字时间 */
export function getFirstArticleDetail(id: number): Promise<ApiResponse<FirstArticleRecord>> {
  return request({
    url: `/first-articles/${id}`,
    method: 'get'
  })
}

/** 签放：量差未超线的待签放单才可放行，超线单后端硬拦截 */
export function releaseFirstArticle(
  id: number,
  data: FirstArticleReleaseRequest
): Promise<ApiResponse<FirstArticleRecord>> {
  return request({
    url: `/first-articles/${id}/release`,
    method: 'post',
    data
  })
}

/** 退回再量：待签放单退回，重新测量需另开新单 */
export function returnFirstArticle(
  id: number,
  data: FirstArticleReturnRequest
): Promise<ApiResponse<FirstArticleRecord>> {
  return request({
    url: `/first-articles/${id}/return`,
    method: 'post',
    data
  })
}
