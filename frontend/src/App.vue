<script setup>
import { session, clearSession } from "./session";
import { useRouter } from "vue-router";
const router = useRouter();
function logout() {
  clearSession();
  void router.replace("/login");
}
import { live } from "./orders";
</script>

<template>
  <div class="shell">
    <aside>
      <div class="brand">宿舍报修</div>
      <nav v-if="!live" aria-label="工作区">
        <RouterLink to="/student">学生工作区</RouterLink>
        <RouterLink to="/admin">管理工作区</RouterLink>
        <RouterLink to="/repairer">维修工作区</RouterLink>
      </nav>
      <div v-if="session.user">
        <p>{{ session.user.username }}</p>
        <el-button @click="logout">退出登录</el-button>
      </div>
    </aside>
    <main>
      <div class="top">
        <span>服务中心 / 工单管理</span>
      </div>
      <RouterView v-slot="{ Component, route }"
        ><component :is="Component" :key="route.path"
      /></RouterView>
    </main>
  </div>
</template>

<style>
:root {
  font-family: "Microsoft YaHei", system-ui, sans-serif;
  color: #15283b;
  background: #f2f6f9;
  --el-color-primary: #176d65;
}
* {
  box-sizing: border-box;
}
body {
  margin: 0;
}
.shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
}
aside {
  background: #142b38;
  color: #f6fbfb;
  padding: 30px 18px;
}
.brand {
  font-size: 23px;
  font-weight: 700;
  margin: 0 10px 34px;
}
aside p {
  overflow-wrap: anywhere;
}
nav {
  display: grid;
  gap: 8px;
}
nav a {
  color: #c6dbe1;
  text-decoration: none;
  padding: 13px;
  border-radius: 8px;
}
nav a.router-link-active {
  background: #255048;
  color: #fff;
}
main {
  padding: 24px clamp(16px, 4vw, 58px) 52px;
}
.top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #52687a;
  border-bottom: 1px solid #dce5ea;
  padding-bottom: 18px;
}
@media (max-width: 650px) {
  .shell {
    display: block;
  }
  aside {
    padding: 14px;
  }
  .brand {
    margin: 0 0 9px;
  }
  nav {
    display: flex;
    flex-wrap: wrap;
  }
  nav a {
    padding: 8px;
  }
}
</style>
