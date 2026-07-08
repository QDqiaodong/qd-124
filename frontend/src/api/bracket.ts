import request from '@/utils/request'
import type { ApiResponse, PageResponse, Bracket } from '@/types'

export function getBracketList(params: {
  pageNum: number
  pageSize: number
  name?: string
  model?: string
  bindStatus?: number
}): Promise<ApiResponse<PageResponse<Bracket>>> {
  return request({
    url: '/bracket/list',
    method: 'get',
    params
  })
}

export function getBracket(id: number): Promise<ApiResponse<Bracket>> {
  return request({
    url: `/bracket/${id}`,
    method: 'get'
  })
}

export function createBracket(data: {
  name: string
  model: string
  length: number
  width: number
}): Promise<ApiResponse<Bracket>> {
  return request({
    url: '/bracket',
    method: 'post',
    data
  })
}

export function updateBracket(
  id: number,
  data: {
    name: string
    model: string
    length: number
    width: number
  }
): Promise<ApiResponse<Bracket>> {
  return request({
    url: `/bracket/${id}`,
    method: 'put',
    data
  })
}

export function deleteBracket(id: number): Promise<ApiResponse<void>> {
  return request({
    url: `/bracket/${id}`,
    method: 'delete'
  })
}

export function getBracketModels(): Promise<ApiResponse<string[]>> {
  return request({
    url: '/bracket/models',
    method: 'get'
  })
}

export function getBracketStats(): Promise<ApiResponse<{ total: number; bound: number; unbound: number }>> {
  return request({
    url: '/bracket/stats',
    method: 'get'
  })
}
