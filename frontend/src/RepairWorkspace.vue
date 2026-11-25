<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import {
  actOnOrder,
  createOrder,
  errorMessage,
  listOrders,
  live,
  orderWorkflow,
  listRepairers,
  orderDetail,
  repairRecords,
  pendingTasks,
} from "./orders";
const props = defineProps({ role: { type: String, required: true } });
const labels = {
  student: "学生工作区",
  admin: "管理工作区",
  repairer: "维修工作区",
};
const statuses = {
  PENDING: "待审核",
  APPROVED: "待派单",
  REJECTED: "已驳回",
  ASSIGNED: "待维修",
  IN_PROGRESS: "维修中",
  WAITING_ACCEPTANCE: "待验收",
  REWORK: "返修",
  COMPLETED: "已完成",
};
const actions = {
  student: {
    WAITING_ACCEPTANCE: [
      ["accept", "验收通过"],
      ["rework", "申请返修"],
    ],
  },
  admin: {
    PENDING: [
      ["approve", "审核通过"],
      ["reject", "驳回"],
    ],
    APPROVED: [["assign", "派单"]],
  },
  repairer: {
    ASSIGNED: [["start", "开始维修"]],
    REWORK: [["start", "开始返工"]],
    IN_PROGRESS: [["finish", "提交维修结果"]],
  },
};
const orders = ref([]),
  repairers = ref([]),
  checkingAction = ref(false),
  identity = ref(props.role === "repairer" ? 2 : 1),
  loading = ref(false),
  saving = ref(false),
  error = ref(""),
  notice = ref(""),
  filter = ref("");
const page = ref(0),
  pageSize = ref(20),
  taskTotal = ref(null),
  workflows = ref({}),
  workflowErrors = ref({}),
  details = reactive({});
const expandedIds = ref([]);
const repairerPage = ref(0),
  repairerTotal = ref(0),
  repairersLoading = ref(false),
  repairerPageSize = 20;
