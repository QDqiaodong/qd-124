import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElMessageBox } from 'element-plus'
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

const flush = async () => {
  await flushPromises()
  await new Promise((resolve) => setTimeout(resolve, 10))
  await nextTick()
}

const findButton = (text: string): HTMLButtonElement | undefined =>
  Array.from(document.querySelectorAll('button')).find((btn) =>
    (btn.textContent || '').includes(text)
  ) as HTMLButtonElement | undefined

const equipment = {
  id: 1,
  code: 'EQ-001',
  name: '1号封口机',
  maxBrackets: 5,
  allowedModels: ['A-01'],
  minLength: null,
  maxLength: null,
  minWidth: null,
  maxWidth: null,
  ruleConfigured: true,
  bracketCount: 3,
  capacityStatus: 'normal'
}

/** 跨字段同时收紧：型号 + 长度 + 容量，存量 3 个绑定分别落入三类 */
const crossFieldDiagnosis = {
  equipmentId: 1,
  equipmentCode: 'EQ-001',
  equipmentName: '1号封口机',
  currentCount: 3,
  maxBrackets: 2,
  ruleChanged: true,
  noImpact: false,
  manualCount: 2,
  capacityExceededCount: 1,
  existingViolations: [
    {
      bracketId: 10,
      bracketName: '支架甲',
      model: 'B-X',
      length: 500,
      width: 100,
      impactType: 'existing_violation',
      reasons: ['型号不在允许范围内（允许：A-01、A-02）', '长度超出允许范围（长度100~300mm）']
    }
  ],
  capacityImpacts: [
    {
      bracketId: 12,
      bracketName: '支架丙',
      model: 'A-01',
      length: 150,
      width: 90,
      impactType: 'capacity_only',
      reasons: ['超出设备最大支架数量（上限2个，当前已绑定3个），需人工解绑或调整容量']
    }
  ],
  futureOnlyItems: [
    {
      bracketId: 11,
      bracketName: '支架乙',
      model: 'A-02',
      length: 120,
      width: 80,
      impactType: 'future_only',
      reasons: []
    }
  ]
}

