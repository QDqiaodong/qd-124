import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElMessageBox } from 'element-plus'
import { nextTick } from 'vue'
import BracketList from '@/views/BracketList.vue'
import {
  getBracketList,
  createBracket,
  updateBracket,
  deleteBracket,
  getBracketStats,
  getBracketModels
} from '@/api/bracket'
import { getAllEquipment } from '@/api/equipment'

vi.mock('@/api/bracket', () => ({
  getBracketList: vi.fn(),
  createBracket: vi.fn(),
  updateBracket: vi.fn(),
  deleteBracket: vi.fn(),
  getBracketStats: vi.fn(),
  getBracketModels: vi.fn()
}))

vi.mock('@/api/equipment', () => ({
  getAllEquipment: vi.fn()
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

const mountPage = () =>
  mount(BracketList, {
    global: { plugins: [ElementPlus] },
    attachTo: document.body
  })

describe('BracketList 支架档案与热门型号建议', () => {
  // jsdom 缺少 ResizeObserver，el-table 布局需要
  ;(globalThis as any).ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getBracketStats).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { total: 0, bound: 0, unbound: 0 }
    })
    vi.mocked(getBracketList).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { list: [], total: 0, pageNum: 1, pageSize: 10 }
    } as any)
    vi.mocked(getAllEquipment).mockResolvedValue({ code: 200, message: 'ok', data: [] })
    vi.mocked(getBracketModels).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: ['A-01', 'B-02']
    })
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('打开新增弹窗时拉取热门型号建议并渲染到型号 datalist', async () => {
    const wrapper = mountPage()
    await flush()

    ;(wrapper.vm as any).openCreateDialog()
    await flush()

    expect(getBracketModels).toHaveBeenCalledTimes(1)
    const datalist = document.getElementById('bracket-model-suggestions') as HTMLDataListElement
    expect(datalist).toBeTruthy()
    const values = Array.from(datalist.querySelectorAll('option')).map((o) => o.value)
    expect(values).toEqual(['A-01', 'B-02'])
    // el-input 需把 list 属性透传到原生 input，浏览器才会弹出建议
    const modelInput = document.querySelector(
      'input[list="bracket-model-suggestions"]'
    ) as HTMLInputElement
    expect(modelInput).toBeTruthy()
  })

  it('新增支架成功后刷新热门型号建议', async () => {
    vi.mocked(createBracket).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { id: 9, name: '新支架', model: 'C-03', length: 100, width: 50 }
    })
    const wrapper = mountPage()
    await flush()

    ;(wrapper.vm as any).openCreateDialog()
    await flush()

    const vm = wrapper.vm as any
    vm.formData.name = '新支架'
    vm.formData.model = 'C-03'
    findButton('确定')!.click()
    await flush()

    expect(createBracket).toHaveBeenCalledWith(
      expect.objectContaining({ name: '新支架', model: 'C-03' })
    )
    // 打开弹窗一次 + 保存成功后一次
    expect(getBracketModels).toHaveBeenCalledTimes(2)
  })

  it('改名保存成功后刷新建议，编辑接口携带最新型号', async () => {
    vi.mocked(getBracketList).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: {
        list: [{ id: 1, name: '支架1', model: 'OLD-MODEL', length: 100, width: 50 }],
        total: 1,
        pageNum: 1,
        pageSize: 10
      }
    } as any)
    vi.mocked(updateBracket).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { id: 1, name: '支架1', model: 'NEW-MODEL', length: 100, width: 50 }
    })
    vi.mocked(getBracketModels)
      .mockResolvedValueOnce({ code: 200, message: 'ok', data: ['OLD-MODEL'] })
      .mockResolvedValueOnce({ code: 200, message: 'ok', data: ['NEW-MODEL'] })

    const wrapper: VueWrapper = mountPage()
    await flush()

    const row = (wrapper.vm as any).bracketList[0]
    ;(wrapper.vm as any).openEditDialog(row)
    await flush()

    const vm = wrapper.vm as any
    vm.formData.model = 'NEW-MODEL'
    findButton('确定')!.click()
    await flush()

    expect(updateBracket).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ model: 'NEW-MODEL' })
    )
    expect(getBracketModels).toHaveBeenCalledTimes(2)
  })

  it('删除支架成功后刷新热门型号建议', async () => {
    vi.mocked(getBracketList).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: {
        list: [{ id: 3, name: '支架3', model: 'Z-09', length: 100, width: 50 }],
        total: 1,
        pageNum: 1,
        pageSize: 10
      }
    } as any)
    vi.mocked(deleteBracket).mockResolvedValue({ code: 200, message: 'ok', data: null } as any)
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as any)

    const wrapper = mountPage()
    await flush()

    const row = (wrapper.vm as any).bracketList[0]
    await (wrapper.vm as any).handleDelete(row)
    await flush()

    expect(deleteBracket).toHaveBeenCalledWith(3)
    expect(getBracketModels).toHaveBeenCalledTimes(1)
  })

  it('保存接口异常时不刷新热门型号建议且弹窗保持打开', async () => {
    vi.mocked(createBracket).mockRejectedValue(new Error('服务器错误'))
    const wrapper = mountPage()
    await flush()

    ;(wrapper.vm as any).openCreateDialog()
    await flush()
    expect(getBracketModels).toHaveBeenCalledTimes(1)

    const vm = wrapper.vm as any
    vm.formData.name = '支架X'
    vm.formData.model = 'X-99'
    findButton('确定')!.click()
    await flush()

    expect(createBracket).toHaveBeenCalledTimes(1)
    expect(getBracketModels).toHaveBeenCalledTimes(1)
    expect(vm.formDialogVisible).toBe(true)
  })
})
