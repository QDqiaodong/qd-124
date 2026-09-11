import request from '@/utils/request'
import type { ApiResponse, BracketImportPreview, BracketImportResult } from '@/types'

const ALLOWED_EXTENSIONS = ['.csv', '.xlsx', '.xls']

export function isImportFileSupported(file: File): boolean {
  const name = file.name.toLowerCase()
  return ALLOWED_EXTENSIONS.some((ext) => name.endsWith(ext))
}

export function previewBracketImport(file: File): Promise<ApiResponse<BracketImportPreview>> {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/bracket/import/preview',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function confirmBracketImport(file: File): Promise<ApiResponse<BracketImportResult>> {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/bracket/import/confirm',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function getBracketImportTemplateUrl(): string {
  return '/api/bracket/import/template'
}