describe('EquipmentList 规则变更影响诊断', () => {
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
    vi.mocked(getBracketModels).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: ['A-01', 'A-02']
    })
    vi.mocked(updateEquipmentRule).mockResolvedValue({ code: 200, message: 'ok', data: equipment } as any)
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('打开弹窗不触发诊断；点击预览后按跨字段候选规则返回三类受影响条目及原因', async () => {
    vi.mocked(diagnoseEquipmentRule).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: crossFieldDiagnosis
    } as any)

    const wrapper = mount(EquipmentList, {
      global: { plugins: [ElementPlus] },
      attachTo: document.body
    })
    await flush()

    ;(wrapper.vm as any).openRuleDialog(equipment)
    await flush()
    expect(diagnoseEquipmentRule).not.toHaveBeenCalled()

    // 同时收紧容量、允许型号、长度范围（跨字段修改）
    const vm = wrapper.vm as any
    vm.ruleForm.maxBrackets = 2
    vm.ruleModelList = ['A-01', 'A-02']
    vm.ruleForm.minLength = 100
    vm.ruleForm.maxLength = 300

    findButton('预览变更影响')!.click()
    await flush()

    expect(diagnoseEquipmentRule).toHaveBeenCalledTimes(1)
    expect(diagnoseEquipmentRule).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        maxBrackets: 2,
        allowedModels: 'A-01,A-02',
        minLength: 100,
        maxLength: 300
      })
    )
    // 预览阶段不落库
    expect(updateEquipmentRule).not.toHaveBeenCalled()

    const text = document.body.textContent || ''
    expect(text).toContain('需要人工处理的存量绑定（2）')
    expect(text).toContain('支架甲')
    expect(text).toContain('型号不在允许范围内')
    expect(text).toContain('长度超出允许范围')
    expect(text).toContain('支架丙')
    expect(text).toContain('超出设备最大支架数量')
    expect(text).toContain('仅影响后续绑定的已有支架（1）')
    expect(text).toContain('支架乙')
    expect(text).toContain('系统不会自动解绑')
  })

  it('诊断视图点击取消：不落库且弹窗关闭', async () => {
    vi.mocked(diagnoseEquipmentRule).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: crossFieldDiagnosis
    } as any)

    const wrapper = mount(EquipmentList, {
      global: { plugins: [ElementPlus] },
      attachTo: document.body
    })
    await flush()
    ;(wrapper.vm as any).openRuleDialog(equipment)
    await flush()
    ;(wrapper.vm as any).ruleForm.maxBrackets = 2

    findButton('预览变更影响')!.click()
    await flush()
    expect((wrapper.vm as any).diagnosisVisible).toBe(true)

    findButton('取消')!.click()
    await flush()

    expect(updateEquipmentRule).not.toHaveBeenCalled()
    expect((wrapper.vm as any).ruleDialogVisible).toBe(false)
  })

  it('返回修改：回到规则表单且不落库，可再次调整后预览', async () => {
    vi.mocked(diagnoseEquipmentRule).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: crossFieldDiagnosis
    } as any)

    const wrapper = mount(EquipmentList, {
      global: { plugins: [ElementPlus] },
      attachTo: document.body
    })
    await flush()
    ;(wrapper.vm as any).openRuleDialog(equipment)
    await flush()

    findButton('预览变更影响')!.click()
    await flush()
    findButton('返回修改')!.click()
    await flush()

    expect((wrapper.vm as any).diagnosisVisible).toBe(false)
    expect((wrapper.vm as any).ruleDialogVisible).toBe(true)
    expect(updateEquipmentRule).not.toHaveBeenCalled()

    // 表单仍保留候选值，可再次预览
    expect(findButton('预览变更影响')).toBeTruthy()
  })

  it('确认保存：候选规则落库并刷新设备清单，保存后弹窗关闭', async () => {
    vi.mocked(diagnoseEquipmentRule).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: crossFieldDiagnosis
    } as any)

    const wrapper = mount(EquipmentList, {
      global: { plugins: [ElementPlus] },
      attachTo: document.body
    })
    await flush()
    const listCallsBefore = vi.mocked(getEquipmentList).mock.calls.length

    ;(wrapper.vm as any).openRuleDialog(equipment)
    await flush()
    const vm = wrapper.vm as any
    vm.ruleForm.maxBrackets = 2
    vm.ruleModelList = ['A-01', 'A-02']
    vm.ruleForm.minLength = 100
    vm.ruleForm.maxLength = 300

    findButton('预览变更影响')!.click()
    await flush()
    findButton('确认保存')!.click()
    await flush()

    expect(updateEquipmentRule).toHaveBeenCalledTimes(1)
    expect(updateEquipmentRule).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ maxBrackets: 2, allowedModels: 'A-01,A-02', minLength: 100, maxLength: 300 })
    )
    expect(vm.ruleDialogVisible).toBe(false)
    expect(vi.mocked(getEquipmentList).mock.calls.length).toBeGreaterThan(listCallsBefore)
  })

  it('无影响场景：规则未变化时提示不受影响且禁止保存', async () => {
    vi.mocked(diagnoseEquipmentRule).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: {
        ...crossFieldDiagnosis,
        ruleChanged: false,
        noImpact: true,
        manualCount: 0,
        existingViolations: [],
        capacityImpacts: [],
        futureOnlyItems: crossFieldDiagnosis.futureOnlyItems
      }
    } as any)

    const wrapper = mount(EquipmentList, {
      global: { plugins: [ElementPlus] },
      attachTo: document.body
    })
    await flush()
    ;(wrapper.vm as any).openRuleDialog(equipment)
    await flush()

    findButton('预览变更影响')!.click()
    await flush()

    const text = document.body.textContent || ''
    expect(text).toContain('候选规则与当前配置一致')
    expect(text).not.toContain('需要人工处理的存量绑定')
    const saveButton = findButton('确认保存')!
    expect(saveButton.disabled).toBe(true)
  })

  it('放宽规则且存量全部合规：仅提示影响后续绑定，无人工处理项', async () => {
    vi.mocked(diagnoseEquipmentRule).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: {
        ...crossFieldDiagnosis,
        maxBrackets: null,
        manualCount: 0,
        capacityExceededCount: 0,
        existingViolations: [],
        capacityImpacts: [],
        futureOnlyItems: [
          crossFieldDiagnosis.futureOnlyItems[0],
          { ...crossFieldDiagnosis.capacityImpacts[0], impactType: 'future_only', reasons: [] }
        ]
      }
    } as any)

    const wrapper = mount(EquipmentList, {
      global: { plugins: [ElementPlus] },
      attachTo: document.body
    })
    await flush()
    ;(wrapper.vm as any).openRuleDialog(equipment)
    await flush()

    findButton('预览变更影响')!.click()
    await flush()

    const text = document.body.textContent || ''
    expect(text).toContain('规则变更仅影响后续绑定')
    expect(text).toContain('全部合规，无需人工处理')
    expect(text).not.toContain('需要人工处理的存量绑定')
    expect(findButton('确认保存')!.disabled).toBe(false)
  })

  it('清空规则同样先走诊断预览，不直接落库', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as any)
    vi.mocked(diagnoseEquipmentRule).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: {
        equipmentId: 1,
        equipmentCode: 'EQ-001',
        equipmentName: '1号封口机',
        currentCount: 3,
        maxBrackets: null,
        ruleChanged: true,
        noImpact: false,
        manualCount: 0,
        capacityExceededCount: 0,
        existingViolations: [],
        capacityImpacts: [],
        futureOnlyItems: []
      }
    } as any)

    const wrapper = mount(EquipmentList, {
      global: { plugins: [ElementPlus] },
      attachTo: document.body
    })
    await flush()
    ;(wrapper.vm as any).openRuleDialog(equipment)
    await flush()

    await (wrapper.vm as any).handleClearRule()
    await flush()

    expect(updateEquipmentRule).not.toHaveBeenCalled()
    expect(diagnoseEquipmentRule).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        maxBrackets: null,
        allowedModels: '',
        minLength: null,
        maxLength: null,
        minWidth: null,
        maxWidth: null
      })
    )
    expect((wrapper.vm as any).diagnosisVisible).toBe(true)
  })
})
