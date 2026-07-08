import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import BracketList from '@/views/BracketList.vue'
import EquipmentList from '@/views/EquipmentList.vue'
import BatchBind from '@/views/BatchBind.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'BracketList',
    component: BracketList,
    meta: { title: '支架档案管理' }
  },
  {
    path: '/equipment',
    name: 'EquipmentList',
    component: EquipmentList,
    meta: { title: '设备配套清单' }
  },
  {
    path: '/batch-bind',
    name: 'BatchBind',
    component: BatchBind,
    meta: { title: '批量绑定' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  if (to.meta.title) {
    document.title = `${to.meta.title as string} - 封口机支架绑定管理系统`
  }
  next()
})

export default router
