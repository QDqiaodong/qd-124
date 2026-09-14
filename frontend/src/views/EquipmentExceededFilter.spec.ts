import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { nextTick } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import EquipmentList from '@/views/EquipmentList.vue'
import { getEquipmentList, getUnboundBracketCount } from '@/api/equipment'

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

const lastListParams = () => {
  const calls = vi.mocked(getEquipmentList).mock.calls
  return calls[calls.length - 1][0]
}

const normalEq = {
  id: 1,
  code: 'EQ-NORMAL',
  name: '正常封口机',
  maxBrackets: 5,
  allowedModels: [],
  minLength: null,
  maxLength: null,
  minWidth: null,
  maxWidth: null,
  ruleConfigured: true,
  bracketCount: 1,
  capacityStatus: 'normal'
}

const fullEq = {
  id: 2,
  code: 'EQ-FULL',
  name: '已满封口机',
  maxBrackets: 2,
  allowedModels: [],
  minLength: null,
  maxLength: null,
  minWidth: null,
  maxWidth: null,
  ruleConfigured: true,
  bracketCount: 2,
  capacityStatus: 'full'
}

const exceededEq = {
  id: 3,
  code: 'EQ-OVER',
  name: '超额封口机',
  maxBrackets: 2,
  allowedModels: [],
  minLength: null,
  maxLength: null,
  minWidth: null,
  maxWidth: null,
  ruleConfigured: true,
  bracketCount: 3,
  capacityStatus: 'exceeded'
}

const buildRouter = () =>
  createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/equipment', name: 'EquipmentList', component: EquipmentList },
      { path: '/', name: 'Home', component: { template: '<div />' } }
    ]
  })

const mountPage = async (initialQuery: Record<string, string> = {}) => {
  const router = buildRouter()
  await router.push({ path: '/equipment', query: initialQuery })
  const wrapper = mount(EquipmentList, {
    global: { plugins: [ElementPlus, router] },
    attachTo: document.body
  })
  await flush()
  return { wrapper, router }
}