let refreshVersion = 0;
const form = reactive({ title: "", location: "", description: "" });
const dialog = reactive({
  open: false,
  order: null,
  action: "",
  label: "",
  repairerId: 2,
  description: "",
});
const scoped = computed(() => orders.value);
const visible = computed(() => orders.value);
const pending = computed(() =>
  live
    ? taskTotal.value
    : orders.value.filter((o) => actions[props.role][o.status]?.length).length,
);
const completed = computed(
  () => orders.value.filter((o) => o.status === "COMPLETED").length,
);
function availableActions(order) {
  const candidates = actions[props.role][order.status] || [];
  if (!live) return candidates;
  const allowed = workflows.value[order.id]?.allowedActions || [];
  return candidates.filter(([key]) =>
    allowed.includes(key === "rework" ? "accept" : key),
  );
}
async function bounded(items, operation) {
  let cursor = 0;
  await Promise.all(
    Array.from({ length: Math.min(4, items.length) }, async () => {
      while (cursor < items.length) await operation(items[cursor++]);
    }),
  );
}
async function refresh() {
  const version = ++refreshVersion;
  loading.value = true;
  error.value = "";
  try {
    const [rows, tasks] = await Promise.allSettled([
      listOrders({
        page: page.value,
        size: pageSize.value,
        status: filter.value,
        role: props.role,
        identity: identity.value,
      }),
      live ? pendingTasks() : Promise.resolve({ total: 0 }),
    ]);
    if (version !== refreshVersion) return;
    if (rows.status === "rejected") throw rows.reason;
    orders.value = rows.value;
    taskTotal.value = tasks.status === "fulfilled" ? tasks.value.total : null;
    if (tasks.status === "rejected")
      error.value = "待办统计暂不可用：" + errorMessage(tasks.reason);
    workflows.value = {};
    workflowErrors.value = {};
    Object.keys(details).forEach((id) => delete details[id]);
    expandedIds.value = expandedIds.value.filter((id) =>
      rows.value.some((order) => order.id === id),
    );
    if (live)
      await bounded(
        rows.value.filter((o) => actions[props.role][o.status]?.length),
        async (order) => {
          try {
            const flow = await orderWorkflow(order.id);
            if (version === refreshVersion) workflows.value[order.id] = flow;
          } catch (failure) {
            if (version === refreshVersion)
              workflowErrors.value[order.id] = errorMessage(failure);
          }
        },
      );
    if (version === refreshVersion)
      await bounded(
        rows.value.filter((order) => expandedIds.value.includes(order.id)),
        (order) => loadDetails(order),
      );
  } catch (failure) {
    if (version === refreshVersion) {
      orders.value = [];
      taskTotal.value = null;
      error.value = errorMessage(failure);
    }
  } finally {
    if (version === refreshVersion) loading.value = false;
  }
}
async function goPage(value) {
  if (loading.value || saving.value || value < 0) return;
  page.value = value;
  await refresh();
}
watch([filter, pageSize], () => {
  page.value = 0;
  void refresh();
});
async function loadDetails(order, recordPage = 0) {
  const version = refreshVersion;
  details[order.id] = { ...details[order.id], loading: true, error: "" };
  try {
    const [data, workflow, records] = await Promise.all([
      orderDetail(order.id),
      orderWorkflow(order.id),
      repairRecords(order.id, recordPage, 20),
    ]);
    if (version !== refreshVersion) return;
    details[order.id] = { data, workflow, records, loading: false, error: "" };
    workflows.value[order.id] = workflow;
  } catch (failure) {
    if (version !== refreshVersion) return;
    details[order.id] = {
      ...details[order.id],
      loading: false,
      error: errorMessage(failure),
    };
  }
}
function expandDetails(order, expanded) {
  expandedIds.value = expanded.map((item) => item.id);
  if (expanded.some((item) => item.id === order.id)) void loadDetails(order);
}
async function loadRepairers(value = 0) {
  if (repairersLoading.value || value < 0) return;
  repairersLoading.value = true;
  try {
    const result = await listRepairers(value, repairerPageSize);
    repairers.value = result.items;
    repairerTotal.value = result.total;
    repairerPage.value = value;
  } catch (failure) {
    error.value = errorMessage(failure);
  } finally {
    repairersLoading.value = false;
  }
}
async function mutate(operation, message) {
  if (saving.value) return false;
  saving.value = true;
  error.value = "";
  notice.value = "";
  try {
    await operation();
    notice.value = message;
    await refresh();
    return true;
  } catch (failure) {
    if ([403, 404, 409].includes(failure.response?.status)) {
      dialog.open = false;
      await refresh();
    }
    error.value = errorMessage(failure);
    return false;
  } finally {
    saving.value = false;
  }
}
async function submit() {
  if (saving.value || loading.value) return;
  const payload = {
    studentId: identity.value,
    title: form.title.trim(),
    location: form.location.trim(),
    description: form.description.trim(),
  };
  if (!payload.title || !payload.location || !payload.description) {
    error.value = "请填写报修标题、地点和故障描述。";
    return;
  }
  if (
    await mutate(async () => {
      await createOrder(payload);
      page.value = 0;
      filter.value = "";
    }, "报修已提交")
  )
    Object.assign(form, { title: "", location: "", description: "" });
}
async function openAction(order, action, label) {
  if (checkingAction.value || saving.value || loading.value) return;
  checkingAction.value = true;
  error.value = "";
  try {
    if (live) {
      const flow = await orderWorkflow(order.id);
      workflows.value[order.id] = flow;
      if (
        !flow.allowedActions.includes(action === "rework" ? "accept" : action)
      ) {
        await refresh();
        error.value = "当前流程不允许此操作，请使用刷新后的工单。";
        return;
      }
      if (action === "assign") await loadRepairers(0);
    }
    Object.assign(dialog, {
      open: true,
      order,
      action,
      label,
      repairerId: live ? undefined : (order.repairerId ?? 2),
      description: "",
    });
  } catch (failure) {
    error.value = errorMessage(failure);
  } finally {
    checkingAction.value = false;
  }
}
async function confirmAction() {
  if (saving.value || repairersLoading.value || !dialog.order) return;
  const { order, action, description, repairerId } = dialog;
  if (
    (action === "assign" && !repairerId) ||
    (action === "finish" && !description.trim())
  ) {
    error.value = action === "assign" ? "请选择维修人员。" : "请填写维修结果。";
    return;
  }
  let payload;
  if (action === "assign") payload = { repairerId };
  if (action === "finish")
    payload = live
      ? { description: description.trim() }
      : { repairerId: identity.value, description: description.trim() };
  if (action === "start" && !live) payload = { repairerId: identity.value };
  if (action === "accept" || action === "rework")
    payload = live
      ? { approved: action === "accept" }
      : { studentId: identity.value, approved: action === "accept" };
  if (
    await mutate(
      () =>
        actOnOrder(order.id, action === "rework" ? "accept" : action, payload),
      "工单已更新",
    )
  )
    dialog.open = false;
}
onMounted(refresh);
</script>

