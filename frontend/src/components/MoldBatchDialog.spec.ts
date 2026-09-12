import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { nextTick } from 'vue'
import MoldBatchDialog from '@/components/MoldBatchDialog.vue'
import { registerMoldBatch, getMoldBatchHistory } from '@/api/equipment'

vi.mock('@/api/equipment', () => ({
  registerMoldBatch: vi.fn(),
  getMoldBatchHistory: vi.fn()
}))

const flush = async () => {
  await flushPromises()
  await new Promise((resolve) => setTimeout(resolve, 10))
  await nextTick()
}

const equipment = {
  id: 7,
  code: 'FK-007',
  name: '7号封口机',
  allowedMoldModels: ['MD-A', 'MD-B'],
  moldBatchReady: true,
  currentBatchNo: 'MB-NEW',
  currentMoldModel: 'MD-A',
  currentBatchChangeTime: '2026-09-10T08:00:00'
}

const historyPage = {
  total: 2,
  list: [
    {
      id: 2, equipmentId: 7, batchNo: 'MB-NEW', moldModel: 'MD-A',
      changeTime: '2026-09-10T08:00:00', operator: '张工', remark: '当前', current: true
    },
    {
      id: 1, equipmentId: 7, batchNo: 'MB-OLD', moldModel: 'MD-B',
      changeTime: '2026-08-01T08:00:00', operator: '张工', remark: '旧批次', current: false
    }
  ]
}

describe('MoldBatchDialog 换模批次登记与历史', () => {
  ;(globalThis as any).ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getMoldBatchHistory).mockResolvedValue({ code: 200, message: 'ok', data: historyPage } as any)
    vi.mocked(registerMoldBatch).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: historyPage.list[0]
    } as any)
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('打开时拉取该机历史换模记录，最新一条标记为当前批次', async () => {
    const wrapper = mount(MoldBatchDialog, {
      props: { modelValue: false, equipment },
      global: { plugins: [ElementPlus] },
      attachTo: document.body
    })
    await flush()
    expect(getMoldBatchHistory).not.toHaveBeenCalled()

    await wrapper.setProps({ modelValue: true })
    await flush()

    expect(getMoldBatchHistory).toHaveBeenCalledWith(7, { pageNum: 1, pageSize: 8 })
    const text = wrapper.text()
    expect(text).toContain('MB-NEW')
    expect(text).toContain('MB-OLD')
    expect(text).toContain('当前批次')
  })

  it('登记成功：调用登记接口并通知父组件刷新、重新拉取历史', async () => {
    const wrapper = mount(MoldBatchDialog, {
      props: { modelValue: true, equipment },
      global: { plugins: [ElementPlus] },
      attachTo: document.body
    })
    await flush()
    vi.mocked(getMoldBatchHistory).mockClear()

    const vm = wrapper.vm as any
    vm.form.batchNo = 'MB-20260912'
    vm.form.moldModel = 'MD-B'
    await vm.handleSubmit()
    await flush()

    expect(registerMoldBatch).toHaveBeenCalledWith(7, expect.objectContaining({
      batchNo: 'MB-20260912',
      moldModel: 'MD-B'
    }))
    expect(wrapper.emitted('registered')).toBeTruthy()
    // 登记后重新拉取历史
    expect(getMoldBatchHistory).toHaveBeenCalledTimes(1)
  })

  it('设备未配置允许模具型号清单时登记表单禁用，提示先配置规则', async () => {
    const wrapper = mount(MoldBatchDialog, {
      props: { modelValue: true, equipment: { ...equipment, allowedMoldModels: [], moldBatchReady: false } },
      global: { plugins: [ElementPlus] },
      attachTo: document.body
    })
    await flush()

    expect(wrapper.text()).toContain('尚未维护允许模具型号清单')
    const vm = wrapper.vm as any
    expect(vm.hasAllowedMolds).toBe(false)
  })
})
