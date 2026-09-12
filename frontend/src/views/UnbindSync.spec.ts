import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElMessageBox } from 'element-plus'
import { nextTick } from 'vue'
import EquipmentList from '@/views/EquipmentList.vue'
import BracketList from '@/views/BracketList.vue'
import {
  getEquipmentList,
  getAllEquipment,
  getEquipmentBrackets,
  getUnboundBracketCount
} from '@/api/equipment'
import { getBracketList, getBracketStats, getBracketModels } from '@/api/bracket'
import { unbindBracket } from '@/api/binding'

vi.mock('@/api/equipment', () => ({
  getEquipmentList: vi.fn(),
  getAllEquipment: vi.fn(),
  getEquipmentBrackets: vi.fn(),
  getUnboundBracketCount: vi.fn(),
  updateEquipmentRule: vi.fn(),
  diagnoseEquipmentRule: vi.fn()
}))

vi.mock('@/api/bracket', () => ({
  getBracketList: vi.fn(),
  createBracket: vi.fn(),
  updateBracket: vi.fn(),
  deleteBracket: vi.fn(),
  getBracketStats: vi.fn(),
  getBracketModels: vi.fn()
}))

vi.mock('@/api/binding', () => ({
  unbindBracket: vi.fn(),
  checkBind: vi.fn(),
  confirmBind: vi.fn()
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() })
}))

const flush = async () => {
  await flushPromises()
  await new Promise((resolve) => setTimeout(resolve, 10))
  await nextTick()
}

/**
 * 内存库模拟真实后端：解绑后各查询接口按库实时返回，
 * 验证两个页面解绑后自身统计立即变化、重新挂载另一页面时读到的也是新值。
 */
const db = {
  equipments: [
    { id: 1, code: 'FK-001', name: '1号封口机', maxBrackets: 3, ruleConfigured: true, capacityStatus: 'normal' }
  ],
  brackets: [
    { id: 10, name: 'A型支架', model: 'ST-A001', length: 300, width: 150, equipmentId: 1, equipmentName: '1号封口机' },
    { id: 11, name: 'B型支架', model: 'ST-B002', length: 400, width: 200, equipmentId: 1, equipmentName: '1号封口机' },
    { id: 12, name: 'D型支架', model: 'ST-D004', length: 350, width: 180, equipmentId: null, equipmentName: null }
  ]
}

const resetDb = () => {
  db.brackets[0].equipmentId = 1
  db.brackets[0].equipmentName = '1号封口机'
  db.brackets[1].equipmentId = 1
  db.brackets[1].equipmentName = '1号封口机'
  db.brackets[2].equipmentId = null
  db.brackets[2].equipmentName = null
}

const equipmentVO = (e: any) => ({
  ...e,
  bracketCount: db.brackets.filter((b) => b.equipmentId === e.id).length,
  allowedModels: [], minLength: null, maxLength: null, minWidth: null, maxWidth: null,
  allowedMoldModels: [], moldBatchReady: false
})
const bracketVO = (b: any) => ({ ...b })

const mockBackend = () => {
  vi.mocked(getEquipmentList).mockImplementation(async (params: any) => ({
    code: 200, message: 'ok',
    data: {
      list: db.equipments.map(equipmentVO),
      total: db.equipments.length,
      pageNum: params?.pageNum ?? 1,
      pageSize: params?.pageSize ?? 6
    }
  }) as any)
  vi.mocked(getAllEquipment).mockImplementation(async () => ({
    code: 200, message: 'ok', data: db.equipments.map(equipmentVO)
  }) as any)
  vi.mocked(getEquipmentBrackets).mockImplementation(async (id: number) => ({
    code: 200, message: 'ok',
    data: db.brackets.filter((b) => b.equipmentId === id).map(bracketVO)
  }) as any)
  vi.mocked(getUnboundBracketCount).mockImplementation(async () => ({
    code: 200, message: 'ok', data: db.brackets.filter((b) => b.equipmentId == null).length
  }) as any)
  vi.mocked(getBracketList).mockImplementation(async (params: any) => {
    let rows = db.brackets.map(bracketVO)
    if (params?.bindStatus === 0) rows = rows.filter((b) => b.equipmentId == null)
    if (params?.name) rows = rows.filter((b) => b.name.includes(params.name))
    return {
      code: 200, message: 'ok',
      data: {
        list: rows, total: rows.length,
        pageNum: params?.pageNum ?? 1, pageSize: params?.pageSize ?? 10
      }
    } as any
  })
  vi.mocked(getBracketStats).mockImplementation(async () => ({
    code: 200, message: 'ok',
    data: {
      total: db.brackets.length,
      bound: db.brackets.filter((b) => b.equipmentId != null).length,
      unbound: db.brackets.filter((b) => b.equipmentId == null).length
    }
  }) as any)
  vi.mocked(getBracketModels).mockResolvedValue({ code: 200, message: 'ok', data: [] } as any)
  vi.mocked(unbindBracket).mockImplementation((async (id: number) => {
    const b = db.brackets.find((x) => x.id === id)
    if (b) {
      b.equipmentId = null
      b.equipmentName = null
    }
    return { code: 200, message: 'ok', data: null }
  }) as any)
}

