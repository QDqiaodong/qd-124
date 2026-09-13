import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { nextTick } from 'vue'
import EquipmentList from '@/views/EquipmentList.vue'
import {
  getEquipmentList,
  getEquipmentBrackets,
  getUnboundBracketCount,
  updateEquipmentRule,
  diagnoseEquipmentRule
} from '@/api/equipment'
import { getBracketModels } from '@/api/bracket'

vi.mock('@/api/equipment', () => ({
  getEquipmentList: vi.fn(),
  getEquipmentBrackets: vi.fn(),
  getUnboundBracketCount: vi.fn(),
  updateEquipmentRule: vi.fn(),
  diagnoseEquipmentRule: vi.fn()
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

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() })
}))

const flush = async () => {
  await flushPromises()
  await new Promise((resolve) => setTimeout(resolve, 10))
  await nextTick()
}

const findButton = (text: string): HTMLButtonElement | undefined =>
  Array.from(document.querySelectorAll('button')).find((btn) =>
    (btn.textContent || '').includes(text)
  ) as HTMLButtonElement | undefined

/**
 * 模拟真实后端的内存库：保存配套规则后各查询接口按库实时返回，
 * 用于验证「保存成功当下页顶容量标签与该机展开行的上限立即与新上限一致，
 * 不需要再搜一次或切到档案再回来」。
 */
const db = {
  equipments: [
    {
      id: 1, code: 'FK-001', name: '1号封口机', maxBrackets: 3 as number | null,
      allowedModels: ['ST-A001'], allowedMoldModels: [] as string[],
      minLength: null, maxLength: null, minWidth: null, maxWidth: null
    }
  ],
  brackets: [
    { id: 10, name: 'A型支架', model: 'ST-A001', length: 300, width: 150, equipmentId: 1 as number | null },
    { id: 11, name: 'B型支架', model: 'ST-A001', length: 400, width: 200, equipmentId: 1 as number | null }
  ]
}

const resetDb = () => {
  db.equipments[0].maxBrackets = 3
  db.brackets[0].equipmentId = 1
  db.brackets[1].equipmentId = 1
}

const equipmentVO = (e: any) => {
  const bracketCount = db.brackets.filter((b) => b.equipmentId === e.id).length
  const max = e.maxBrackets
  return {
    ...e,
    bracketCount,
    ruleConfigured: true,
    capacityStatus: max == null ? 'unlimited' : bracketCount > max ? 'exceeded' : bracketCount === max ? 'full' : 'normal',
    moldBatchReady: true
  }
}

const mockBackend = () => {
  vi.mocked(getEquipmentList).mockImplementation(async (params: any) => ({
    code: 200, message: 'ok',
    data: {
      list: db.equipments.map(equipmentVO),
      total: db.equipments.length,
      pageNum: params?.pageNum ?? 1,
      pageSize: params?.pageSize ?? 6
    }
  }) as any)
  vi.mocked(getEquipmentBrackets).mockImplementation(async (id: number) => ({
    code: 200, message: 'ok', data: db.brackets.filter((b) => b.equipmentId === id)
  }) as any)
  vi.mocked(getUnboundBracketCount).mockImplementation(async () => ({
    code: 200, message: 'ok', data: db.brackets.filter((b) => b.equipmentId == null).length
  }) as any)
  vi.mocked(getBracketModels).mockResolvedValue({ code: 200, message: 'ok', data: ['ST-A001'] } as any)
  vi.mocked(updateEquipmentRule).mockImplementation(async (id: number, rule: any) => {
    const e = db.equipments.find((x) => x.id === id)!
    e.maxBrackets = rule.maxBrackets
    return { code: 200, message: 'ok', data: equipmentVO(e) } as any
  })
  vi.mocked(diagnoseEquipmentRule).mockImplementation(async (id: number, rule: any) => ({
    code: 200, message: 'ok',
    data: {
      equipmentId: id, equipmentCode: 'FK-001', equipmentName: '1号封口机',
      currentCount: db.brackets.filter((b) => b.equipmentId === id).length,
      maxBrackets: rule.maxBrackets, ruleChanged: true, noImpact: false,
      manualCount: 0, capacityExceededCount: 0,
      existingViolations: [], capacityImpacts: [], futureOnlyItems: []
    }
  }) as any)
}

/** 走一遍真实操作：打开配套规则弹窗 → 改上限 → 预览 → 确认保存，返回保存完成时刻 */
const saveNewMaxBrackets = async (vm: any, newMax: number) => {
  vm.openRuleDialog(vm.equipmentList[0])
  await flush()
  vm.ruleForm.maxBrackets = newMax
  findButton('预览变更影响')!.click()
  await flush()
  findButton('确认保存')!.click()
  await flush()
}

describe('规则保存后占用/上限显示同步（回归）', () => {
  // jsdom 缺少 ResizeObserver，el-table 布局需要
  ;(globalThis as any).ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }

  beforeEach(() => {
    vi.clearAllMocks()
    resetDb()
    mockBackend()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('保存成功当下：页顶容量标签与该机展开行的上限立即与新上限一致，再搜一次不回弹', async () => {
    const wrapper = mount(EquipmentList, { global: { plugins: [ElementPlus] }, attachTo: document.body })
    await flush()
    const vm = wrapper.vm as any

    // 展开该机：保存前 容量 2 / 3、展开行 最大支架数量：3 个
    vm.toggleExpand(1)
    await flush()
    expect(document.body.textContent).toContain('容量 2 / 3')
    expect(document.body.textContent).toContain('最大支架数量：3 个')

    // 改上限 3 → 5 并保存
    await saveNewMaxBrackets(vm, 5)

    // 保存成功当下：数据与两处显示都已按新上限刷新，无需再搜一次或切档案
    expect(vm.equipmentList[0].maxBrackets).toBe(5)
    let text = document.body.textContent || ''
    expect(text).toContain('容量 2 / 5')
    expect(text).toContain('最大支架数量：5 个')
    expect(text).not.toContain('容量 2 / 3')
    expect(text).not.toContain('最大支架数量：3 个')

    // 再搜一次该机：仍是新上限，不回弹到旧数字
    vm.searchCode = 'FK-001'
    vm.handleSearch()
    await flush()
    text = document.body.textContent || ''
    expect(text).toContain('容量 2 / 5')
    expect(text).toContain('最大支架数量：5 个')

    wrapper.unmount()
  })

  it('收紧上限到占用以下：保存成功当下容量标签与超出容量标记立即按新上限显示', async () => {
    const wrapper = mount(EquipmentList, { global: { plugins: [ElementPlus] }, attachTo: document.body })
    await flush()
    const vm = wrapper.vm as any

    vm.toggleExpand(1)
    await flush()

    // 改上限 3 → 1（当前已挂 2 个，超出新上限）
    await saveNewMaxBrackets(vm, 1)

    // 保存成功当下：容量标签 2 / 1 与「超出容量」标记立即出现
    expect(vm.equipmentList[0].maxBrackets).toBe(1)
    expect(vm.equipmentList[0].capacityStatus).toBe('exceeded')
    const text = document.body.textContent || ''
    expect(text).toContain('容量 2 / 1')
    expect(text).toContain('超出容量')
    expect(text).toContain('最大支架数量：1 个')

    wrapper.unmount()
  })
})
