<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import {
  actOnOrder,
  createOrder,
  errorMessage,
  listOrders,
  live,
  orderWorkflow,
  listRepairers,
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
const orders = ref([]);
const repairers = ref([]);
const checkingAction = ref(false);
const identity = ref(props.role === "repairer" ? 2 : 1);
const loading = ref(false);
const saving = ref(false);
const error = ref("");
const notice = ref("");
const filter = ref("");
const form = reactive({ title: "", location: "", description: "" });
const dialog = reactive({
  open: false,
  order: null,
  action: "",
  label: "",
  repairerId: 2,
  description: "",
});

const scoped = computed(() => {
  if (live || props.role === "admin") return orders.value;
  const field = props.role === "student" ? "studentId" : "repairerId";
  return orders.value.filter(
    (order) => identity.value && Number(order[field]) === identity.value,
  );
});
const visible = computed(() =>
  scoped.value.filter(
    (order) => !filter.value || order.status === filter.value,
  ),
);
const pending = computed(
  () =>
    scoped.value.filter((order) => actions[props.role][order.status]?.length)
      .length,
);
const completed = computed(
  () => scoped.value.filter((order) => order.status === "COMPLETED").length,
);

async function refresh() {
  loading.value = true;
  error.value = "";
  try {
    orders.value = await listOrders();
  } catch (failure) {
    error.value = errorMessage(failure);
  } finally {
    loading.value = false;
  }
}

async function mutate(operation, message) {
  saving.value = true;
  error.value = "";
  notice.value = "";
  try {
    await operation();
    notice.value = message;
    await refresh();
    return true;
  } catch (failure) {
    if (failure.response?.status === 409) {
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
  const payload = {
    studentId: identity.value,
    title: form.title.trim(),
    location: form.location.trim(),
    description: form.description.trim(),
  };
  if (
    !payload.studentId ||
    !payload.title ||
    !payload.location ||
    !payload.description
  ) {
    error.value = "请填写学生 ID、报修标题、地点和故障描述。";
    return;
  }
  if (await mutate(() => createOrder(payload), "报修已提交")) {
    Object.assign(form, { title: "", location: "", description: "" });
    filter.value = "";
  }
}

async function openAction(order, action, label) {
  if (checkingAction.value) return;
  checkingAction.value = true;
  error.value = "";
  try {
    if (live) {
      const workflow = await orderWorkflow(order.id);
      if (
        !workflow.allowedActions.includes(
          action === "rework" ? "accept" : action,
        )
      ) {
        await refresh();
        error.value = "当前流程不允许此操作，工单已刷新。";
        return;
      }
      if (action === "assign") repairers.value = await listRepairers();
    }
  } catch (failure) {
    error.value = errorMessage(failure);
    return;
  } finally {
    checkingAction.value = false;
  }
  Object.assign(dialog, {
    open: true,
    order,
    action,
    label,
    repairerId: live ? undefined : (order.repairerId ?? 2),
    description: "",
  });
  error.value = "";
}

async function confirmAction() {
  const { order, action, description, repairerId } = dialog;
  if (
    (action === "assign" && !repairerId) ||
    (action === "finish" && !description.trim())
  ) {
    error.value = "请填写维修人员 ID 或维修结果。";
    return;
  }
  let payload;
  if (action === "assign") payload = { repairerId };
  if (action === "start" || action === "finish")
    payload = { repairerId: identity.value };
  if (action === "finish") payload.description = description.trim();
  if (live && action === "start") payload = undefined;
  if (live && action === "finish")
    payload = { description: description.trim() };
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
      <span>全部工单</span><strong>{{ scoped.length }}</strong>
    </div>
    <div>
      <span>当前待处理</span><strong>{{ pending }}</strong>
    </div>
    <div>
      <span>已完成</span><strong>{{ completed }}</strong>
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
            maxlength="80"
            placeholder="例如：水龙头漏水"
        /></el-form-item>
        <el-form-item label="宿舍地点"
          ><el-input
            v-model="form.location"
            maxlength="100"
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
      <el-button type="primary" native-type="submit" :loading="saving"
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
      empty-text="暂无符合条件的工单"
    >
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="details">
            <p>{{ row.description || "暂无故障描述" }}</p>
            <p v-if="row.repairResult">维修结果：{{ row.repairResult }}</p>
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
            v-for="[key, label] in actions[role][row.status] || []"
            :key="key"
            link
            type="primary"
            :disabled="saving"
            @click="openAction(row, key, label)"
            >{{ label }}</el-button
          >
        </template>
      </el-table-column>
    </el-table>
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
        >
          <el-option
            v-for="person in repairers"
            :key="person.id"
            :value="person.id"
            :label="person.real_name || person.username"
          />
        </el-select>
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
      ><el-button type="primary" :loading="saving" @click="confirmAction"
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
