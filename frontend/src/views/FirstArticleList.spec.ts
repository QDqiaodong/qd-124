import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { nextTick } from 'vue'
import FirstArticleList from '@/views/FirstArticleList.vue'
import { getAllEquipment } from '@/api/equipment'
import {
  createFirstArticle,
  getFirstArticleList,
  getFirstArticleDetail,
  releaseFirstArticle,
  returnFirstArticle
} from '@/api/firstArticle'

vi.mock('@/api/equipment', () => ({ getAllEquipment: vi.fn() }))
vi.mock('@/api/firstArticle', () => ({
  createFirstArticle: vi.fn(),
  getFirstArticleList: vi.fn(),
  getFirstArticleDetail: vi.fn(),
  releaseFirstArticle: vi.fn(),
  returnFirstArticle: vi.fn()
}))

const flush = async () => {
  await flushPromises()
  await new Promise((resolve) => setTimeout(resolve, 10))
  await nextTick()
}

const equipmentReady = {
  id: 1,
  code: 'FK-001',
  name: '1号封口机',
  currentBatchNo: 'MB-20260901-02',
  currentMoldModel: 'MD-X10',
  moldBatchReady: true
}

const equipmentNoBatch = {
  id: 2,
  code: 'FK-002',
  name: '2号封口机',
  currentBatchNo: null,
  currentMoldModel: null,
  moldBatchReady: false
}

/** 待签放·量差合格 */
const pendingOk = {
  id: 1,
  formNo: 'FA-20260913-0001',
  equipmentId: 1,
  equipmentCode: 'FK-001',
  equipmentName: '1号封口机',
  batchNo: 'MB-20260901-02',
  moldModel: 'MD-X10',
  standardLength: 300,
  standardWidth: 150,
  standardHeight: 80,
  tolerance: 0.5,
  measuredLength: 300.12,
  measuredWidth: 149.95,
  measuredHeight: 80.08,
  lengthDeviation: 0.12,
  widthDeviation: -0.05,
  heightDeviation: 0.08,
  outOfTolerance: false,
  status: 'PENDING',
  operator: '王调度',
  createTime: '2026-09-13T08:30:00'
}

/** 待签放·量差超线：不能放行，只能退回再量 */
const pendingOver = {
  ...pendingOk,
  id: 2,
  formNo: 'FA-20260913-0002',
  measuredLength: 300.74,
  lengthDeviation: 0.74,
  outOfTolerance: true
}

/** 已放行：带签放人/签放时间 */
const released = {
  ...pendingOk,
  id: 3,
  formNo: 'FA-20260911-0003',
  status: 'RELEASED',
  releaseSigner: '陈检',
  releaseTime: '2026-09-11T10:30:00'
}

/** 已退回再量：带退回人/退回时间 */
const returned = {
  ...pendingOver,
  id: 4,
  formNo: 'FA-20260912-0004',
  status: 'RETURNED',
  returnOperator: '李工',
  returnReason: '长量差超线，退回再量',
  returnTime: '2026-09-12T15:20:00'
}

const pageOf = (list: any[]) => ({ list, total: list.length, pageNum: 1, pageSize: 10 })

