> V1.1归档后的目录：backend/、frontend/。当前克隆与初始化请先读根README.md；以下后端命令从backend目录执行。

# 宿舍报修管理系统：Vue 前端对接说明

版本：阶段 D，2026-11-23。后端：Spring Boot 3.5.16 / Java 21 / PostgreSQL / JWT / Flowable 7.2.0。

## 1. 本地启动与环境隔离

后端根目录：`<仓库根目录>\backend`。

默认配置指向 `dorm_repair_db:8080`，该正式库尚未迁移，工作流写入和查询返回 503；不要拿默认环境演示完整流程。

前端联调使用已准备好的隔离库 `dorm_repair_flowable_a_test_20261123`，地址 `http://127.0.0.1:18080`。在 PowerShell 中执行：

```powershell
cd backend
$env:INTEGRATION_JWT_SECRET = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
# 数据库要求密码时，另设 INTEGRATION_DB_PASSWORD；不要在聊天或仓库保存实际值。
mvn spring-boot:run "-Dspring-boot.run.profiles=integration"
```

`integration` 使用固定隔离库、独立密钥变量和端口，关闭自动建表。需要先完成隔离库夹具/迁移；本机已准备完毕。其他机器不能仅启动此配置就自动生成业务表，需要按测试准备说明建立独立库。

18080 被占用时先结束你自己启动的联调实例；自动化 HTTP 测试也使用该端口，不要同时运行。退出终端前可删除 INTEGRATION_JWT_SECRET 环境变量；正式 JWT_SECRET 不受影响。

IDEA 运行时激活 `integration` profile，并设置 `INTEGRATION_JWT_SECRET`。只有该 profile 使用这组变量。

### 创建自己知道密码的联调账号

测试自动生成账号的随机密码没有保存。请在交互终端创建独立账号：

```powershell
mvn package -DskipTests
mvn dependency:build-classpath "-Dmdep.outputFile=target/runtime-classpath.txt" "-DincludeScope=runtime"
$cp = "target/classes;" + (Get-Content target/runtime-classpath.txt -Raw).Trim()
$env:DB_URL = "jdbc:postgresql://localhost:5432/dorm_repair_flowable_a_test_20261123"
# 如需数据库密码，工具使用 DB_PASSWORD；不是 INTEGRATION_DB_PASSWORD。
java -cp $cp com.dormrepair.tools.CreateTestUser frontend_student STUDENT
java -cp $cp com.dormrepair.tools.CreateTestUser frontend_repairer REPAIRER
java -cp $cp com.dormrepair.tools.CreateTestUser frontend_admin ADMIN
```

工具交互输入至少 12 字符、最多 72 个 UTF-8 字节的密码，只新增、不覆盖同名用户。创建前务必确认 DB_URL 是隔离库。结束后可清除当前终端 DB_URL、DB_PASSWORD，避免影响其他命令。

## 2. 登录与角色

所有请求与响应均使用 JSON。除登录外，所有业务接口携带：

```http
Authorization: Bearer <accessToken的完整值，不含引号>
```

`POST /api/auth/login`：

```json
{"username":"frontend_student","password":"本地自行设置的密码"}
```

成功 200：

```json
{"accessToken":"<JWT>","tokenType":"Bearer","expiresIn":3600}
```

`GET /api/auth/me` 返回：

```json
{"id":7,"username":"frontend_student","role":"STUDENT"}
```

| 角色 | 允许操作 |
|---|---|
| STUDENT | 创建本人报修、查看本人数据、验收本人维修结果 |
| REPAIRER | 查看分配给本人的数据、开始维修/返工、提交维修结果 |
| ADMIN | 查看全部工单、审核/驳回、派单、查询维修人员 |

操作人从 JWT 获取。创建/维修/验收请求中的旧 `studentId`、`repairerId` 操作人字段会被忽略。只有派单的 `repairerId` 表示管理员选择的目标人员。

登出由客户端删除 Token；已签发 Token 在过期前仍可能有效。没有刷新 Token 接口。用户角色变更需重新登录或等待旧 Token 过期。

## 3. 全部业务接口

下表路径均相对于后端根地址；`id` 为数字工单 ID。

| 方法 | 路径 | 角色 / 参数 | 成功结果 |
|---|---|---|---|
| POST | /api/auth/login | 公开；username、password | Token 对象 |
| GET | /api/auth/me | 已登录 | 当前身份 |
| GET | /api/repair-orders | 三角色；page、size、status 可选 | 工单数组 |
| GET | /api/repair-orders/{id} | 按归属查看 | 工单对象 |
| GET | /api/repair-orders/{id}/records | 按归属查看；page、size | 维修记录分页对象 |
| GET | /api/repair-orders/{id}/workflow | 按归属查看 | 流程对象 |
| POST | /api/repair-orders | STUDENT；title、description、location | message、id |
| POST | /api/repair-orders/{id}/approve | ADMIN，无请求体 | message、id |
| POST | /api/repair-orders/{id}/reject | ADMIN，无请求体 | message、id |
| POST | /api/repair-orders/{id}/assign | ADMIN；repairerId | message、id、repairerId |
| POST | /api/repair-orders/{id}/start | 本单 REPAIRER；无请求体或 {} | message、id |
| POST | /api/repair-orders/{id}/finish | 本单 REPAIRER；description | message、id |
| POST | /api/repair-orders/{id}/accept | 本单 STUDENT；approved 布尔值 | message、id |
| GET | /api/workflow/tasks | 当前角色可处理任务；page、size | 待办分页对象 |
| GET | /api/users/repairers | 仅 ADMIN；page、size | 维修人员分页对象 |

