# V2.0 Final 核心闭环前端接口

## 连接与身份

启动：配置独立的 `V2_DB_USER`、按需 `V2_DB_PASSWORD` 及随机 `V2_JWT_SECRET` 后运行 `./scripts/start-v2.ps1`。脚本启用 `v2-dev,v2-core`，只允许本机 V2 隔离开发库，监听 `127.0.0.1:18081`；业务 Flyway 与引擎表管理分开，异步执行器关闭。不要给正式库启用此配置。

沿用 B 阶段 `POST /api/v2/auth/login`、`GET /api/v2/auth/me`、`Authorization: Bearer <accessToken>`。响应所有业务 ID 为字符串；列表 `{items,page,size,total}`，page 从 0 开始，size 为 1–100。读取最新 version 后再写入，409 时刷新，不盲目重放。

业务管理员使用现有 `legacy:admin`；维修人员需 `legacy:repairer` 且是当前指派人；学生需 `draft:own` 且是本人资料/工单。账号及权限管理继续使用 `user:manage`、`role:manage` 等既有权限。用原有角色配置区分超级管理员与普通业务管理员，本轮没有新建权限模块或按角色名称授权。

## 正式成单

先用 D/E 接口保存完整资料、明确修订及图片，随后：

```http
POST /api/v2/drafts/12/submit
Content-Type: application/json
Authorization: Bearer <accessToken>

{"version":2,"revisionId":"35"}
```

只接受本人 EDITABLE 资料的当前明确修订；不接受旧修订或未完成资料。提交时重新验证权限、有效分类及项目、空间、地址、入住、设施范围与关联及时间窗口。私人房间地址需当前有效的管理确认入住关系；自行填写地址不产生授权。修改后的源位置不一致时返回冲突，需重新保存资料。

正式工单、冻结 snapshot、附件修订引用、真实 `dormRepairV2` 实例及资料 CONVERTED 标记在同一事务创建；失败原资料仍可编辑或重试；唯一 draft_id 和行锁防重复。不产生无流程的降级工单。提交成功返回工单详情，不能把 D 阶段 complete 或旧 `formalSubmissionAvailable:false` 当作提交成功。

```json
{"id":"18","status":"PENDING","stage":"REVIEW","version":0,"roundNo":1,"draftId":"12","revisionId":"35","processInstanceId":"真实引擎ID","processDefinitionId":"真实流程定义ID","snapshot":{"description":"水龙头漏水","itemId":"9","addressSnapshot":{"contactName":"虚构联系人","phone":"虚构联系电话"},"timeMode":"ANY","estimatedMinutes":30},"allowedActions":[],"appointments":[],"appointmentsTotal":0,"records":[],"recordsTotal":0,"evaluation":null}
```

allowedActions 随当前登录人变化，上例为学生在审核节点。snapshot 保存结构化修订资料，不随源名称、地址及设施修改变化。仍保存原 V1 status 兼容值，V2 页面按 stage 和实际 tasks 展示流程。

## 工单查询与动作

以下路径均带 `/api/v2` 前缀，动作均 POST。所有写操作必须携带当前 `version`，除表中特别字段外不接受 ownerId、操作者 ID 或任意流程变量。

|路径|请求字段（除 version）|可执行人/动作|
|---|---|---|
|`GET /orders?page=0&size=20&stage=REVIEW`|stage 可省略|学生本人、维修当前指派、业务管理员全部 V2 工单|
|`GET /orders/{id}`|无|同上；无权或不存在均 404|
|`/orders/{id}/review`|approved:boolean，note 可选|管理员；REVIEW|
|`/orders/{id}/assign`|repairerId:string，note 可选|管理员；ASSIGN；重新校验技能、账号、负载和可用时间|
|`/orders/{id}/appointment`|startAt、endAt（带时区 ISO 时间），reason 可选|当前维修人员；PROPOSE_APPOINTMENT|
|`/orders/{id}/appointment/decision`|accepted:boolean，reason 可选|本人工单学生；DECIDE_APPOINTMENT|
|`/orders/{id}/start`|无|当前维修人员；START_REPAIR|
|`/orders/{id}/result`|description，materials 可选数组|当前维修人员；SUBMIT_RESULT|
|`/orders/{id}/exception`|type、reason|当前维修人员；REPORT_EXCEPTION|
|`/orders/{id}/exception/handle`|decision、reason|管理员；HANDLE_EXCEPTION|
|`/orders/{id}/acceptance`|accepted:boolean；不通过时 reason 必填|学生本人；ACCEPT|
|`/orders/{id}/evaluation`|stars:1–5整数，comment 可选|完成后学生本人；EVALUATE；仅一次|
|`GET /orders/{id}/records`|page、size|授权参与者，真实维修记录|
|`GET /orders/{id}/appointments`|page、size|授权参与者，全部协商历史|
|`GET /orders/{id}/events`|page、size|授权参与者，材料、轮次、异常原因与操作事件|

