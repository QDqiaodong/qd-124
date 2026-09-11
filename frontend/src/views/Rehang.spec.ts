import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { nextTick } from 'vue'
import Rehang from '@/views/Rehang.vue'
import { getAllEquipment, getEquipmentBrackets } from '@/api/equipment'
import { checkRehang, confirmRehang } from '@/api/binding'

const push = vi.fn()

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: mockQuery }),
  useRouter: () => ({ push })
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

const equipments = [
  { id: 1, code: 'SRC-1', name: '源封口机', bracketCount: 2, ruleConfigured: true, maxBrackets: 5 },
  { id: 2, code: 'TGT-2', name: '目标封口机', bracketCount: 0, ruleConfigured: true, maxBrackets: 10,
    allowedModels: ['A-01'], minLength: null, maxLength: null, minWidth: null, maxWidth: null }
]

const sourceBrackets = [
  { id: 10, name: '支架甲', model: 'A-01', length: 200, width: 100, equipmentId: 1 },
  { id: 11, name: '支架乙', model: 'B-99', length: 200, width: 100, equipmentId: 1 }
]

const checkResultData = {
  sourceEquipmentId: 1,
  sourceEquipmentCode: 'SRC-1',
  sourceEquipmentName: '源封口机',
  equipmentId: 2,
  equipmentCode: 'TGT-2',
  equipmentName: '目标封口机',
  currentCount: 0,
  maxBrackets: 10,
  availableSlots: 10,
  items: [
    { bracketId: 10, bracketName: '支架甲', model: 'A-01', length: 200, width: 100, passed: true, reason: null },
    { bracketId: 11, bracketName: '支架乙', model: 'B-99', length: 200, width: 100, passed: false, reason: '型号不在允许范围内（允许：A-01）' }
  ],
  passedItems: [
    { bracketId: 10, bracketName: '支架甲', model: 'A-01', length: 200, width: 100, passed: true, reason: null }
  ],
  conflicts: [
    { bracketId: 11, bracketName: '支架乙', model: 'B-99', length: 200, width: 100, passed: false, reason: '型号不在允许范围内（允许：A-01）' }
  ]
}

describe('Rehang 换线改挂预检与确认', () => {
  // jsdom 缺少 ResizeObserver，el-table 布局需要
  ;(globalThis as any).ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }

  beforeEach(() => {
    vi.clearAllMocks()
    mockQuery = {}
    vi.mocked(getAllEquipment).mockResolvedValue({ code: 200, message: 'ok', data: equipments } as any)
    vi.mocked(getEquipmentBrackets).mockResolvedValue({ code: 200, message: 'ok', data: sourceBrackets } as any)
    vi.mocked(checkRehang).mockResolvedValue({ code: 200, message: 'ok', data: checkResultData } as any)
    vi.mocked(confirmRehang).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { rehungCount: 1, rehung: [{ ...sourceBrackets[0], equipmentId: 2 }], conflicts: checkResultData.conflicts }
    } as any)
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('携带 sourceId 进入时默认选中源设备并拉取其已挂支架', async () => {
    mockQuery = { sourceId: '1' }
    mount(Rehang, { global: { plugins: [ElementPlus] }, attachTo: document.body })
    await flush()

    expect(getAllEquipment).toHaveBeenCalledTimes(1)
    expect(getEquipmentBrackets).toHaveBeenCalledWith(1)
  })

  it('预检请求携带源设备、目标设备与勾选支架，结果展示通过/冲突分项', async () => {
    const wrapper = mount(Rehang, { global: { plugins: [ElementPlus] }, attachTo: document.body })
    await flush()

    const vm = wrapper.vm as any
    vm.sourceEquipmentId = 1
    vm.targetEquipmentId = 2
    vm.handleSelectionChange(sourceBrackets)
    await vm.handleRehangCheck()
    await flush()

    expect(checkRehang).toHaveBeenCalledWith({
      sourceEquipmentId: 1,
      targetEquipmentId: 2,
      bracketIds: [10, 11]
    })
    expect(vm.checkDialogVisible).toBe(true)
    expect(vm.checkResult.passedItems).toHaveLength(1)
    expect(vm.checkResult.conflicts).toHaveLength(1)

    // 预检不改变源设备支架选择与挂载数据
    expect(getEquipmentBrackets).toHaveBeenCalledTimes(0)
  })

  it('确认改挂后仅提交一次、刷新源设备占用与设备清单，冲突项保留', async () => {
    const wrapper = mount(Rehang, { global: { plugins: [ElementPlus] }, attachTo: document.body })
    await flush()

    const vm = wrapper.vm as any
    vm.sourceEquipmentId = 1
    vm.checkResult = checkResultData
    await vm.handleCheckConfirm()
    await flush()

    // 确认时提交预检的全部条目，由后端重新预检并只改挂通过项
    expect(confirmRehang).toHaveBeenCalledTimes(1)
    expect(confirmRehang).toHaveBeenCalledWith({
      sourceEquipmentId: 1,
      targetEquipmentId: 2,
      bracketIds: [10, 11]
    })
    // 刷新：源设备支架清单重新拉取、设备清单占用重新统计
    expect(getEquipmentBrackets).toHaveBeenCalledWith(1)
    expect(getAllEquipment).toHaveBeenCalledTimes(2)
    expect(vm.checkDialogVisible).toBe(false)
    expect(vm.selectedBrackets).toHaveLength(0)
  })

  it('源设备与目标设备相同时前端拦截，不发起预检', async () => {
    const wrapper = mount(Rehang, { global: { plugins: [ElementPlus] }, attachTo: document.body })
    await flush()

    const vm = wrapper.vm as any
    vm.sourceEquipmentId = 1
    vm.targetEquipmentId = 1
    vm.handleSelectionChange(sourceBrackets)
    await vm.handleRehangCheck()

    expect(checkRehang).not.toHaveBeenCalled()
  })
})
