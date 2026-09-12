import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { nextTick } from 'vue'
import BatchBind from '@/views/BatchBind.vue'
import { getBracketList } from '@/api/bracket'
import { getAllEquipment } from '@/api/equipment'
import { checkBatchBind, confirmBatchBind } from '@/api/binding'

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
  list: [
    { id: 1, name: '支架甲', model: 'A-01', length: 200, width: 100, equipmentId: null },
    {
      id: 2,
      name: '支架乙',
      model: 'A-02',
      length: 200,
      width: 100,
      equipmentId: null,
      repairStatus: 'REPAIRING'
    },
    {
      id: 3,
      name: '支架丙',
      model: 'A-03',
      length: 200,
      width: 100,
      equipmentId: null,
      repairStatus: 'RETURNED_UNQUALIFIED'
    }
  ],
  total: 3,
  pageNum: 1,
  pageSize: 10
}

const readyEquipment = {
  id: 1,
  code: 'FK-001',
  name: '1号封口机',
  bracketCount: 0,
  ruleConfigured: false,
  moldBatchReady: true,
  allowedMoldModels: ['MD-A'],
  currentBatchNo: 'MB-OK',
  currentMoldModel: 'MD-A'
}

/** 后端按返修闸门逐项返回的预检条目：1 项通过、2 项返修拦截 */
const repairBlockedItems = [
  {
    bracketId: 1,
    bracketName: '支架甲',
    model: 'A-01',
    length: 200,
    width: 100,
    passed: true,
    reason: null,
    repairGate: null
  },
  {
    bracketId: 2,
    bracketName: '支架乙',
    model: 'A-02',
    length: 200,
    width: 100,
    passed: false,
    reason: '支架「支架乙」已标记返修（返修单 RP-002），尚未写回库结论与检验人，不能批量挂接/改挂，请先完成回库检验登记',
    repairGate: { passed: false, reason: '返修中未回库', repairNo: 'RP-002', status: 'REPAIRING' }
  },
  {
    bracketId: 3,
    bracketName: '支架丙',
    model: 'A-03',
    length: 200,
    width: 100,
    passed: false,
    reason: '支架「支架丙」返修单 RP-003 回库结论为不合格（检验人：王五），禁止批量挂接/改挂',
    repairGate: {
      passed: false,
      reason: '回库不合格',
      repairNo: 'RP-003',
      status: 'RETURNED_UNQUALIFIED',
      returnResult: false,
      inspector: '王五'
    }
  }
]

/** 后端按返修闸门逐项返回的预检结果：1 项通过、2 项返修拦截 */
const repairBlockedResult = {
  equipmentId: 1,
  equipmentCode: 'FK-001',
  equipmentName: '1号封口机',
  currentCount: 0,
  maxBrackets: null,
  availableSlots: null,
  moldBatchGate: { passed: true, reason: null, currentBatchNo: 'MB-OK', currentMoldModel: 'MD-A' },
  items: repairBlockedItems,
  passedItems: repairBlockedItems.filter((i) => i.passed),
  conflicts: repairBlockedItems.filter((i) => !i.passed)
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
      data: [readyEquipment]
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
    vm.handleSelectionChange([brackets.list[0]])
    vm.targetEquipmentId = 1
    await vm.handleBatchCheck()

    expect(checkBatchBind).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('MB-OK')
  })

  it('勾选返修中/回库不合格支架时预检照常发起，弹窗汇总拦截条数并逐条写明原因', async () => {
    vi.mocked(getAllEquipment).mockResolvedValue({
      code: 200, message: 'ok', data: [readyEquipment]
    } as any)
    vi.mocked(checkBatchBind).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: repairBlockedResult
    } as any)

    const wrapper = mount(BatchBind, { global: { plugins: [ElementPlus] }, attachTo: document.body })
    await flush()

    const vm = wrapper.vm as any
    // 勾选全部 3 项（含返修中未回库、回库不合格各 1 项）
    vm.handleSelectionChange(brackets.list)
    vm.targetEquipmentId = 1
    await vm.handleBatchCheck()
    await flush()

    // 不再被前端一条轻提示拦截：预检请求照常发起，弹窗打开
    expect(checkBatchBind).toHaveBeenCalledTimes(1)
    expect(checkBatchBind).toHaveBeenCalledWith({ bracketIds: [1, 2, 3], equipmentId: 1 })
    const dialog = document.querySelector('.el-dialog')
    expect(dialog).not.toBeNull()
    expect(dialog!.getAttribute('style') || '').not.toContain('display: none')

    // 顶部汇总返修拦截条数
    expect(wrapper.text()).toContain('2 项支架返修未合格回库')
    // 表格逐条写明原因
    expect(wrapper.text()).toContain('尚未写回库结论与检验人')
    expect(wrapper.text()).toContain('回库结论为不合格')
    // 1 项可确认绑定，返修 2 项不计入通过项
    expect(wrapper.text()).toContain('确认绑定（1 项）')
  })

  it('勾选的支架全部返修拦截时弹窗照常出现且确认按钮禁用，确认接口不得放行', async () => {
    vi.mocked(getAllEquipment).mockResolvedValue({
      code: 200, message: 'ok', data: [readyEquipment]
    } as any)
    const allBlockedItems = repairBlockedItems.filter((i) => !i.passed)
    vi.mocked(checkBatchBind).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: {
        ...repairBlockedResult,
        items: allBlockedItems,
        passedItems: [],
        conflicts: allBlockedItems
      }
    } as any)

    const wrapper = mount(BatchBind, { global: { plugins: [ElementPlus] }, attachTo: document.body })
    await flush()

    const vm = wrapper.vm as any
    vm.handleSelectionChange([brackets.list[1], brackets.list[2]])
    vm.targetEquipmentId = 1
    await vm.handleBatchCheck()
    await flush()

    expect(checkBatchBind).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('2 项支架返修未合格回库')
    expect(wrapper.text()).toContain('确认绑定（0 项）')

    // 确认按钮禁用，无法触发确认接口
    const confirmBtn = document.querySelector('.el-dialog .el-button--primary') as HTMLButtonElement
    expect(confirmBtn).not.toBeNull()
    expect(confirmBtn.disabled).toBe(true)
    await vm.handleCheckConfirm()
    expect(confirmBatchBind).not.toHaveBeenCalled()
  })
})