<template>
  <header>
    <h1>{{ labels[role] }}</h1>
    <p>查看报修进度并处理当前工单。</p>
  </header>
  <section class="metrics" aria-label="工单统计">
    <div>
      <span>本页工单</span><strong>{{ scoped.length }}</strong>
    </div>
    <div>
      <span>当前待处理</span><strong>{{ pending ?? "—" }}</strong>
    </div>
    <div>
      <span>本页已完成</span><strong>{{ completed }}</strong>
    </div>
  </section>

  <el-alert
    v-if="error && !dialog.open"
    :title="error"
    type="error"
    @close="error = ''"
  />
  <el-alert v-if="notice" :title="notice" type="success" @close="notice = ''" />

  <section v-if="!live && role !== 'admin'" class="panel identity">
    <label for="identity"
      >联调{{ role === "student" ? "学生" : "维修人员" }} ID</label
    >
    <el-input-number
      id="identity"
      v-model="identity"
      :min="1"
      :precision="0"
      :disabled="saving || dialog.open"
    />
  </section>

  <section v-if="role === 'student'" class="panel">
    <h2>提交报修</h2>
    <el-form label-position="top" @submit.prevent="submit">
      <div class="fields">
        <el-form-item label="报修标题"
          ><el-input
            v-model="form.title"
            maxlength="100"
            placeholder="例如：水龙头漏水"
        /></el-form-item>
        <el-form-item label="宿舍地点"
          ><el-input
            v-model="form.location"
            maxlength="200"
            placeholder="楼栋和房间号"
        /></el-form-item>
      </div>
      <el-form-item label="故障描述"
        ><el-input
          v-model="form.description"
          type="textarea"
          maxlength="500"
          :rows="3"
      /></el-form-item>
      <el-button
        type="primary"
        native-type="submit"
        :loading="saving"
        :disabled="loading"
        >提交报修</el-button
      >
    </el-form>
  </section>

  <section class="panel">
    <div class="toolbar">
      <h2>工单列表</h2>
      <div class="controls">
        <el-select
          v-model="filter"
          clearable
          placeholder="所有状态"
          aria-label="筛选工单状态"
          :disabled="loading || saving"
        >
          <el-option
            v-for="(name, value) in statuses"
            :key="value"
            :label="name"
            :value="value"
          />
        </el-select>
        <el-button :loading="loading" @click="refresh">刷新</el-button>
      </div>
    </div>
    <el-table
      v-loading="loading"
      :data="visible"
      row-key="id"
      @expand-change="expandDetails"
      empty-text="暂无符合条件的工单"
    >
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="details" v-loading="details[row.id]?.loading">
            <el-alert
              v-if="details[row.id]?.error"
              :title="details[row.id].error"
              type="error"
              :closable="false"
            />
            <template v-else-if="details[row.id]?.data">
              <h3>工单详情</h3>
              <p>{{ details[row.id].data.description || "暂无故障描述" }}</p>
              <p>地点：{{ details[row.id].data.location }}</p>
              <p>
                流程模式：{{
                  details[row.id].workflow.mode === "LEGACY"
                    ? "LEGACY 历史工单（无流程实例）"
                    : "FLOWABLE"
                }}
              </p>
              <p>
                当前节点：{{
                  details[row.id].workflow.currentTask?.name ||
                  (details[row.id].workflow.mode === "LEGACY"
                    ? "历史业务状态：" + statuses[row.status]
                    : "无当前任务")
                }}
              </p>
              <p>
                {{
                  details[row.id].workflow.ended ? "流程已结束" : "流程处理中"
                }}
              </p>
              <h3>维修记录（{{ details[row.id].records.total }} 条）</h3>
              <p v-if="!details[row.id].records.items.length">暂无维修记录</p>
              <div
                v-for="(record, index) in details[row.id].records.items"
                :key="record.id"
              >
                <p>
                  <strong
                    >第
                    {{
                      details[row.id].records.page *
                        details[row.id].records.size +
                      index +
                      1
                    }}
                    次维修</strong
                  >
                  · 维修人员 {{ record.repairer_id }} · {{ record.created_at }}
                </p>
                <p>{{ record.description }}</p>
              </div>
              <div class="controls">
                <el-button
                  size="small"
                  :disabled="
                    details[row.id].loading ||
                    details[row.id].records.page === 0
                  "
                  @click="loadDetails(row, details[row.id].records.page - 1)"
                  >上一页记录</el-button
                >
                <el-button
                  size="small"
                  :disabled="
                    details[row.id].loading ||
                    (details[row.id].records.page + 1) *
                      details[row.id].records.size >=
                      details[row.id].records.total
                  "
                  @click="loadDetails(row, details[row.id].records.page + 1)"
                  >下一页记录</el-button
                >
              </div>
            </template>
            <p v-else>正在加载工单详情…</p>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="id" label="编号" width="72" />
      <el-table-column prop="title" label="报修事项" min-width="145" />
      <el-table-column prop="location" label="地点" min-width="125" />
      <el-table-column label="状态" width="115">
        <template #default="{ row }"
          ><el-tag
            :type="
              row.status === 'COMPLETED'
                ? 'success'
                : row.status === 'REJECTED'
                  ? 'danger'
                  : 'info'
            "
            >{{ statuses[row.status] || row.status }}</el-tag
          ></template
        >
      </el-table-column>
      <el-table-column label="操作" min-width="170">
        <template #default="{ row }">
          <el-button
            v-for="[key, label] in availableActions(row)"
            :key="key"
            link
            type="primary"
            :disabled="saving || loading || checkingAction"
            @click="openAction(row, key, label)"
            >{{ label }}</el-button
          >
          <span
            v-if="live && workflowErrors[row.id]"
            :title="workflowErrors[row.id]"
            >操作权限暂不可用，请刷新</span
          >
        </template>
      </el-table-column>
    </el-table>
    <div class="controls" style="margin-top: 16px">
      <el-button
        :disabled="loading || saving || page === 0"
        @click="goPage(page - 1)"
        >上一页工单</el-button
      >
      <span>第 {{ page + 1 }} 页（每页 {{ pageSize }} 条）</span>
      <el-button
        :disabled="loading || saving || orders.length < pageSize"
        @click="goPage(page + 1)"
        >下一页工单</el-button
      >
      <el-select
        v-model="pageSize"
        aria-label="每页工单数量"
        :disabled="loading || saving"
        style="width: 110px"
      >
        <el-option
          v-for="size in [20, 50, 100]"
          :key="size"
          :label="size + '条/页'"
          :value="size"
        />
      </el-select>
    </div>
  </section>

  <el-dialog
    v-model="dialog.open"
    :title="dialog.label"
    width="min(440px, 94vw)"
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
    :show-close="!saving"
  >
    <p>工单 #{{ dialog.order?.id }} · {{ dialog.order?.title }}</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form label-position="top">
      <el-form-item v-if="dialog.action === 'assign'" label="维修人员"
        ><el-input-number
          v-if="!live"
          v-model="dialog.repairerId"
          :min="1"
          :precision="0"
        />
        <el-select
          v-else
          v-model="dialog.repairerId"
          placeholder="选择维修人员"
          :loading="repairersLoading"
          :disabled="repairersLoading || saving"
        >
          <el-option
            v-for="person in repairers"
            :key="person.id"
            :value="person.id"
            :label="person.real_name || person.username"
          />
        </el-select>
        <div v-if="live" class="controls" style="margin-top: 8px">
          <el-button
            size="small"
            :disabled="repairersLoading || saving || repairerPage === 0"
            @click="loadRepairers(repairerPage - 1)"
            >上一页人员</el-button
          >
          <span
            >第 {{ repairerPage + 1 }} 页 / 共
            {{ Math.max(1, Math.ceil(repairerTotal / repairerPageSize)) }}
            页</span
          >
          <el-button
            size="small"
            :disabled="
              repairersLoading ||
              saving ||
              (repairerPage + 1) * repairerPageSize >= repairerTotal
            "
            @click="loadRepairers(repairerPage + 1)"
            >下一页人员</el-button
          >
          <el-button
            size="small"
            :disabled="repairersLoading || saving || repairerTotal === 0"
            @click="
              loadRepairers(
                Math.max(0, Math.ceil(repairerTotal / repairerPageSize) - 1),
              )
            "
            >末页人员</el-button
          >
          <span v-if="!repairersLoading && repairerTotal === 0"
            >暂无可用维修人员</span
          >
        </div>
      </el-form-item>
      <el-form-item v-if="dialog.action === 'finish'" label="维修结果"
        ><el-input
          v-model="dialog.description"
          type="textarea"
          :rows="3"
          maxlength="500"
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button :disabled="saving" @click="dialog.open = false"
        >取消</el-button
      ><el-button
        type="primary"
        :loading="saving"
        :disabled="repairersLoading || loading"
        @click="confirmAction"
        >确认</el-button
      ></template
    >
  </el-dialog>
</template>

<style scoped>
h1 {
  font-size: 34px;
  margin: 26px 0 5px;
}
h2 {
  font-size: 18px;
  margin: 0 0 18px;
}
header p,
.metrics span {
  color: #52687a;
}
.metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin: 23px 0 18px;
}
.metrics div,
.panel {
  border: 1px solid #dce5ea;
  background: #fff;
  border-radius: 12px;
  padding: 18px;
}
.metrics strong {
  display: block;
  font-size: 28px;
  margin-top: 4px;
}
.panel,
.el-alert {
  margin-bottom: 16px;
}
.identity,
.toolbar,
.controls {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.toolbar {
  justify-content: space-between;
  margin-bottom: 12px;
}
.toolbar h2 {
  margin: 0;
}
.controls .el-select {
  width: 145px;
}
.fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.details {
  padding: 0 24px;
  white-space: pre-wrap;
}
@media (max-width: 650px) {
  .fields {
    grid-template-columns: 1fr;
    gap: 0;
  }
  .metrics div,
  .panel {
    padding: 12px;
  }
  h1 {
    font-size: 26px;
  }
}
</style>
