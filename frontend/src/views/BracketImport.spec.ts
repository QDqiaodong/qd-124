import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElMessageBox } from 'element-plus'
import { nextTick } from 'vue'
import BracketImport from '@/views/BracketImport.vue'
import { getBracketStats, getBracketModels } from '@/api/bracket'
import { getAllEquipment } from '@/api/equipment'
import { previewBracketImport, confirmBracketImport } from '@/api/bracketImport'
import type { BracketImportPreview, BracketImportResult } from '@/types'

vi.mock('@/api/bracket', () => ({
  getBracketStats: vi.fn(),
  getBracketModels: vi.fn()
}))

vi.mock('@/api/equipment', () => ({
  getAllEquipment: vi.fn()
}))

vi.mock('@/api/bracketImport', () => ({
  isImportFileSupported: vi.fn(() => true),
  previewBracketImport: vi.fn(),
  confirmBracketImport: vi.fn(),
  getBracketImportTemplateUrl: vi.fn(() => '/api/bracket/import/template')
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

const mountPage = () =>
  mount(BracketImport, {
    global: { plugins: [ElementPlus] },
    attachTo: document.body
  })

const previewData: BracketImportPreview = {
  totalCount: 3,
  validCount: 1,
  duplicateCount: 1,
  invalidCount: 1,
  rows: [
    { rowNum: 2, name: '新支架', model: 'ST-NEW', length: 300, width: 150, status: 'VALID', reason: null },
    { rowNum: 3, name: '老支架', model: 'ST-OLD', length: 310, width: 160, status: 'DUPLICATE', reason: '型号「ST-OLD」已存在于支架档案，导入时将跳过该条' },
    { rowNum: 4, name: '坏支架', model: 'ST-BAD', length: 0, width: 170, status: 'INVALID', reason: '长度超出允许范围（0.01~10000mm）' }
  ]
}

const resultData: BracketImportResult = {
  totalCount: 3,
  successCount: 1,
  failedCount: 1,
  skippedCount: 1,
  failedRows: [
    { rowNum: 3, name: '老支架', model: 'ST-OLD', length: 310, width: 160, status: 'DUPLICATE', reason: '型号「ST-OLD」已存在于支架档案，导入时将跳过该条' },
    { rowNum: 4, name: '坏支架', model: 'ST-BAD', length: 0, width: 170, status: 'INVALID', reason: '长度超出允许范围（0.01~10000mm）' }
  ]
}

// 两台在用批次机台（已登记当前模具批次）：ST-NEW 两边都不允许
const inProductionEquipments = [
  { id: 1, code: 'FK-001', name: '1号封口机', moldBatchReady: true, allowedModels: ['ST-OTHER'] },
  { id: 2, code: 'FK-002', name: '2号封口机', moldBatchReady: true, allowedModels: ['ST-OTHER', 'ST-OK'] }
]

describe('BracketImport 支架档案批量导入工作台', () => {
  ;(globalThis as any).ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }

  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    vi.mocked(getBracketStats).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { total: 8, bound: 3, unbound: 5 }
    })
    vi.mocked(getBracketModels).mockResolvedValue({ code: 200, message: 'ok', data: ['ST-NEW'] })
    vi.mocked(getAllEquipment).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: inProductionEquipments as any
    })
    vi.mocked(previewBracketImport).mockResolvedValue({ code: 200, message: 'ok', data: previewData } as any)
    vi.mocked(confirmBracketImport).mockResolvedValue({ code: 200, message: 'ok', data: resultData } as any)
    document.body.innerHTML = ''
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    document.body.innerHTML = ''
  })

  it('挂载即拉取未绑定统计并展示', async () => {
    const wrapper = mountPage()
    await flush()
    expect(getBracketStats).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('未绑定')
    expect(wrapper.text()).toContain('5')
  })

  it('校验后按状态展示可导入/已存在/失败数量与行明细', async () => {
    const wrapper = mountPage()
    await flush()

    const vm = wrapper.vm as any
    vm.selectedFile = new File(['x'], 'b.csv', { type: 'text/csv' })
    await vm.handlePreview()
    await flush()

    expect(previewBracketImport).toHaveBeenCalledTimes(1)
    const text = wrapper.text()
    expect(text).toContain('校验结果预览')
    expect(text).toContain('可导入')
    expect(text).toContain('已存在')
    expect(text).toContain('校验失败')
    expect(text).toContain('型号「ST-OLD」已存在于支架档案')
    expect(text).toContain('长度超出允许范围')
    // 确认按钮显示可导入行数
    expect(findButton('确认导入 1 行')).toBeTruthy()
  })

  it('确认导入只调用确认接口，成功后刷新统计与热门型号建议', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as any)
    const wrapper = mountPage()
    await flush()

    const vm = wrapper.vm as any
    vm.selectedFile = new File(['x'], 'b.csv', { type: 'text/csv' })
    await vm.handlePreview()
    await flush()

    findButton('确认导入 1 行')!.click()
    await flush()

    expect(confirmBracketImport).toHaveBeenCalledTimes(1)
    // 挂载一次 + 导入成功后一次
    expect(getBracketStats).toHaveBeenCalledTimes(2)
    expect(getBracketModels).toHaveBeenCalledTimes(1)
    const text = wrapper.text()
    expect(text).toContain('成功（新增）')
    expect(text).toContain('失败（未写入）')
    expect(text).toContain('跳过（型号已存在）')
  })

  it('取消确认弹窗时不调用导入接口、不刷新建议', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue('cancel')
    const wrapper = mountPage()
    await flush()

    const vm = wrapper.vm as any
    vm.selectedFile = new File(['x'], 'b.csv', { type: 'text/csv' })
    await vm.handlePreview()
    await flush()

    findButton('确认导入 1 行')!.click()
    await flush()

    expect(confirmBracketImport).not.toHaveBeenCalled()
    expect(getBracketModels).not.toHaveBeenCalled()
  })

  it('失败行可下载为带原因列的 CSV（含表头与全部未入库行）', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as any)
    // jsdom 中 anchor.click 无下载行为，stub 掉避免导航；通过 createObjectURL 捕获文件内容
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const createdBlobs: string[] = []
    // jsdom 的 Blob 没有 text()，用子类记录构造时传入的文本；并打桩 createObjectURL
    const OriginalBlob = globalThis.Blob
    vi.stubGlobal(
      'Blob',
      class extends OriginalBlob {
        constructor(parts: BlobPart[], options?: BlobPropertyBag) {
          super(parts, options)
          createdBlobs.push(parts.map(String).join(''))
        }
      }
    )
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL: vi.fn(() => 'blob:mock-url'),
      revokeObjectURL: vi.fn()
    })

    const wrapper = mountPage()
    await flush()

    const vm = wrapper.vm as any
    vm.selectedFile = new File(['x'], 'b.csv', { type: 'text/csv' })
    await vm.handlePreview()
    await flush()
    findButton('确认导入 1 行')!.click()
    await flush()

    const downloadBtn = findButton('下载失败行')
    expect(downloadBtn).toBeTruthy()
    downloadBtn!.click()
    await flush()

    expect(createdBlobs).toHaveLength(1)
    const text = createdBlobs[0]
    expect(text).toContain('支架名称,支架型号,长(mm),宽(mm),未入库原因')
    expect(text).toContain('ST-OLD')
    expect(text).toContain('ST-BAD')
    expect(text).toContain('长度超出允许范围')
  })

  it('没有可导入行时确认按钮禁用', async () => {
    vi.mocked(previewBracketImport).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { totalCount: 1, validCount: 0, duplicateCount: 0, invalidCount: 1, rows: [previewData.rows[2]] }
    } as any)
    const wrapper = mountPage()
    await flush()

    const vm = wrapper.vm as any
    vm.selectedFile = new File(['x'], 'b.csv', { type: 'text/csv' })
    await vm.handlePreview()
    await flush()

    const btn = findButton('确认导入') as HTMLButtonElement
    expect(btn).toBeTruthy()
    expect(btn.disabled).toBe(true)
    expect(wrapper.text()).toContain('没有可导入的数据行')
  })

  /** 走一遍 校验 → 确认导入 流程并等待结果渲染 */
  const importOnce = async () => {
    const wrapper = mountPage()
    await flush()
    const vm = wrapper.vm as any
    vm.selectedFile = new File(['x'], 'b.csv', { type: 'text/csv' })
    await vm.handlePreview()
    await flush()
    findButton('确认导入 1 行')!.click()
    await flush()
    return wrapper
  }

  it('型号对不上任何在用批次允许清单时：仍算成功，但结果表新增标记列并写出冲突机台名', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as any)
    const wrapper = await importOnce()

    const text = wrapper.text()
    // 成功明细表存在，标记列表头存在
    expect(text).toContain('成功入库明细')
    expect(text).toContain('换线改挂')
    // 该行仍计入成功
    expect(text).toContain('ST-NEW')
    // 标为不宜改挂并列出两台冲突机台
    expect(text).toContain('型号对不上在用批次')
    expect(text).toContain('冲突机台：1号封口机、2号封口机')
    // 顶部告警与待确认按钮
    expect(text).toContain('不能直接拿去换线改挂')
    expect(findButton('我已知悉')).toBeTruthy()
  })

  it('型号命中任意一台在用批次允许清单时不标记、不写冲突机台', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as any)
    vi.mocked(getAllEquipment).mockResolvedValue({
      code: 200,
      message: 'ok',
      // ST-NEW 被 2 号机允许
      data: [
        { id: 1, code: 'FK-001', name: '1号封口机', moldBatchReady: true, allowedModels: ['ST-OTHER'] },
        { id: 2, code: 'FK-002', name: '2号封口机', moldBatchReady: true, allowedModels: ['ST-NEW'] }
      ] as any
    })
    const wrapper = await importOnce()

    const text = wrapper.text()
    expect(text).toContain('可用于在产批次')
    expect(text).not.toContain('型号对不上在用批次')
    expect(text).not.toContain('冲突机台')
    expect(findButton('我已知悉')).toBeFalsy()
    // 无冲突时不留快照
    expect(localStorage.getItem('bracket-import:rehang-warning:v1')).toBeNull()
  })

  it('关掉结果区再进导入页，标记列与冲突机台仍在（快照还原）', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as any)
    await importOnce()
    expect(localStorage.getItem('bracket-import:rehang-warning:v1')).not.toBeNull()

    // 模拟关闭结果抽屉后重新进入导入页：组件重新挂载
    const wrapper2 = mountPage()
    await flush()
    const text2 = wrapper2.text()
    expect(text2).toContain('导入结果')
    expect(text2).toContain('上次导入未确认提示')
    expect(text2).toContain('型号对不上在用批次')
    expect(text2).toContain('冲突机台：1号封口机、2号封口机')
    expect(findButton('我已知悉')).toBeTruthy()
  })

  it('用户点「我已知悉」后收起提醒并清除快照，再次进入导入页不再出现标记列', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as any)
    const wrapper = await importOnce()

    const ackBtn = findButton('我已知悉')!
    ackBtn.click()
    await flush()
    // 快照已清除：重进页面不再提示
    expect(localStorage.getItem('bracket-import:rehang-warning:v1')).toBeNull()
    // 当前页提醒告警收起
    expect(wrapper.text()).not.toContain('不能直接拿去换线改挂')
    expect(findButton('我已知悉')).toBeFalsy()

    // 重进导入页：无快照，结果区不再出现
    const wrapper2 = mountPage()
    await flush()
    expect(wrapper2.text()).not.toContain('导入结果')
    expect(wrapper2.text()).not.toContain('型号对不上在用批次')
  })
})
