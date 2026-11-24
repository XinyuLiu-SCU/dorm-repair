<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import { http, errorMessage } from "./orders";
import { session, clearSession, rolePath } from "./session";

const router = useRouter();
const username = ref("");
const password = ref("");
const pending = ref(false);
const error = ref("");

async function login() {
  if (pending.value || !username.value.trim() || !password.value) return;
  pending.value = true;
  error.value = "";
  try {
    const { data } = await http.post("/auth/login", {
      username: username.value.trim(),
      password: password.value,
    });
    sessionStorage.setItem("dorm-repair-token", data.accessToken);
    session.user = (await http.get("/auth/me")).data;
    await router.replace(rolePath());
  } catch (failure) {
    clearSession();
    error.value =
      failure.response?.status === 401
        ? "账号或密码错误。"
        : errorMessage(failure);
  } finally {
    password.value = "";
    pending.value = false;
  }
}
</script>

<template>
  <section style="max-width: 380px; margin: 48px auto">
    <h1>登录宿舍报修系统</h1>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form label-position="top" @submit.prevent="login">
      <el-form-item label="账号"
        ><el-input v-model="username" autocomplete="username"
      /></el-form-item>
      <el-form-item label="密码"
        ><el-input
          v-model="password"
          type="password"
          autocomplete="current-password"
      /></el-form-item>
      <el-button type="primary" native-type="submit" :loading="pending"
        >登录</el-button
      >
    </el-form>
  </section>
</template>
