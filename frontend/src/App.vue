<template>
  <div class="layout-container">
    <header class="layout-header">
      <div class="logo">
        <div class="logo-icon">
          <el-icon><Setting /></el-icon>
        </div>
        <span>封口机支架绑定管理系统</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        mode="horizontal"
        router
      >
        <el-menu-item index="/">
          <el-icon><Grid /></el-icon>
          <span>支架档案管理</span>
        </el-menu-item>
        <el-menu-item index="/equipment">
          <el-icon><Monitor /></el-icon>
          <span>设备配套清单</span>
        </el-menu-item>
        <el-menu-item index="/batch-bind">
          <el-icon><Link /></el-icon>
          <span>批量绑定</span>
        </el-menu-item>
      </el-menu>
    </header>
    <main class="layout-main">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Setting, Grid, Monitor, Link } from '@element-plus/icons-vue'

const route = useRoute()
const activeMenu = computed(() => route.path)
</script>
