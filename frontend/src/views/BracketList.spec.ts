import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import ElementPlus, { ElMessageBox, ElPagination, ElSelect } from 'element-plus'
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

  /**
   * 选中「回库不合格」后：
   * 1. 列表请求必须带上 repairStatus=RETURNED_UNQUALIFIED；
   * 2. 只改名保存后的刷新请求仍带同一筛选（表格不会把已回库合格的行带回）；
   * 3. 翻页、改每页条数的请求同样保持筛选参数。
   */
  it('回库不合格筛选在改名保存、翻页、改每页条数后持续生效', async () => {
    vi.mocked(getBracketList).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: {
        list: [
          {
            id: 7,
            name: '待改名支架',
            model: 'A-01',
            length: 100,
            width: 50,
            repairStatus: 'RETURNED_UNQUALIFIED'
          }
        ],
        total: 1,
        pageNum: 1,
        pageSize: 10
      }
    } as any)
    vi.mocked(updateBracket).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { id: 7, name: '新名称', model: 'A-01', length: 100, width: 50 }
    })

    const wrapper = mountPage()
    await flush()
    vi.mocked(getBracketList).mockClear()

    // 工具栏唯一的 el-select 即返修状态筛选，选中「回库不合格」
    wrapper.findComponent(ElSelect).vm.$emit('update:modelValue', 'RETURNED_UNQUALIFIED')
    wrapper.findComponent(ElSelect).vm.$emit('change', 'RETURNED_UNQUALIFIED')
    await flush()

    expect(getBracketList).toHaveBeenCalledWith(
      expect.objectContaining({ repairStatus: 'RETURNED_UNQUALIFIED', pageNum: 1 })
    )
    vi.mocked(getBracketList).mockClear()

    // 只改名并保存：保存后的列表刷新必须仍停留在回库不合格
    const vm = wrapper.vm as any
    vm.openEditDialog(vm.bracketList[0])
    await flush()
    vm.formData.name = '新名称'
    findButton('确定')!.click()
    await flush()

    expect(updateBracket).toHaveBeenCalledWith(
      7,
      expect.objectContaining({ name: '新名称', model: 'A-01' })
    )
    expect(getBracketList).toHaveBeenCalledWith(
      expect.objectContaining({ repairStatus: 'RETURNED_UNQUALIFIED' })
    )
    expect(vm.searchRepairStatus).toBe('RETURNED_UNQUALIFIED')
    vi.mocked(getBracketList).mockClear()

    // 翻到第 2 页，筛选仍在（v-model:current-page 与 current-change 同时触发）
    const pagination = wrapper.findComponent(ElPagination).vm
    pagination.$emit('update:current-page', 2)
    pagination.$emit('current-change', 2)
    await flush()
    expect(getBracketList).toHaveBeenCalledWith(
      expect.objectContaining({ repairStatus: 'RETURNED_UNQUALIFIED', pageNum: 2 })
    )
    vi.mocked(getBracketList).mockClear()

    // 改每页条数为 20，筛选仍在
    pagination.$emit('update:page-size', 20)
    pagination.$emit('size-change', 20)
    await flush()
    expect(getBracketList).toHaveBeenCalledWith(
      expect.objectContaining({ repairStatus: 'RETURNED_UNQUALIFIED', pageSize: 20 })
    )
  })

  it('重置按钮清空返修状态筛选且后续请求不再携带 repairStatus', async () => {
    const wrapper = mountPage()
    await flush()

    const vm = wrapper.vm as any
    vm.searchRepairStatus = 'RETURNED_UNQUALIFIED'
    vi.mocked(getBracketList).mockClear()

    findButton('重置')!.click()
    await flush()

    expect(vm.searchRepairStatus).toBe('')
    const calls = vi.mocked(getBracketList).mock.calls
    const lastCall = calls[calls.length - 1][0]
    // 空筛选序列化为 undefined，axios 不会把该参数发到后端
    expect(lastCall.repairStatus).toBeUndefined()
  })
})