describe('EquipmentList 工具栏「只看超额」开关', () => {
  // jsdom 缺少 ResizeObserver，el-table 布局需要
  ;(globalThis as any).ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getEquipmentList).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { list: [normalEq, fullEq], total: 2, pageNum: 1, pageSize: 6 }
    } as any)
    vi.mocked(getUnboundBracketCount).mockResolvedValue({ code: 200, message: 'ok', data: 0 } as any)
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('工具栏存在「只看超额」开关，默认关闭且不发送过滤参数', async () => {
    const { wrapper } = await mountPage()

    expect(wrapper.find('.exceeded-filter').exists()).toBe(true)
    expect(wrapper.find('.exceeded-filter-label').text()).toBe('只看超额')
    expect((wrapper.vm as any).onlyExceeded).toBe(false)

    const firstCallParams = vi.mocked(getEquipmentList).mock.calls[0][0]
    expect(firstCallParams.onlyExceeded).toBeUndefined()
  })

  it('打开开关：携带 onlyExceeded 由服务端过滤，列表只留超额机且卡片带「超出容量」标记', async () => {
    const { wrapper } = await mountPage()

    vi.mocked(getEquipmentList).mockResolvedValueOnce({
      code: 200,
      message: 'ok',
      data: { list: [exceededEq], total: 1, pageNum: 1, pageSize: 6 }
    } as any)

    // 模拟开关切换（v-model + change）
    ;(wrapper.vm as any).onlyExceeded = true
    ;(wrapper.vm as any).handleOnlyExceededChange()
    await flush()

    const params = lastListParams()
    expect(params.onlyExceeded).toBe(true)
    // 切换筛选回到第一页，避免在旧页码上展示对不上的数据
    expect(params.pageNum).toBe(1)

    const cards = wrapper.findAll('.equipment-card')
    expect(cards).toHaveLength(1)
    expect(wrapper.text()).toContain('EQ-OVER')
    expect(wrapper.text()).toContain('超出容量')
    // 满员/正常机不在名单里，标记自然也不会残留
    expect(wrapper.text()).not.toContain('EQ-FULL')
    expect(wrapper.text()).not.toContain('EQ-NORMAL')
    expect(wrapper.text()).not.toContain('已满')
  })

  it('关闭开关：回到当前编号/名称搜索结果，不再发送 onlyExceeded', async () => {
    const { wrapper } = await mountPage()

    // 先搜名称
    ;(wrapper.vm as any).searchName = '封口机'
    ;(wrapper.vm as any).handleSearch()
    await flush()
    expect(lastListParams().name).toBe('封口机')

    // 打开
    vi.mocked(getEquipmentList).mockResolvedValueOnce({
      code: 200,
      message: 'ok',
      data: { list: [exceededEq], total: 1, pageNum: 1, pageSize: 6 }
    } as any)
    ;(wrapper.vm as any).onlyExceeded = true
    ;(wrapper.vm as any).handleOnlyExceededChange()
    await flush()
    expect(lastListParams().onlyExceeded).toBe(true)

    // 关闭：仍带着原名称条件，onlyExceeded 消失，服务端返回搜索结果
    vi.mocked(getEquipmentList).mockResolvedValueOnce({
      code: 200,
      message: 'ok',
      data: { list: [normalEq, fullEq], total: 2, pageNum: 1, pageSize: 6 }
    } as any)
    ;(wrapper.vm as any).onlyExceeded = false
    ;(wrapper.vm as any).handleOnlyExceededChange()
    await flush()

    const lastParams = lastListParams()
    expect(lastParams.onlyExceeded).toBeUndefined()
    expect(lastParams.name).toBe('封口机')
    expect(wrapper.findAll('.equipment-card')).toHaveLength(2)
    expect(wrapper.text()).toContain('EQ-NORMAL')
  })

  it('翻页请求同样携带开关参数：超额机不能只靠前端藏卡片', async () => {
    const { wrapper } = await mountPage()

    vi.mocked(getEquipmentList).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { list: [exceededEq], total: 8, pageNum: 1, pageSize: 6 }
    } as any)
    ;(wrapper.vm as any).onlyExceeded = true
    ;(wrapper.vm as any).handleOnlyExceededChange()
    await flush()

    // 模拟分页器翻到第 2 页
    ;(wrapper.vm as any).pagination.pageNum = 2
    await (wrapper.vm as any).fetchEquipmentList()
    await flush()

    const params = lastListParams()
    expect(params.pageNum).toBe(2)
    expect(params.onlyExceeded).toBe(true)
  })

  it('开关状态写入地址栏，刷新（带 query 重新挂载）后仍只请求超额名单', async () => {
    const { router } = await mountPage({ name: '一号', onlyExceeded: 'true' })

    // 地址栏保留了开关与搜索条件
    expect(router.currentRoute.value.query.onlyExceeded).toBe('true')
    expect(router.currentRoute.value.query.name).toBe('一号')

    // 以刷新后的全新组件视角重新挂载一次：首屏请求即带过滤参数
    const remounted = await mountPage({ name: '一号', onlyExceeded: 'true' })
    expect((remounted.wrapper.vm as any).onlyExceeded).toBe(true)
    expect((remounted.wrapper.vm as any).searchName).toBe('一号')
    const params = lastListParams()
    expect(params.onlyExceeded).toBe(true)
    expect(params.name).toBe('一号')
  })

  it('重置按钮同时关闭开关并清空地址栏过滤参数', async () => {
    const { wrapper, router } = await mountPage({ onlyExceeded: 'true' })

    ;(wrapper.vm as any).searchCode = 'EQ'
    ;(wrapper.vm as any).handleReset()
    await flush()

    expect((wrapper.vm as any).onlyExceeded).toBe(false)
    expect((wrapper.vm as any).searchCode).toBe('')
    expect(router.currentRoute.value.query).toEqual({})
    const params = lastListParams()
    expect(params.onlyExceeded).toBeUndefined()
    expect(params.code).toBeUndefined()
  })
})
