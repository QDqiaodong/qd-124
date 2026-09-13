import type { BracketImportRow, BracketImportSuccessRow, BracketImportResult, Equipment } from '@/types'

/**
 * 导入成功行「改挂适配」标记 + 结果快照持久化。
 *
 * 背景：支架型号必须落在机台的「允许支架型号清单」内才能换线改挂到该机；
 * 只有登记了有效当前模具批次（moldBatchReady）的机台才算「在用批次」、有资格承接改挂。
 * 批量导入新增的支架虽全部入库成功，但若型号对不上任何一台在用批次机台的允许清单，
 * 它就没有任何可改挂的去处，需要在结果表逐行标红提示，直至用户确认看过。
 */

/** 在用批次机台：已登记当前模具批次且批次型号在允许模具清单内（与 moldBatchReady 同义） */
export function getInProductionEquipment(equipments: Equipment[]): Equipment[] {
  return equipments.filter((eq) => eq.moldBatchReady === true)
}

/** 该机台是否显式允许该支架型号：空清单视为「不限」，允许任意型号 */
function isModelAllowedBy(model: string, eq: Equipment): boolean {
  const allowed = eq.allowedModels
  if (!allowed || allowed.length === 0) return true
  const target = model.trim()
  return allowed.some((m) => (m || '').trim() === target)
}

/** 冲突机台：在用批次中、显式不允许该型号的机台；空清单（不限）的机台不计入 */
export function getConflictEquipments(model: string | null, inProduction: Equipment[]): Equipment[] {
  if (!model) return []
  return inProduction.filter((eq) => !isModelAllowedBy(model, eq))
}

/**
 * 为导入成功行逐行打标：
 * - 至少有一台在用批次机台允许该型号（或其清单为空表示不限）→ 可改挂
 * - 所有在用批次机台都显式拒绝该型号 → 标记 rehangBlocked，并记录冲突机台名
 *
 * 没有任何在用批次机台时不打标：此时改挂由换模批次闸门统一拦截，
 * 与型号清单无关，不应提示为型号冲突。
 */
export function markSuccessRows(
  rows: BracketImportRow[],
  equipments: Equipment[]
): BracketImportSuccessRow[] {
  const inProduction = getInProductionEquipment(equipments)
  if (inProduction.length === 0) {
    return rows.map((row) => ({ ...row, rehangBlocked: false, conflictEquipmentNames: [] }))
  }
  return rows.map((row) => {
    const conflicts = getConflictEquipments(row.model, inProduction)
    // 命中任意一台允许该机即有改挂去处；仅在「台台都拒绝」时拦截
    const blocked = conflicts.length === inProduction.length
    return {
      ...row,
      rehangBlocked: blocked,
      conflictEquipmentNames: blocked ? conflicts.map((eq) => eq.name).filter(Boolean) : []
    }
  })
}

/** 导入结果快照：关闭结果区/重进导入页后仍能还原带标记列的结果表 */
export interface ImportResultSnapshot {
  result: BracketImportResult
  successRows: BracketImportSuccessRow[]
  /** 存在不宜改挂行时持久化；用户确认看过后清除 */
  blockedCount: number
  savedAt: string
}

const STORAGE_KEY = 'bracket-import:rehang-warning:v1'

/** 保存待确认的结果快照；无不宜改挂行时清除旧快照，避免过期提示反复出现 */
export function saveImportResultSnapshot(snapshot: ImportResultSnapshot | null): void {
  try {
    if (!snapshot || snapshot.blockedCount === 0) {
      localStorage.removeItem(STORAGE_KEY)
      return
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(snapshot))
  } catch (error) {
    // localStorage 不可用（隐私模式/配额）时退化为仅本次会话内提示
    console.error('导入结果快照保存失败:', error)
  }
}

export function loadImportResultSnapshot(): ImportResultSnapshot | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as ImportResultSnapshot
    if (!parsed || !parsed.result || !Array.isArray(parsed.successRows)) return null
    return parsed
  } catch (error) {
    console.error('导入结果快照读取失败:', error)
    return null
  }
}

export function clearImportResultSnapshot(): void {
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch (error) {
    console.error('导入结果快照清除失败:', error)
  }
}