详情内 records/appointments 各最多 100 条，超过时通过上述分页接口读取；total 明确返回，不限制返工轮数。结果示例：

```json
{"version":6,"description":"更换密封圈后测试无漏水","materials":[{"name":"密封圈","quantity":2}]}
```

材料最多 20 项，数量为正数、最多三位小数且不超过 100000；只登记使用情况，没有库存或成本能力。materials 与实际 repair_recordId、round 关联保存在 RESULT_SUBMITTED 事件 details，维修描述在真实 repair_record 中。

预约：ANY 或在学生原 WINDOW 内可由维修人员直接确认；超出窗口只创建 PROPOSED，必须学生确认后才能开始。学生拒绝后回到维修人员协商节点。时间须在维修人员可用窗口内、未来、长度不少于快照预计工时，且不得与其他未完成工单的已确认预约重叠。修改排班若会破坏已确认预约则 409，需先协调处理。无人值守字段仅表示原意愿，不授予实际入室权限。

异常 type：`UNABLE_ENTRY`、`MISSING_MATERIAL`、`REASSIGN`；管理员 decision：`RESUME` 或 `REASSIGN`。暂停停在真实异常任务；恢复回开始维修；改派取消当前预约并清除当前指派，重新派单及预约，原维修人员立即失去该单访问权。理由留痕，不建立独立异常平台。

验收不通过回到原维修人员的开始维修节点，roundNo 加一；不强制限制返工次数；每轮分别形成维修记录。完成后只允许一次五星及文字评价。

## 技能、可用时间、推荐

|接口|用途|
|---|---|
|`GET /workers?page=0&size=20`|业务管理员分页查看已配置维修人员、技能、可用时间和实际在手负载|
|`PUT /workers/{userId}`|业务管理员配置；目标必须启用且具备维修权限|
|`GET /workers/me`|维修人员本人配置与最多 100 个有效预约|
|`PUT /workers/me/availability`|维修人员本人只改 availability 和 version，不得自增技能/容量|
|`GET /orders/{id}/recommendations`|业务管理员取得前三名及过滤原因；不会自动派单|

```json
{"version":0,"baseBuildingId":"2","capacity":3,"skills":[{"itemId":"9","level":5}],"availability":[{"startAt":"2030-01-01T08:00:00+08:00","endAt":"2030-01-01T18:00:00+08:00"}]}
```

示例日期需替换为实际未来时间。version=0 创建，修改用服务端返回版本。技能按具体 itemId 显式配置，不继承所有子项。capacity 为 1–20，skills 最多 100 项、availability 最多 50 个不重叠窗口；当前工单负载来自数据库，不接受前端覆盖。

推荐先排除权限失效、技能不符、容量已满、学生窗口内无可用时间；按技能 50%、负载 30%、位置便利度 20% 评分。位置只比较同楼/同校区，不伪造空间距离。返回：

```json
{"candidates":[{"repairerId":"8","realName":"虚构维修员","score":100.0,"components":{"skill":100.0,"load":100.0,"location":100.0},"activeLoad":0,"suggestedStart":"未来带时区时间","suggestedEnd":"未来带时区时间"}],"excluded":[{"repairerId":"10","reason":"TIME_CONFLICT"}],"weights":{"skill":0.5,"load":0.3,"location":0.2},"adminConfirmationRequired":true}
```