const mountEquipment = () =>
  mount(EquipmentList, { global: { plugins: [ElementPlus] }, attachTo: document.body })
const mountArchive = () =>
  mount(BracketList, { global: { plugins: [ElementPlus] }, attachTo: document.body })

describe('解绑后占用/未绑定统计双向同步（回归）', () => {
  ;(globalThis as any).ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }

  beforeEach(() => {
    vi.clearAllMocks()
    resetDb()
    mockBackend()
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as any)
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('设备配套清单展开解绑：角标与页顶未绑定个数立即加减；再进档案页占用已是新数', async () => {
    // —— 设备配套清单页 ——
    const eqWrapper = mountEquipment()
    await flush()
    const eqVm = eqWrapper.vm as any
    expect(eqVm.unboundCount).toBe(1)
    expect(eqVm.equipmentList[0].bracketCount).toBe(2)

    eqVm.toggleExpand(1)
    await flush()
    const row = eqVm.equipmentBracketsMap.get(1)[0]
    await eqVm.handleUnbind(row, eqVm.equipmentList[0])
    await flush()

    expect(unbindBracket).toHaveBeenCalledWith(10)
    // 页顶未绑定个数 +1
    expect(eqVm.unboundCount).toBe(2)
    // 该机占用角标 -1（容量标签与 el-badge 同源 bracketCount）
    expect(eqVm.equipmentList[0].bracketCount).toBe(1)
    const text = document.body.textContent || ''
    expect(text).toContain('2 个')
    expect(text).toContain('容量 1')

    // 解绑后再搜该机：占用仍是解绑后的 1，不回弹
    eqVm.searchCode = 'FK-001'
    eqVm.handleSearch()
    await flush()
    expect(eqVm.equipmentList[0].bracketCount).toBe(1)

    eqWrapper.unmount()
    await flush()

    // —— 回到档案页：统计卡与列表按库实时 ——
    const archiveWrapper = mountArchive()
    await flush()
    const archiveVm = archiveWrapper.vm as any
    expect(archiveVm.stats).toMatchObject({ total: 3, bound: 1, unbound: 2 })
    const unbound = archiveVm.bracketList.find((b: any) => b.id === 10)
    expect(unbound.equipmentId).toBeNull()
    expect(unbound.equipmentName).toBeNull()
    archiveWrapper.unmount()
  })

  it('档案页解绑后再进设备配套清单：角标与未绑定个数同步', async () => {
    // —— 档案页解绑 id=10 ——
    const archiveWrapper = mountArchive()
    await flush()
    const archiveVm = archiveWrapper.vm as any
    expect(archiveVm.stats).toMatchObject({ total: 3, bound: 2, unbound: 1 })

    const row = archiveVm.bracketList.find((b: any) => b.id === 10)
    await archiveVm.handleUnbind(row)
    await flush()

    expect(unbindBracket).toHaveBeenCalledWith(10)
    expect(archiveVm.stats).toMatchObject({ total: 3, bound: 1, unbound: 2 })
    archiveWrapper.unmount()
    await flush()

    // —— 进入设备配套清单 ——
    const eqWrapper = mountEquipment()
    await flush()
    const eqVm = eqWrapper.vm as any
    expect(eqVm.unboundCount).toBe(2)
    expect(eqVm.equipmentList[0].bracketCount).toBe(1)
    const text = document.body.textContent || ''
    expect(text).toContain('2 个')
    expect(text).toContain('容量 1')

    // 展开该机配套支架：只剩 1 项
    eqVm.toggleExpand(1)
    await flush()
    expect(eqVm.equipmentBracketsMap.get(1).map((b: any) => b.id)).toEqual([11])
    eqWrapper.unmount()
  })
})
