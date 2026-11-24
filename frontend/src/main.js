import { createApp } from "vue";
import { createRouter, createWebHashHistory } from "vue-router";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import LoginView from "./LoginView.vue";
import { http, live } from "./orders";
import { session, clearSession, rolePath } from "./session";
import App from "./App.vue";
import Workspace from "./RepairWorkspace.vue";

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: "/", redirect: live ? "/login" : "/student" },
    { path: "/login", component: LoginView },
    ...["student", "admin", "repairer"].map((role) => ({
      path: "/" + role,
      component: Workspace,
      props: { role },
    })),
  ],
});

router.beforeEach(async (to) => {
  if (!live) return to.path === "/login" ? "/student" : true;
  if (to.path === "/login") return session.user ? rolePath() : true;
  if (!session.user && sessionStorage.getItem("dorm-repair-token")) {
    try {
      session.user = (await http.get("/auth/me")).data;
    } catch {
      clearSession();
    }
  }
  if (!session.user) return "/login";
  return to.path === rolePath() ? true : rolePath();
});

http.interceptors.response.use(undefined, (error) => {
  if (error.response?.status === 401 && error.config?.url !== "/auth/login") {
    clearSession();
    void router.replace("/login");
  }
  return Promise.reject(error);
});

createApp(App).use(router).use(ElementPlus).mount("#app");