技能等级 1–5 对应 20–100，负载分为 100/(1+未完成工单数)，位置分同楼100/同校区60/其他20。过滤原因另有 SKILL_MISMATCH、CAPACITY、ACCOUNT_PERMISSION。当前课程范围显式最多 200 个候选，超出返回409，不静默截断。管理员可选择推荐之外但满足有效技能、容量且存在可用时间的人员，具体超窗口预约仍要学生同意。

## 任务、真实流程图、统计及图片

`GET /workflow/tasks?page=0&size=20` 返回当前人实际可办的引擎任务：`{items:[{taskId,orderId,title,node,name,stage,version}],page,size,total}`。管理员审核、派单、异常；维修人员本人维修/预约；学生本人改期/验收。查询只包括正式 V2 流程，不混入草稿或 V1 历史单。

`GET /orders/{id}/workflow` 返回 mode=FLOWABLE_V2、processInstanceId、processDefinitionId、stage、ended、tasks、allowedActions、activities、historyTruncated。tasks 是数组；activities 为真实引擎历史，含 node/type/startedAt/endedAt，最多200条，超出明确 historyTruncated=true。结束流程通过 HistoryService 查询，不能用“任务为空”自行冒充完成。

`GET /orders/{id}/bpmn` 为该工单实际流程定义的 XML，包含可编辑 DI。前端使用现有 Vue 配合 bpmn-js 只读 Viewer，importXML 后按 workflow.tasks[].node 及已结束活动高亮；图和历史来自该实例的定义，不用候选 R2 图替代。前端不直接完成任意任务或修改变量。

|stage|中文|对应 V1 status|
|---|---|---|
|REVIEW|待审核|PENDING|
|ASSIGN|待派单|APPROVED|
|APPOINTMENT|预约协商|ASSIGNED|
|START|待开始维修/返工|ASSIGNED/REWORK|
|WORK|维修中|IN_PROGRESS|
|EXCEPTION|异常待协调|IN_PROGRESS|
|ACCEPT|待验收|WAITING_ACCEPTANCE|
|COMPLETED|已完成|COMPLETED|
|REJECTED|已驳回|REJECTED|

预约子流程的真实节点 appointmentRepairer/appointmentStudent；其他节点 review/assign/startRepair/repairWork/exceptionHandle/studentAccept。按钮仅用 allowedActions 控制，服务端仍独立授权及校验。

`GET /statistics` 仅业务管理员：`{total,byStage:[{stage,count}],ratings:{count,average}}`，全部来自实际 V2 业务数据，不含草稿、不制造历史统计。

`GET /orders/{id}/images` 返回该单固定修订的最多6张元数据及 contentUrl。用 Bearer 请求 contentUrl 获取 Blob；允许该单学生、当前指派人及业务管理员读取。无关用户404；改派后原维修人员立即不可见；图片接口 no-store。旧 `/images/{id}` 和草稿图片接口仍只允许所有者，管理角色不因此获得所有私人草稿图片。

## 页面接入与错误

学生：保存带图资料→submit→进度→必要时确认改期→验收/返工→评价。管理员：任务→review→recommendations→assign→异常协调；维修人员：任务→appointment→start→result 或 exception。审核拒绝直接结束。

400 为字段/分页/时间格式错误；401 为缺失、过期或撤销 Token，清会话并重新登录；403 为缺少功能权限；404 为不存在或不在数据范围；409 为版本、节点、重复操作、资源冲突，刷新详情；503 为服务不可用。沿用 V2 `{status,code,message,requestId,fieldErrors}` 契约；错误反馈显示 requestId，不记录密码/Token/完整私人资料。事务异常可能500，刷新确认后再按最新版本操作，不自动连续重试写请求。

Vite 继续 `VITE_BACKEND_URL=http://127.0.0.1:18081`、`VITE_DATA_MODE=live`，现有 axios baseURL=/api，所以代码写 `/v2/...`。开发 CORS 继续 GET/POST/PUT/PATCH/OPTIONS 与 Authorization/Content-Type/X-Request-Id；不用新增 DELETE。接口本地实现和真实HTTP已验证；Vue页面、BPMN高亮及多角色浏览器完整链路仍待最终联调，不将后端测试称为浏览器验收。