诊断接口 `GET /api/db-test` 仅 ADMIN，返回文本，不作为前端业务功能。`/api/hello` 默认拒绝访问。

学生只能查看自己的工单，维修人员只能查看分配给自己的工单；新详情、records、workflow 对不存在或不可见的工单统一返回 404。已有写操作的归属错误返回 403。

### 创建与操作示例

```json
{"title":"水龙头漏水","description":"持续滴水，需要更换","location":"1栋101"}
```

标题必填且不超过 100 字符，位置必填且不超过 200 字符，描述必填。

成功：

```json
{"message":"报修申请提交成功","id":10}
```

派单请求 / 响应：

```json
{"repairerId":8}
```

```json
{"message":"维修人员分配成功","id":10,"repairerId":8}
```

提交结果请求：

```json
{"description":"已更换水龙头并测试"}
```

验收通过 / 要求返工：

```json
{"approved":true}
```

```json
{"approved":false}
```

其他成功操作仍返回 `message`、`id`，没有强制添加统一 data 包装。前端操作后刷新详情和 workflow。

## 4. 工单列表、详情及维修记录

`GET /api/repair-orders?page=0&size=20&status=PENDING`。保留原数组结构，默认 size=100，最大100；按 ID 倒序。无 status 表示全部状态；传未知状态返回400。没有 total 字段；通过下一页为空判断结束，不要把默认返回的100条误认为全部数据。

详情示例（列表项使用同类字段；列表已有 ADMIN 分支可能不含 repairer_id，请通过详情获取完整字段）：

```json
{
  "id":10,"student_id":7,"repairer_id":8,
  "title":"水龙头漏水","description":"持续滴水，需要更换","location":"1栋101",
  "status":"IN_PROGRESS","created_at":"2026-11-23T20:00:00.000+00:00",
  "updated_at":"2026-11-23T20:10:00.000+00:00"
}
```

`repairer_id` 未派单时为 null。业务表时间是 PostgreSQL 无时区 timestamp，JSON 表示取决于 JVM 时区；前端按约定展示本地时间，不据此做状态判断。ID 当前为数字，大数据量如超出 JS 安全整数需再统一序列化方案，课程演示数据未达此范围。

`GET /api/repair-orders/10/records?page=0&size=20`：按 `created_at asc, id asc` 稳定排序，包含每轮维修记录。

```json
{"items":[{"id":15,"order_id":10,"repairer_id":8,"description":"已更换水龙头","created_at":"2026-11-23T20:20:00.000+00:00"}],"page":0,"size":20,"total":1}
```

维修记录是维修结果，不等同于审核/验收意见；本阶段没有额外意见字段。

## 5. 状态枚举与流程顺序

| 状态 | 中文 |
|---|---|
| PENDING | 待审核 |
| APPROVED | 已审核，待派单 |
| ASSIGNED | 已派单，待开始 |
| IN_PROGRESS | 维修中 |
| WAITING_ACCEPTANCE | 待学生验收 |
| REWORK | 待返工 |
| COMPLETED | 已完成 |
| REJECTED | 已驳回 |

正常：学生 create → 管理员 approve → 管理员 assign → 维修人员 start → finish → 学生 accept(true)。

驳回：学生 create → 管理员 reject → 结束。

返工：正常执行到 finish → 学生 accept(false) → 原维修人员 start → finish → 学生再次 accept；可以多次循环。

重复审核、派单、开始、提交结果、验收返回409。重复创建尚未实现请求幂等，前端应在提交期间禁用按钮。

## 6. 流程查询与 allowedActions

```json
{
  "orderId":10,"mode":"FLOWABLE","status":"IN_PROGRESS",
  "processInstanceId":"流程实例ID","currentNode":"repairFinish",
  "currentTask":{"id":"任务ID","key":"repairFinish","name":"维修并提交结果","assigneeId":8},
  "ended":false,"allowedActions":["finish"]
}
```

currentNode：adminReview、adminAssign、repairStart、repairFinish、studentAcceptance、reworkStart。

allowedActions：approve、reject、assign、start、finish、accept；前端可据此显示按钮，但提交时后端仍会重新校验。空数组表示当前用户不可操作。流程挂起时也返回空数组。查看权限不等于操作权限。

结束流程从历史服务核实结束状态，返回 `ended:true`，`currentNode:null`、`currentTask:null`、`allowedActions:[]`，保留实例ID。历史记录缺失或状态不一致返回409。

历史工单返回：

```json
{"orderId":3,"mode":"LEGACY","status":"COMPLETED","processInstanceId":null,"currentNode":null,"currentTask":null,"ended":true,"allowedActions":[]}
```

