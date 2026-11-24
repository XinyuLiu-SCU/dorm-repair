import axios from "axios";

export const http = axios.create({ baseURL: "/api", timeout: 8000 });
http.interceptors.request.use((request) => {
  const token = sessionStorage.getItem("dorm-repair-token");
  if (token) request.headers.Authorization = "Bearer " + token;
  return request;
});

export const live = import.meta.env.VITE_DATA_MODE === "live";
const endpoint = "/repair-orders";
const storageKey = "dorm-repair-demo-v1";
const initialOrders = [
  {
    id: 1,
    studentId: 1,
    title: "洗手池水龙头漏水",
    location: "一号楼 302 室",
    description: "水龙头关紧之后仍会滴水。",
    status: "PENDING",
  },
  {
    id: 2,
    studentId: 1,
    repairerId: 2,
    title: "书桌照明灯无法开启",
    location: "一号楼 302 室",
    description: "打开开关后灯具无反应。",
    status: "COMPLETED",
  },
];
const transitions = {
  approve: { PENDING: "APPROVED" },
  reject: { PENDING: "REJECTED" },
  assign: { APPROVED: "ASSIGNED" },
  start: { ASSIGNED: "IN_PROGRESS", REWORK: "IN_PROGRESS" },
  finish: { IN_PROGRESS: "WAITING_ACCEPTANCE" },
};

const readDemo = () =>
  JSON.parse(localStorage.getItem(storageKey) || "null") ?? initialOrders;
const saveDemo = (orders) =>
  localStorage.setItem(storageKey, JSON.stringify(orders));
const unwrap = ({ data }) => data?.data ?? data;

export async function listOrders() {
  if (!live) return readDemo();
  const result = [];
  for (let page = 0; ; page++) {
    const { data } = await http.get(endpoint, { params: { page, size: 100 } });
    if (!Array.isArray(data)) throw new Error("工单接口未返回数组。");
    result.push(...data);
    if (data.length < 100) break;
  }
  const orders = Array.isArray(result)
    ? result
    : (result?.records ?? result?.content);
  if (!Array.isArray(orders))
    throw new Error("工单列表响应格式与当前前端约定不一致。");
  return orders;
}

export async function createOrder(form) {
  if (live) {
    const { title, location, description } = form;
    return unwrap(await http.post(endpoint, { title, location, description }));
  }
  const orders = readDemo();
  const id = Math.max(0, ...orders.map((order) => order.id)) + 1;
  saveDemo([{ ...form, id, status: "PENDING" }, ...orders]);
}

export async function actOnOrder(id, action, payload) {
  if (live)
    return unwrap(
      await http.post(
        endpoint + "/" + encodeURIComponent(id) + "/" + action,
        payload,
      ),
    );
  const orders = readDemo();
  const order = orders.find((item) => item.id === id);
  if (!order) throw new Error("工单已不存在。");
  const status =
    action === "accept" && order.status === "WAITING_ACCEPTANCE"
      ? payload.approved
        ? "COMPLETED"
        : "REWORK"
      : transitions[action]?.[order.status];
  if (!status) throw new Error("当前状态无法进行此操作，请刷新工单。");
  order.status = status;
  if (action === "assign") order.repairerId = payload.repairerId;
  if (action === "finish") order.repairResult = payload.description;
  saveDemo(orders);
}

export function errorMessage(error) {
  return error.response?.data?.message || error.message || "操作失败，请重试。";
}

export async function orderWorkflow(id) {
  return unwrap(
    await http.get(endpoint + "/" + encodeURIComponent(id) + "/workflow"),
  );
}

export async function listRepairers() {
  const repairers = [];
  for (let page = 0; ; page++) {
    const { data } = await http.get("/users/repairers", {
      params: { page, size: 100 },
    });
    repairers.push(...data.items);
    if (repairers.length >= data.total || !data.items.length) return repairers;
  }
}
