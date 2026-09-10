import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useModelSuggestions } from '@/composables/useModelSuggestions'
import { getBracketModels } from '@/api/bracket'

vi.mock('@/api/bracket', () => ({
  getBracketModels: vi.fn()
}))

describe('useModelSuggestions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('初次刷新后返回热门型号建议', async () => {
    vi.mocked(getBracketModels).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: ['A-01', 'B-02']
    })

    const { modelSuggestions, refreshModelSuggestions } = useModelSuggestions()
    expect(modelSuggestions.value).toEqual([])

    await refreshModelSuggestions()

    expect(getBracketModels).toHaveBeenCalledTimes(1)
    expect(modelSuggestions.value).toEqual(['A-01', 'B-02'])
  })

  it('再次刷新可拿到最新数据（旧型号删除/改名后不残留）', async () => {
    vi.mocked(getBracketModels)
      .mockResolvedValueOnce({ code: 200, message: 'ok', data: ['OLD-MODEL'] })
      .mockResolvedValueOnce({ code: 200, message: 'ok', data: ['NEW-MODEL'] })

    const { modelSuggestions, refreshModelSuggestions } = useModelSuggestions()
    await refreshModelSuggestions()
    expect(modelSuggestions.value).toEqual(['OLD-MODEL'])

    // 模拟后端在支架改名/删除并失效缓存后的下一次查询
    await refreshModelSuggestions()
    expect(modelSuggestions.value).toEqual(['NEW-MODEL'])
    expect(modelSuggestions.value).not.toContain('OLD-MODEL')
  })

  it('接口异常时保留原有建议且不抛出错误', async () => {
    vi.mocked(getBracketModels)
      .mockResolvedValueOnce({ code: 200, message: 'ok', data: ['A-01'] })
      .mockRejectedValueOnce(new Error('网络错误'))

    const { modelSuggestions, refreshModelSuggestions } = useModelSuggestions()
    await refreshModelSuggestions()

    await expect(refreshModelSuggestions()).resolves.toBeUndefined()
    expect(modelSuggestions.value).toEqual(['A-01'])
  })
})
