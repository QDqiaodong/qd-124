import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import {
  getInProductionEquipment,
  getConflictEquipments,
  markSuccessRows,
  saveImportResultSnapshot,
  loadImportResultSnapshot,
  clearImportResultSnapshot,
  type ImportResultSnapshot
} from './bracketImportResult'
import type { BracketImportRow, BracketImportResult, Equipment } from '@/types'

const eq = (partial: Partial<Equipment>): Equipment => ({
  id: 0,
  code: '',
  name: '',
  ...partial
})

// 两台在用批次机台 + 一台未登记有效批次的机台
const equipments: Equipment[] = [
  eq({ id: 1, code: 'FK-001', name: '1号封口机', moldBatchReady: true, allowedModels: ['ST-OK'] }),
  eq({
    id: 2,
    code: 'FK-002',
    name: '2号封口机',
    moldBatchReady: true,
    allowedModels: ['ST-OK', 'ST-MID']
  }),
  eq({ id: 3, code: 'FK-003', name: '3号封口机', moldBatchReady: false, allowedModels: ['ST-OK'] })
]

const row = (model: string | null, rowNum = 2): BracketImportRow => ({
  rowNum,
  name: `支架-${model}`,
  model,
  length: 300,
  width: 150,
  status: 'VALID',
  reason: null
})

const buildSnapshot = (blockedCount: number): ImportResultSnapshot => ({
  result: {
    totalCount: 1,
    successCount: 1,
    failedCount: 0,
    skippedCount: 0,
    failedRows: []
  } as BracketImportResult,
  successRows: [
    {
      ...row('ST-NEW'),
      rehangBlocked: blockedCount > 0,
      conflictEquipmentNames: blockedCount > 0 ? ['1号封口机', '2号封口机'] : []
    }
  ],
  blockedCount,
  savedAt: '2026-09-12T10:00:00.000Z'
})

describe('bracketImportResult 导入成功行改挂适配标记', () => {
  it('只有 moldBatchReady 的机台才算在用批次', () => {
    expect(getInProductionEquipment(equipments).map((e) => e.id)).toEqual([1, 2])
  })

  it('型号命中任意一台在用批次机台的允许清单时不算冲突', () => {
    // ST-MID 仅 2 号机允许：有一台可改挂即放行
    expect(getConflictEquipments('ST-MID', getInProductionEquipment(equipments)).map((e) => e.id)).toEqual([1])
    const marked = markSuccessRows([row('ST-OK'), row('ST-MID')], equipments)
    expect(marked[0].rehangBlocked).toBe(false)
    expect(marked[0].conflictEquipmentNames).toEqual([])
    expect(marked[1].rehangBlocked).toBe(false)
  })

  it('型号被所有在用批次机台拒绝时拦截，并写出全部冲突机台名', () => {
    const marked = markSuccessRows([row('ST-NEW')], equipments)
    expect(marked[0].rehangBlocked).toBe(true)
    // 3 号机未登记有效批次，不属于在用批次，不进冲突机台
    expect(marked[0].conflictEquipmentNames).toEqual(['1号封口机', '2号封口机'])
  })

  it('在用批次机台允许清单为空表示不限，任意型号都不拦截', () => {
    const withUnlimited = [
      ...equipments,
      eq({ id: 4, code: 'FK-004', name: '4号封口机', moldBatchReady: true, allowedModels: [] })
    ]
    const marked = markSuccessRows([row('ST-NEW')], withUnlimited)
    expect(marked[0].rehangBlocked).toBe(false)
    expect(marked[0].conflictEquipmentNames).toEqual([])
  })

  it('型号比对按 trim 精确匹配，空格差异不影响判定', () => {
    const marked = markSuccessRows([row('  ST-OK  ')], equipments)
    expect(marked[0].rehangBlocked).toBe(false)
  })

  it('没有任何在用批次机台时不打型号冲突标记（由换模批次闸门统一拦截）', () => {
    const noReady: Equipment[] = [eq({ id: 9, name: '9号封口机', moldBatchReady: false, allowedModels: [] })]
    const marked = markSuccessRows([row('ST-NEW')], noReady)
    expect(marked[0].rehangBlocked).toBe(false)
    expect(marked[0].conflictEquipmentNames).toEqual([])
  })
})

describe('bracketImportResult 结果快照持久化', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('有不宜改挂行时写入快照，可原样还原', () => {
    const snapshot = buildSnapshot(1)
    saveImportResultSnapshot(snapshot)
    const loaded = loadImportResultSnapshot()
    expect(loaded?.blockedCount).toBe(1)
    expect(loaded?.successRows[0].conflictEquipmentNames).toEqual(['1号封口机', '2号封口机'])
    expect(loaded?.result.successCount).toBe(1)
  })

  it('无不宜改挂行时保存会清除旧快照，避免过期提示反复出现', () => {
    saveImportResultSnapshot(buildSnapshot(1))
    expect(loadImportResultSnapshot()).not.toBeNull()
    saveImportResultSnapshot(buildSnapshot(0))
    expect(loadImportResultSnapshot()).toBeNull()
    saveImportResultSnapshot(null)
    expect(loadImportResultSnapshot()).toBeNull()
  })

  it('确认看过/显式清除后快照不再存在', () => {
    saveImportResultSnapshot(buildSnapshot(2))
    clearImportResultSnapshot()
    expect(loadImportResultSnapshot()).toBeNull()
  })

  it('快照内容损坏时安全降级为空，不抛异常', () => {
    localStorage.setItem('bracket-import:rehang-warning:v1', '{not-json')
    expect(loadImportResultSnapshot()).toBeNull()
  })
})
