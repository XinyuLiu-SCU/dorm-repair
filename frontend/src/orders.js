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

export async function listOrders({
  page = 0,
  size = 20,
  status = "",
  role,
  identity,
} = {}) {
  if (!live) {
    let rows = readDemo();
    if (role !== "admin")
      rows = rows.filter(
        (o) =>
          Number(o[role === "student" ? "studentId" : "repairerId"]) ===
          identity,
      );
    if (status) rows = rows.filter((o) => o.status === status);
    return rows.slice(page * size, (page + 1) * size);
  }
  const { data } = await http.get(endpoint, {
    params: { page, size, status: status || undefined },
  });
  if (!Array.isArray(data)) throw new Error("工单接口未返回数组。");
  return data;
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
  if (action === "finish")
    order.records = [
      ...(order.records || []),
      {
        id: Date.now(),
        repairer_id: order.repairerId,
        description: payload.description,
        created_at: new Date().toISOString(),
      },
    ];
  saveDemo(orders);
}

export function errorMessage(error) {
  if (error.code === "ERR_NETWORK")
    return "网络连接失败，请检查后端服务并重试。";
  const messages = {
    400: "输入参数不正确，请检查后重试。",
    401: "登录已过期，请重新登录。",
    403: "没有执行此操作的权限。",
    404: "工单不存在或无法查看。",
    409: "工单状态已变化，请刷新后再操作。",
    503: "服务暂不可用，请稍后重试。",
  };
  return (
    messages[error.response?.status] ||
    (error.code === "ECONNABORTED"
      ? "请求超时，请确认结果后刷新，不要重复提交。"
      : error.response?.data?.message || error.message || "操作失败，请重试。")
  );
}

export async function orderWorkflow(id) {
  if (!live) {
    const o = readDemo().find((x) => x.id === id);
    return {
      mode: "LEGACY",
      status: o.status,
      ended: ["COMPLETED", "REJECTED"].includes(o.status),
      currentTask: null,
      allowedActions: [],
    };
  }
  return unwrap(
    await http.get(endpoint + "/" + encodeURIComponent(id) + "/workflow"),
  );
}

export async function listRepairers(page = 0, size = 20) {
  const { data } = await http.get("/users/repairers", {
    params: { page, size },
  });
  return data;
}
export async function orderDetail(id) {
  if (!live) return readDemo().find((o) => o.id === id);
  return unwrap(await http.get(endpoint + "/" + encodeURIComponent(id)));
}
export async function repairRecords(id, page = 0, size = 20) {
  if (!live) {
    const records = readDemo().find((o) => o.id === id)?.records || [];
    return {
      items: records.slice(page * size, (page + 1) * size),
      page,
      size,
      total: records.length,
    };
  }
  return unwrap(
    await http.get(endpoint + "/" + encodeURIComponent(id) + "/records", {
      params: { page, size },
    }),
  );
}
export async function pendingTasks() {
  return unwrap(
    await http.get("/workflow/tasks", { params: { page: 0, size: 1 } }),
  );
}
