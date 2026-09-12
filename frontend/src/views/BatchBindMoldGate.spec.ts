import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { nextTick } from 'vue'
import BatchBind from '@/views/BatchBind.vue'
import { getBracketList } from '@/api/bracket'
import { getAllEquipment } from '@/api/equipment'
import { checkBatchBind } from '@/api/binding'

vi.mock('@/api/bracket', () => ({ getBracketList: vi.fn() }))
vi.mock('@/api/equipment', () => ({ getAllEquipment: vi.fn() }))
vi.mock('@/api/binding', () => ({
  checkBatchBind: vi.fn(),
  confirmBatchBind: vi.fn()
}))

const flush = async () => {
  await flushPromises()
  await new Promise((resolve) => setTimeout(resolve, 10))
  await nextTick()
}

const brackets = {
  list: [{ id: 1, name: '支架甲', model: 'A-01', length: 200, width: 100, equipmentId: null }],
  total: 1,
  pageNum: 1,
  pageSize: 10
}

describe('BatchBind 换模批次放行联锁', () => {
  ;(globalThis as any).ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getBracketList).mockResolvedValue({ code: 200, message: 'ok', data: brackets } as any)
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('目标机未登记有效当前批次时前端拦截，不发起批量校验', async () => {
    vi.mocked(getAllEquipment).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: [{
        id: 3, code: 'FK-003', name: '3号封口机', bracketCount: 0,
        ruleConfigured: false, moldBatchReady: false,
        allowedMoldModels: ['MD-Z30'], currentBatchNo: null
      }]
    } as any)

    const wrapper = mount(BatchBind, { global: { plugins: [ElementPlus] }, attachTo: document.body })
    await flush()

    const vm = wrapper.vm as any
    vm.handleSelectionChange(brackets.list)
    vm.targetEquipmentId = 3
    await vm.handleBatchCheck()

    expect(checkBatchBind).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('未登记有效当前批次')
  })

  it('目标机当前批次就绪时正常发起批量校验', async () => {
    vi.mocked(getAllEquipment).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: [{
        id: 1, code: 'FK-001', name: '1号封口机', bracketCount: 0,
        ruleConfigured: false, moldBatchReady: true,
        allowedMoldModels: ['MD-A'], currentBatchNo: 'MB-OK', currentMoldModel: 'MD-A'
      }]
    } as any)
    vi.mocked(checkBatchBind).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: {
        equipmentId: 1, equipmentCode: 'FK-001', equipmentName: '1号封口机',
        currentCount: 0, maxBrackets: null, availableSlots: null,
        moldBatchGate: { passed: true, reason: null, currentBatchNo: 'MB-OK', currentMoldModel: 'MD-A' },
        items: [], passedItems: [], conflicts: []
      }
    } as any)

    const wrapper = mount(BatchBind, { global: { plugins: [ElementPlus] }, attachTo: document.body })
    await flush()

    const vm = wrapper.vm as any
    vm.handleSelectionChange(brackets.list)
    vm.targetEquipmentId = 1
    await vm.handleBatchCheck()

    expect(checkBatchBind).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('MB-OK')
  })
})
