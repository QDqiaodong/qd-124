import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { nextTick } from 'vue'
import Rehang from '@/views/Rehang.vue'
import { getAllEquipment, getEquipmentBrackets } from '@/api/equipment'
import { checkRehang, confirmRehang } from '@/api/binding'

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: mockQuery }),
  useRouter: () => ({ push: vi.fn() })
}))

let mockQuery: Record<string, string> = {}

vi.mock('@/api/equipment', () => ({
  getAllEquipment: vi.fn(),
  getEquipmentBrackets: vi.fn()
}))

vi.mock('@/api/binding', () => ({
  checkRehang: vi.fn(),
  confirmRehang: vi.fn()
}))

const flush = async () => {
  await flushPromises()
  await new Promise((resolve) => setTimeout(resolve, 10))
  await nextTick()
}

/**
 * 模拟真实后端的内存库：确认改挂后各设备占用实时变化，
 * 用于验证「确认后三处占用立即与档案一致、冲突项留在源机不清零、再进改挂页不跳回旧占用」。
 */
const db = {
  equipments: [
    { id: 1, code: 'SRC-1', name: '源封口机', ruleConfigured: true, maxBrackets: 5 },
    {
      id: 2, code: 'TGT-2', name: '目标封口机', ruleConfigured: true, maxBrackets: 10,
      allowedModels: ['A-01'], minLength: null, maxLength: null, minWidth: null, maxWidth: null
    }
  ],
  brackets: [
    { id: 10, name: '支架甲', model: 'A-01', length: 200, width: 100, equipmentId: 1 },
    { id: 11, name: '支架乙', model: 'B-99', length: 200, width: 100, equipmentId: 1 }
  ]
}

const resetDb = () => {
  db.brackets[0].equipmentId = 1
  db.brackets[1].equipmentId = 1
}

const equipmentVO = (e: any) => ({
  ...e,
  bracketCount: db.brackets.filter((b) => b.equipmentId === e.id).length
})

const mockBackend = () => {
  vi.mocked(getAllEquipment).mockImplementation(async () => ({
    code: 200, message: 'ok', data: db.equipments.map(equipmentVO)
  }) as any)
  vi.mocked(getEquipmentBrackets).mockImplementation(async (id: number) => ({
    code: 200, message: 'ok', data: db.brackets.filter((b) => b.equipmentId === id)
  }) as any)
  vi.mocked(checkRehang).mockImplementation(async ({ sourceEquipmentId, targetEquipmentId, bracketIds }: any) => {
    const items = bracketIds.map((id: number) => {
      const b = db.brackets.find((x) => x.id === id)!
      const passed = b.equipmentId === sourceEquipmentId && b.model === 'A-01'
      return {
        bracketId: id, bracketName: b.name, model: b.model, length: b.length, width: b.width,
        passed, reason: passed ? null : '型号不在允许范围内（允许：A-01）'
      }
    })
    return {
      code: 200, message: 'ok',
      data: {
        sourceEquipmentId,
        sourceEquipmentCode: 'SRC-1',
        sourceEquipmentName: '源封口机',
        equipmentId: targetEquipmentId,
        equipmentCode: 'TGT-2',
        equipmentName: '目标封口机',
        currentCount: db.brackets.filter((b) => b.equipmentId === targetEquipmentId).length,
        maxBrackets: 10,
        availableSlots: 10,
        items,
        passedItems: items.filter((i: any) => i.passed),
        conflicts: items.filter((i: any) => !i.passed)
      }
    } as any
  })
  vi.mocked(confirmRehang).mockImplementation(async ({ sourceEquipmentId, targetEquipmentId, bracketIds }: any) => {
    const moved: any[] = []
    const conflicts: any[] = []
    for (const id of bracketIds) {
      const b = db.brackets.find((x) => x.id === id)!
      if (b.equipmentId === sourceEquipmentId && b.model === 'A-01') {
        b.equipmentId = targetEquipmentId
        moved.push(b)
      } else {
        conflicts.push({ bracketId: id, bracketName: b.name, passed: false, reason: '冲突' })
      }
    }
    return { code: 200, message: 'ok', data: { rehungCount: moved.length, rehung: moved, conflicts } } as any
  })
}

const doRehangConfirm = async (vm: any) => {
  vm.sourceEquipmentId = 1
  await vm.handleSourceChange()
  vm.targetEquipmentId = 2
  vm.handleSelectionChange([...vm.sourceBrackets])
  await vm.handleRehangCheck()
  await flush()
  await vm.handleCheckConfirm()
  await flush()
}

describe('Rehang 确认后占用一致性（回归）', () => {
  ;(globalThis as any).ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockQuery = {}
    resetDb()
    mockBackend()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('确认后源下拉已挂数量、目标机占用立即与档案一致，冲突项留在源机不清零', async () => {
    const wrapper = mount(Rehang, { global: { plugins: [ElementPlus] }, attachTo: document.body })
    await flush()
    const vm = wrapper.vm as any

    // 改挂前：源 2、目标 0
    expect(vm.equipmentList.find((e: any) => e.id === 1)?.bracketCount).toBe(2)
    expect(vm.equipmentList.find((e: any) => e.id === 2)?.bracketCount).toBe(0)

    await doRehangConfirm(vm)

    // 确认后：源 1（冲突项支架乙留下，未被清零）、目标 1
    const src = vm.equipmentList.find((e: any) => e.id === 1)
    const tgt = vm.equipmentList.find((e: any) => e.id === 2)
    expect(src?.bracketCount).toBe(1)
    expect(tgt?.bracketCount).toBe(1)
    // 源设备支架清单只剩冲突项
    expect(vm.sourceBrackets.map((b: any) => b.name)).toEqual(['支架乙'])
    // 三处显示与档案一致
    const bodyText = document.body.textContent || ''
    expect(bodyText).toContain('已挂 1 个')
    expect(bodyText).toContain('当前已占用 1 个')
  })

  it('再进改挂页不跳回旧占用', async () => {
    // 第一次进入并完成改挂
    let wrapper = mount(Rehang, { global: { plugins: [ElementPlus] }, attachTo: document.body })
    await flush()
    let vm = wrapper.vm as any
    await doRehangConfirm(vm)
    expect(vm.equipmentList.find((e: any) => e.id === 1)?.bracketCount).toBe(1)
    wrapper.unmount()
    await flush()

    // 再次进入（重新挂载）：占用应为改挂后的实时值，而非改挂前
    mockQuery = { sourceId: '1' }
    wrapper = mount(Rehang, { global: { plugins: [ElementPlus] }, attachTo: document.body })
    await flush()
    vm = wrapper.vm as any
    expect(vm.equipmentList.find((e: any) => e.id === 1)?.bracketCount).toBe(1)
    expect(vm.equipmentList.find((e: any) => e.id === 2)?.bracketCount).toBe(1)
    // 源设备被预选中且只剩冲突项
    expect(vm.sourceEquipmentId).toBe(1)
    expect(vm.sourceBrackets.map((b: any) => b.name)).toEqual(['支架乙'])
  })
})