describe('FirstArticleList 首件尺寸确认单', () => {
  ;(globalThis as any).ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getAllEquipment).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: [equipmentReady, equipmentNoBatch]
    } as any)
    vi.mocked(getFirstArticleList).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: pageOf([pendingOk, pendingOver, released, returned])
    } as any)
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('列表按机台和放行结果组合筛选，参数透传给后端', async () => {
    const wrapper = mount(FirstArticleList, {
      global: { plugins: [ElementPlus] },
      attachTo: document.body
    })
    await flush()

    expect(getFirstArticleList).toHaveBeenCalledWith({
      pageNum: 1,
      pageSize: 10,
      equipmentId: undefined,
      status: undefined
    })

    const vm = wrapper.vm as any
    vm.filterEquipmentId = 1
    vm.filterStatus = 'RELEASED'
    await vm.handleSearch()

    expect(getFirstArticleList).toHaveBeenLastCalledWith({
      pageNum: 1,
      pageSize: 10,
      equipmentId: 1,
      status: 'RELEASED'
    })

    // 重置后恢复全量查询
    await vm.handleReset()
    expect(getFirstArticleList).toHaveBeenLastCalledWith({
      pageNum: 1,
      pageSize: 10,
      equipmentId: undefined,
      status: undefined
    })
  })

  it('超线待签放单只显示「退回再量」，不显示「签放」；合格待签放单两者都有', async () => {
    const wrapper = mount(FirstArticleList, {
      global: { plugins: [ElementPlus] },
      attachTo: document.body
    })
    await flush()

    const rows = wrapper.findAll('.el-table__body-wrapper tbody tr')
    expect(rows.length).toBe(4)

    const buttonTexts = (row: (typeof rows)[0]) => row.findAll('button').map((b) => b.text())

    // 第 1 行：合格待签放 → 有签放 + 退回再量
    const okButtons = buttonTexts(rows[0])
    expect(okButtons.some((t) => t.includes('签放'))).toBe(true)
    expect(okButtons.some((t) => t.includes('退回再量'))).toBe(true)

    // 第 2 行：超线待签放 → 只有退回再量，没有签放按钮
    expect(rows[1].text()).toContain('超线')
    const overButtons = buttonTexts(rows[1])
    expect(overButtons.some((t) => t.includes('退回再量'))).toBe(true)
    expect(overButtons.some((t) => t.includes('签放'))).toBe(false)

    // 终态行（已放行/已退回）无任何签放/退回操作按钮
    expect(rows[2].text()).toContain('已放行')
    expect(buttonTexts(rows[2]).some((t) => t.includes('签放') || t.includes('退回再量'))).toBe(false)
    expect(rows[3].text()).toContain('已退回再量')
    expect(buttonTexts(rows[3]).some((t) => t.includes('签放') || t.includes('退回再量'))).toBe(false)
  })

  it('开单时实时计算量差，超线即提示保存后不能放行', async () => {
    const wrapper = mount(FirstArticleList, {
      global: { plugins: [ElementPlus] },
      attachTo: document.body
    })
    await flush()

    const vm = wrapper.vm as any
    vm.openCreateDialog()
    await flush()

    vm.createForm.equipmentId = 1
    vm.createForm.standardLength = 300
    vm.createForm.standardWidth = 150
    vm.createForm.standardHeight = 80
    vm.createForm.tolerance = 0.5
    vm.createForm.measuredLength = 300.74
    vm.createForm.measuredWidth = 150.1
    vm.createForm.measuredHeight = 79.9
    await nextTick()

    expect(vm.previewOutOfTolerance).toBe(true)
    expect(document.body.textContent).toContain('量差超线：该单保存后不能放行量产，只能退回再量')

    // 改回合格范围：提示变为可签放
    vm.createForm.measuredLength = 300.12
    await nextTick()
    expect(vm.previewOutOfTolerance).toBe(false)
    expect(document.body.textContent).toContain('量差合格：保存后可由签放人签字放行')
  })

  it('机台未登记当前模具批次时不能开单，不发起保存请求', async () => {
    const wrapper = mount(FirstArticleList, {
      global: { plugins: [ElementPlus] },
      attachTo: document.body
    })
    await flush()

    const vm = wrapper.vm as any
    vm.openCreateDialog()
    await flush()

    vm.createForm.equipmentId = 2
    vm.createForm.standardLength = 300
    vm.createForm.standardWidth = 150
    vm.createForm.standardHeight = 80
    vm.createForm.tolerance = 0.5
    vm.createForm.measuredLength = 300.1
    vm.createForm.measuredWidth = 150.1
    vm.createForm.measuredHeight = 80.1
    vm.createForm.operator = '王调度'
    await nextTick()

    expect(document.body.textContent).toContain('尚未登记当前模具批次')
    await vm.submitCreate()
    expect(createFirstArticle).not.toHaveBeenCalled()
  })

  it('签放需填签放人；签放成功后刷新列表', async () => {
    vi.mocked(releaseFirstArticle).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { ...pendingOk, status: 'RELEASED', releaseSigner: '陈检' }
    } as any)

    const wrapper = mount(FirstArticleList, {
      global: { plugins: [ElementPlus] },
      attachTo: document.body
    })
    await flush()
    vi.mocked(getFirstArticleList).mockClear()

    const vm = wrapper.vm as any
    // 超线单直接调 openReleaseDialog 也被前端拦下
    vm.openReleaseDialog(pendingOver)
    expect(vm.releaseDialogVisible).toBe(false)

    vm.openReleaseDialog(pendingOk)
    expect(vm.releaseDialogVisible).toBe(true)

    // 不填签放人：不发起请求
    await vm.submitRelease()
    expect(releaseFirstArticle).not.toHaveBeenCalled()

    vm.releaseSigner = '陈检'
    await vm.submitRelease()
    expect(releaseFirstArticle).toHaveBeenCalledWith(1, { signer: '陈检' })
    // 签放成功后列表刷新
    expect(getFirstArticleList).toHaveBeenCalledTimes(1)
  })

  it('退回再量需填退回人；退回成功后刷新列表', async () => {
    vi.mocked(returnFirstArticle).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { ...pendingOver, status: 'RETURNED', returnOperator: '李工' }
    } as any)

    const wrapper = mount(FirstArticleList, {
      global: { plugins: [ElementPlus] },
      attachTo: document.body
    })
    await flush()
    vi.mocked(getFirstArticleList).mockClear()

    const vm = wrapper.vm as any
    vm.openReturnDialog(pendingOver)
    expect(vm.returnDialogVisible).toBe(true)
    // 超线单退回原因预填
    expect(vm.returnReason).toContain('超线')

    await vm.submitReturn()
    expect(returnFirstArticle).not.toHaveBeenCalled()

    vm.returnOperator = '李工'
    await vm.submitReturn()
    expect(returnFirstArticle).toHaveBeenCalledWith(2, {
      operator: '李工',
      reason: '量差超线，退回再量'
    })
    expect(getFirstArticleList).toHaveBeenCalledTimes(1)
  })

  it('点开详情能看到谁签的、几点签的', async () => {
    vi.mocked(getFirstArticleDetail).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: released
    } as any)

    const wrapper = mount(FirstArticleList, {
      global: { plugins: [ElementPlus] },
      attachTo: document.body
    })
    await flush()

    const vm = wrapper.vm as any
    await vm.openDetail(released)
    await flush()

    expect(getFirstArticleDetail).toHaveBeenCalledWith(3)
    expect(document.body.textContent).toContain('签放人')
    expect(document.body.textContent).toContain('陈检')
    expect(document.body.textContent).toContain('2026-09-11 10:30')
    expect(document.body.textContent).toContain('开单人')
    expect(document.body.textContent).toContain('王调度')
  })
})