历史未完成工单仍可按原业务规则操作，allowedActions按登录角色和状态计算，不伪造Flowable任务。未登记为历史且缺失流程的工单返回409。

任务ID和实例ID仅用于展示/关联，不存在让前端直接完成任务、修改流程变量的接口。

## 7. 待办和维修人员分页

`GET /api/workflow/tasks?page=0&size=20`：

```json
{"items":[{"task_id":"任务ID","task_key":"repairStart","task_name":"开始维修","assignee_id":"8","order_id":10,"title":"水龙头漏水","status":"ASSIGNED"}],"page":0,"size":20,"total":1}
```

管理员看到可处理的审核/派单任务；维修人员看到分给本人的开始、维修和返工任务；学生只看到本人验收任务。过滤暂停实例、不匹配业务状态/负责人及 LEGACY 工单。LEGACY 工单通过普通列表进入，不属于 Flowable 待办。

待办使用固定版本的 Flowable 表进行只读联表查询，避免 N+1。升级引擎版本前必须重跑此查询回归，不向客户端提供引擎表写入能力。

`GET /api/users/repairers?page=0&size=20`：

```json
{"items":[{"id":8,"username":"frontend_repairer","real_name":"维修人员"}],"page":0,"size":20,"total":1}
```

只返回以上三个公开字段，不返回密码哈希或手机号。仅管理员可请求。

分页对象默认 page=0、size=20；page允许0..100000，size允许1..100。超出或非整数返回400，不自动截断。超出最后一页返回空items并保留total。无记录返回items=[]、total=0。列表接口例外保留数组，默认size=100。

## 8. 错误码与前端建议

```json
{"code":409,"message":"Order state or workflow conflict; reload before retrying"}
```

| HTTP | 语义 | 前端处理 |
|---|---|---|
| 400 | 字段、JSON、分页或状态筛选参数错误 | 提示用户修正 |
| 401 | 未登录、Token无效/过期、登录失败 | 清除失效Token并转登录；登录失败留在登录页 |
| 403 | 角色或写操作归属不允许 | 提示无权限，刷新角色/详情 |
| 404 | 工单不存在或查询不可见 | 统一显示“工单不存在或无法查看” |
| 409 | 重复操作、状态变化、流程不一致 | 刷新详情与workflow，不自动重复提交 |
| 503 | 工作流未启用或数据库暂不可用 | 提示服务未就绪，稍后重试 |

意外内部错误仍可能返回500；不要将数据库或程序异常当成密码错误。不要在日志记录密码、JWT、Authorization请求头。按钮权限只作交互提示，不能代替服务端校验。

## 9. Axios、CORS 与 Vite 代理示例

建议开发时通过Vite代理发送相对路径，避免localhost/127.0.0.1来源差异：

```js
// vite.config.js 示例，仅供前端成员添加；本轮未修改前端文件。
export default {
  server: {
    proxy: {
      '/api': { target: 'http://127.0.0.1:18080', changeOrigin: true }
    }
  }
}
```

```js
import axios from 'axios'
const api = axios.create({ baseURL: '/api', timeout: 15000 })
api.interceptors.request.use(config => {
  const token = sessionStorage.getItem('accessToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
const { data } = await api.post('/auth/login', { username, password })
sessionStorage.setItem('accessToken', data.accessToken)
// 后续 api.get('/auth/me')、api.get('/workflow/tasks', { params: { page: 0, size: 20 } })
```

直接跨域请求时，后端默认允许 `http://localhost:5173`。如前端从127.0.0.1访问，设置后端 `CORS_ORIGINS=http://localhost:5173,http://127.0.0.1:5173`。允许GET/POST/OPTIONS和Authorization/Content-Type，不使用Cookie会话，不需要withCredentials。

## 10. 已验证和仍需联调

已在真实PostgreSQL、真实Tomcat和HTTP验证登录、安全隔离、正常/驳回/多轮返工、并发、异常回滚及新查询接口。

新增查询测试覆盖三角色、404隔离、维修记录分页和顺序、流程结束历史、LEGACY、allowedActions、任务分页及维修人员字段白名单。

完整命令：

```powershell
mvn -B test "-Dflowable.stageA=true" "-Dflowable.stageB=true" "-Dflowable.stageC=true" "-Dflowable.stageD=true"
```

真实HTTP测试期间会占用18080，结束后自动关闭。手工联调使用integration profile独立启动；自动化测试随机账号不作为共享账号交付。

仍需Vue联调：路由守卫、表单提示、页面刷新、Token过期跳转、浏览器CORS/代理、中文和时间显示。正式库尚未迁移，完整正式环境流程未执行。复杂统计、上传、通知、复杂轨迹和分页性能压测不在本阶段范围。


最终验收记录：完整回归65项通过（含复用场景的不同执行环境），阶段D查询套件20项/379次HTTP，阶段C套件14项/216次HTTP，另联调profile真实HTTP冒烟1次。迁移仅在独立演练库执行，正式库保持不变。测试报告为docs/v1.1/FLOWABLE-STAGE-D.txt。
