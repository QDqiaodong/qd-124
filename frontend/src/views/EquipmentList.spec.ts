import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { nextTick } from 'vue'
import EquipmentList from '@/views/EquipmentList.vue'
import {
  getEquipmentList,
  getEquipmentBrackets,
  getUnboundBracketCount,
  updateEquipmentRule
} from '@/api/equipment'
import { getBracketList, getBracketModels } from '@/api/bracket'

vi.mock('@/api/equipment', () => ({
  getEquipmentList: vi.fn(),
  getEquipmentBrackets: vi.fn(),
  getUnboundBracketCount: vi.fn(),
  updateEquipmentRule: vi.fn()
}))

vi.mock('@/api/bracket', () => ({
  getBracketList: vi.fn(),
  getBracketModels: vi.fn()
}))

vi.mock('@/api/binding', () => ({
  unbindBracket: vi.fn(),
  checkBind: vi.fn(),
  confirmBind: vi.fn()
}))

const flush = async () => {
  await flushPromises()
  await new Promise((resolve) => setTimeout(resolve, 10))
  await nextTick()
}

const equipment = {
  id: 1,
  code: 'EQ-001',
  name: '1号封口机',
  maxBrackets: 2,
  allowedModels: ['A-01'],
  minLength: null,
  maxLength: null,
  minWidth: null,
  maxWidth: null,
  ruleConfigured: true,
  bracketCount: 0,
  capacityStatus: 'normal'
}

describe('EquipmentList 配套规则弹窗的热门型号建议', () => {
  // jsdom 缺少 ResizeObserver，el-table 布局需要
  ;(globalThis as any).ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getUnboundBracketCount).mockResolvedValue({ code: 200, message: 'ok', data: 0 })
    vi.mocked(getEquipmentList).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { list: [equipment], total: 1, pageNum: 1, pageSize: 6 }
    } as any)
    vi.mocked(getEquipmentBrackets).mockResolvedValue({ code: 200, message: 'ok', data: [] })
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('打开配套规则弹窗时通过热门型号接口获取建议，且不含已删除/改名的旧型号', async () => {
    // 后端在支架删除/改名后失效缓存，接口只返回当前存在的型号
    vi.mocked(getBracketModels).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: ['A-01', 'NEW-MODEL']
    })

    const wrapper = mount(EquipmentList, {
      global: { plugins: [ElementPlus] },
      attachTo: document.body
    })
    await flush()

    ;(wrapper.vm as any).openRuleDialog(equipment)
    await flush()

    expect(getBracketModels).toHaveBeenCalledTimes(1)
    // 不再用分页列表拼凑型号建议
    expect(getBracketList).not.toHaveBeenCalled()
    expect((wrapper.vm as any).modelOptions).toEqual(['A-01', 'NEW-MODEL'])
    expect((wrapper.vm as any).modelOptions).not.toContain('OLD-MODEL')
  })

  it('热门型号接口异常时不阻塞弹窗打开', async () => {
    vi.mocked(getBracketModels).mockRejectedValue(new Error('网络错误'))

    const wrapper = mount(EquipmentList, {
      global: { plugins: [ElementPlus] },
      attachTo: document.body
    })
    await flush()

    ;(wrapper.vm as any).openRuleDialog(equipment)
    await flush()

    expect((wrapper.vm as any).ruleDialogVisible).toBe(true)
    expect(updateEquipmentRule).not.toHaveBeenCalled()
  })
})
