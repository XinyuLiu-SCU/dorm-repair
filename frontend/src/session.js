import { reactive } from "vue";

export const session = reactive({ user: null });
export const rolePath = () => "/" + session.user.role.toLowerCase();
export function clearSession() {
  sessionStorage.removeItem("dorm-repair-token");
  session.user = null;
}
