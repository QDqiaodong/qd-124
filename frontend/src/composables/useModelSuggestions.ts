import { ref } from 'vue'
import { getBracketModels } from '@/api/bracket'

/**
 * 热门支架型号建议（后端 Redis 缓存，支架档案增删改后失效）。
 * 支架保存或删除成功后调用 refresh，保证绑定/规则页面拿到的是最新型号。
 */
export function useModelSuggestions() {
  const modelSuggestions = ref<string[]>([])

  const refreshModelSuggestions = async () => {
    try {
      const res = await getBracketModels()
      modelSuggestions.value = res.data || []
    } catch (error) {
      console.error('获取热门型号建议失败:', error)
    }
  }

  return {
    modelSuggestions,
    refreshModelSuggestions
  }
}
