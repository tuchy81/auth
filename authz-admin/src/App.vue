<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">HD Authz</div>
      <el-menu :default-active="$route.path" router>
        <el-menu-item index="/grant"><el-icon><Key /></el-icon>권한 부여</el-menu-item>
        <el-menu-item index="/groups"><el-icon><UserFilled /></el-icon>그룹 관리</el-menu-item>
        <el-menu-item index="/audit"><el-icon><Document /></el-icon>권한조회/감사</el-menu-item>
        <el-menu-item index="/simulate"><el-icon><MagicStick /></el-icon>시뮬레이션</el-menu-item>
        <el-sub-menu index="master">
          <template #title><el-icon><Setting /></el-icon>마스터 관리</template>
          <el-menu-item index="/master/systems">시스템</el-menu-item>
          <el-menu-item index="/master/shard">시스템 샤딩</el-menu-item>
          <el-menu-item index="/master/menus">메뉴</el-menu-item>
          <el-menu-item index="/master/apis">API</el-menu-item>
          <el-menu-item index="/master/actions">액션</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/menu-tree"><el-icon><Menu /></el-icon>메뉴 트리(미리보기)</el-menu-item>
        <el-menu-item index="/perftest"><el-icon><DataLine /></el-icon>성능 테스트</el-menu-item>
        <el-menu-item index="/proptest"><el-icon><RefreshRight /></el-icon>캐시 전파 테스트</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="title">엔터프라이즈 권한관리 플랫폼</div>
        <div class="right">
          <span style="color:#6b7280; margin-right:8px;">시스템</span>
          <el-select v-model="app.systemCd" size="small" style="width:140px" @change="app.setSystem">
            <el-option v-for="s in app.systems" :key="s.systemCd" :label="`${s.systemCd} - ${s.systemNm}`" :value="s.systemCd" />
          </el-select>
          <span class="user">{{ app.actor }}</span>
        </div>
      </el-header>
      <el-main><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { onMounted } from 'vue'
import { Key, Menu, Setting, Document, MagicStick, UserFilled, DataLine, RefreshRight } from '@element-plus/icons-vue'
import { useAppStore } from '@/store'
const app = useAppStore()
onMounted(() => app.loadSystems())
</script>

<style>
html, body, #app { height: 100%; margin: 0; }
.layout { height: 100%; }
.aside { background: #001529; color: #fff; }
.aside .el-menu { background: transparent; border: 0; }
.aside .el-menu-item, .aside .el-sub-menu__title { color: #cfd8dc; }
.aside .el-menu-item.is-active { background: #1677ff; color: #fff; }
.logo { color: #fff; padding: 18px 20px; font-weight: 700; font-size: 18px; letter-spacing: 0.5px; }
.header { background: #fff; border-bottom: 1px solid #e6e8eb; display: flex; justify-content: space-between; align-items: center; padding: 0 20px; }
.title { font-weight: 600; font-size: 16px; }
.right { display: flex; align-items: center; gap: 12px; }
.user { color: #6b7280; font-size: 13px; }
</style>
