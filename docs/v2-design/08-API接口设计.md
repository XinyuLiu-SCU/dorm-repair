# 08 API接口设计（R2）

拟议契约，均未因本轮设计上线。V1实际接口仍以FRONTEND-API.md为准；保留原路径/主要字段/JWT，V2能力用/api/v2。旧写接口命中V2单返回409 VERSION_UNSUPPORTED，不能跳过新流程。GET /api/v2/auth/me为新权限/多工作台契约，旧/api/auth/me不强制改变。

## 1. 通用协议

ID统一十进制字符串；新时间ISO8601带偏移，服务端存timestamptz；page从0、size默认20最大100、稳定排序，返回items/page/size/total。操作者只从JWT subject取，拒绝请求actorId/studentId冒充。返回数据不含哈希、密钥、存储路径或任意流程变量。

命令携带orderVersion、目标taskId（任务型）、cycleId/preparationEpoch（有准备代次）；关键创建/预约确认/发退料用Idempotency-Key，操作者+作用域+bodyHash验证，异体同key409。并行分支命令也锁工单、重验版本，不能假定另一支更新对自己永远无影响。没有泛化tasks/complete或variable写API。

错误：400校验、401未认证/账号失效、403功能无权、404不存在或数据不可见、409状态/任务/版本/资源冲突、503工作流不可用；500隐藏内部错误。V2错误{status,code,message,requestId,fieldErrors}，V1整数code继续适配。新的CORS头Idempotency-Key/If-Match/X-Request-Id和ETag暴露仅在未来实现明确来源白名单，当前配置不改。

## 2. 用户、分类、位置与附件

|方法/路径（/api/v2前缀）|请求/响应及权限|
|---|---|
|GET /auth/me|id/username/roles/permissions/availableWorkspaces/authVersion；DB实时授权|
|GET/PATCH /profile；POST /profile/password|本人；改密撤销旧会话|
|GET/POST/PATCH /users[/{id}]；POST /users/{id}/enable或disable|user:manage；账号停用递增authVersion，不停最后管理者|
|GET/POST/PATCH /roles[/{id}]；GET /permissions；PUT /users/{id}/roles；PUT /roles/{id}/permissions|role:manage；防自提权，事务保护最后管理者|
|GET /categories/tree；GET /categories/quick；POST/PATCH /categories[/{id}]|八根/三级/quick/defaultDurationMinutes；维护catalog:manage；改父节点循环/深度400|
|GET/POST/PATCH /addresses[/{id}]；DELETE /addresses/{id}；POST /addresses/{id}/default|本人；buildingId/roomId/contact/phone；位置一致，停用不删除快照|
|GET/POST/PATCH /buildings[/{id}]；GET/POST/PATCH /rooms[/{id}]|读最小字典，写asset:manage；房间所属楼栋不可矛盾|
|GET/POST/PATCH /facilities[/{id}]；GET /facilities/{id}/maintenance-history；GET /facilities/resolve?code=|资产权限/本人入住等范围；QR不授予历史隐私；facilityLocationVersion|
|GET/PUT /building-travel-times|schedule:manage；fromBuildingId/toBuildingId/minutes/configVersion，有向，缺项显式UNKNOWN|
|POST /files；GET /files/{id}/content|JPG/PNG/WebP、<=5242880字节；每组最多6张绑定时校验，授权Blob下载；私有暂存|

创建报修请求：

```json
{"categoryId":"8","addressId":"12","facilityId":"44","description":"空调不制冷","fileIds":["28"],"timePreference":{"mode":"WINDOW","startAt":"2027-03-10T09:00:00+08:00","endAt":"2027-03-10T12:00:00+08:00"}}
```

时间不限使用{"timePreference":{"mode":"ANY"}}，不得同时带start/end。facility与address位置冲突返回400 LOCATION_MISMATCH，不静默替换。首次估时取项目默认快照，缺失标NEEDS_ESTIMATE而不编造时长。

## 3. 工单、修订、轮次及多任务

|方法/路径|请求/响应与权限|
|---|---|
|GET /orders；POST /orders；GET /orders/{id}|SQL权限分页；创建原子快照/修订/期望/流程；详情附version/round/stage/allowedActions|
|GET /orders/{id}/revisions；POST /orders/{id}/supplement|补充taskId/version、title/description/categoryId/addressId/fileIds；追加revision及快照，不覆盖历史|
|GET /orders/{id}/time-preferences；POST /orders/{id}/time-preferences|新增WINDOW/ANY版本；审核前或合法协商任务允许；已确认预约不随意改变，新版本使提议需重审|
|GET /orders/{id}/estimate-history；POST /orders/{id}/estimate-adjustments|minutes>0/reason/expectedEstimateVersion；当前维修者或授权调度者；改变需重验预约|
|GET /orders/{id}/cycles；GET /orders/{id}/records|按roundNo/时间分页；记录含cycleId/roundNo/submissionTaskId/附件/原预约快照|
|GET /orders/{id}/workflow|见下方多任务契约；LEGACY mode无伪造实例|
|GET /orders/{id}/workflow/model?processLinkId=；GET /orders/{id}/workflow/activities|实际definitionId XML/DI及所有活动实例；关联材料模型需独立权限；历史服务查完成|
|GET /workflow/tasks；GET /workflow/task-counts|当前用户授权root/材料/巡检/投诉待办，scope/taskType过滤；避免只按root查漏独立任务|
|POST /orders/{id}/withdraw|本人、reviewScope内，reason/orderVersion；审核后409|
|POST /orders/{id}/review|decision APPROVE/REJECT/RETURN_INFO、reason/taskId；order:review|
|GET /orders/{id}/dispatch-recommendations；POST /orders/{id}/assign|repairerId/orderVersion/recommendationId可选/reason；dispatch:manage最终决定|
|POST /orders/{id}/assignment/accept或refuse|被分配本人，taskId/epoch/reason；拒绝回派单|
|POST /orders/{id}/transfer|授权管理员；targetRepairerId可选、reason、orderVersion/cycleId/preparationEpoch；中断允许scope并可能进入材料核对，不能宣称立即转派完成|
|POST /orders/{id}/start；POST /orders/{id}/pause；POST /orders/{id}/resume|当前维修者；pause reasonCode/needsReplan，Service决定资源/SLA影响而非信任客户端布尔值|
|POST /orders/{id}/finish|本轮taskId/description/fileIds/epoch；追加record并进入验收，重复409|
|POST /orders/{id}/acceptance|本人acceptor，decision ACCEPT/REWORK/reason/cycleId/taskId；7天后仍可，返工不限次|

多任务响应示例（学生仅可见自己的任务及脱敏分支）：

```json
{"orderId":"301","generation":"V2","mode":"FLOWABLE","status":"ASSIGNED","workflowStage":"PREPARING","orderVersion":8,"cycleId":"51","roundNo":0,"preparationEpoch":2,"ended":false,"activities":[{"activityId":"appointmentNegotiation","scope":"APPOINTMENT","state":"ACTIVE"},{"activityId":"resourceCall","scope":"RESOURCE","state":"ACTIVE"}],"branchStates":[{"branch":"APPOINTMENT","state":"WAITING_STUDENT_CONSENT"},{"branch":"RESOURCE","state":"PENDING"}],"tasks":[{"taskId":"t-student-2","processInstanceId":"pi-301","definitionKey":"studentConsent","scope":"APPOINTMENT","assigneeId":"4","cycleId":"51","preparationEpoch":2,"allowedActions":["CONSENT_APPOINTMENT","REJECT_APPOINTMENT"]}],"allowedActions":["CONSENT_APPOINTMENT","REJECT_APPOINTMENT"],"taskActions":[{"action":"CONSENT_APPOINTMENT","taskId":"t-student-2","orderVersion":8,"preparationEpoch":2}]}
```

管理员或仓库人员另按权限看到对应材料任务；不得把以上“1个可见任务”误解为引擎只有1个任务。ended依根流程runtime/history判定，不依tasks.length。

## 4. 预约、授权、材料与SLA

|方法/路径|请求/响应与约束|
|---|---|
|GET/POST /orders/{id}/appointments|维修者或授权协调者提议startAt/endAt、preferenceId、estimateVersion、epoch、supersedesId、reason；返回proposalVersion/studentConsentRequired/participants|
|POST /appointments/{id}/decisions|学生本人decision ACCEPT/REJECT/COUNTER、proposalVersion、counterWindow可选/reason；追加历史，不能改已同意提议时间|
|POST /appointments/{id}/confirm|当前维修者，proposalVersion/taskId/orderVersion/epoch；学生必要同意齐全后原子释放旧预订/新建预订；冲突409|
|GET /appointments/{id}/history；GET /repairers/{id}/calendar|相关本人/调度权限；历史提议/决定；日历只暴露业务必要时段，不曝光请假私因|
|POST /orders/{id}/access-authorizations；POST /access-authorizations/{id}/revoke；GET /orders/{id}/access-events；POST /orders/{id}/access-events|本人授权范围/validFrom/To/termsVersion；ATTENDED可无authorizationId，UNATTENDED必须本单有效授权且校方规则允许；撤销不抹记录|
|GET /orders/{id}/preparations；POST /orders/{id}/material-requirements|当前维修者/调度权限；NONE/KNOWN/UNKNOWN及行明细；变化创建新epoch，不把未知当NONE|
|GET/POST /material-requests；POST /material-requests/{id}/approve或reject|独立流程；关联order/cycle/epoch；stock:approve，审批不等于发料|
|POST /material-requests/{id}/issue；POST /material-requests/{id}/return；POST /material-requests/{id}/handover|stock:issue；幂等键/行数量/原movementId；净发满足需求才READY；交接需双方/授权确认记录|
|GET/POST/PATCH /stock-items[/{id}]；GET /stock-balances；GET /stock-movements；POST /stock-receipts|库存授权；余额无任意PATCH；入库单价/数量，财务口径未确认不出正式核准成本|
|GET /orders/{id}/sla；GET/POST/PATCH /sla-rules[/{id}]|授权配置ruleVersion/threshold/pausePolicy；验收7天/评价15天默认已定；变规则不偷偷改在途deadline|
|GET /orders/{id}/materials|本人必要明细；价格/成本仅stock/cost权限；公开不返回|

预约确认请求示例：

```json
{"proposalVersion":3,"taskId":"t-confirm-3","orderVersion":12,"cycleId":"51","preparationEpoch":2}
```

材料转派冲突返回409 MATERIAL_SETTLEMENT_REQUIRED或命令成功进入TRANSFER_RECONCILIATION（返回order状态及pendingActions），不能一边报“已转派”一边旧人还持有材料。异步后续处理只显示真实状态，不以202伪装最终成功。

## 5. 公共、巡检、服务质量及支撑接口

|方法/路径|语义与权限|
|---|---|
|GET /public-incidents[/{id}]；POST /public-incidents；POST /public-incidents/{id}/confirm-result|脱敏投影；创建/结果确认public:manage，管理员负责主实际结果|
|GET /orders/{id}/duplicate-candidates；POST /public-incidents/{id}/links|管理员建议并按规则取得原单关联同意；独立实例/epoch，不合并私密宿舍|
|POST /incident-link-proposals/{id}/decisions|本人ACCEPT/REJECT，proposalVersion/linkEpoch，不能管理员代同意；关联激活后才等待主结果消息|
|POST /incident-links/{id}/unlink-requests；POST /incident-links/{id}/unlink-decision|本人申请、管理员确认，主单已启动不被撤回；解绑回原独立处理并废弃旧消息关联|
|POST /incident-links/{id}/disputes；POST /incident-disputes/{id}/resolve|原单验收不同意/争议；管理员核查，可关联新后续工单，不强制代本人验收|
|POST/DELETE /public-incidents/{id}/subscription；POST /public-incidents/{id}/feedback；POST /public-announcements/{id}/publish|本人订阅/脱敏反馈，发布专门权限|
|GET/POST/PATCH /inspection-plans[/{id}]；GET /inspection-batches；GET /inspection-tasks|计划/批次/本人目标任务有界分页；独立timer/MI，不让前端任意启动全校批次|
|POST /inspection-tasks/{id}/complete；POST /inspection-findings/{id}/convert|异常转单唯一关联、合法SYSTEM/STAFF来源/acceptor，Outbox最终状态可查询，不假装同步完成|
|POST /orders/{id}/evaluation|最终完成后15天内首次评价，quality/attitude/responseSpeed/punctuality nullable；未预约守时必须null|
|GET/POST /orders/{id}/messages；POST /orders/{id}/complaints；GET /complaints；POST /complaints/{id}/decision|相关参与者沟通；投诉独立实例，私密范围单独判断|
|GET /followups；POST /followups/{id}/contact-records；POST /followups/{id}/complete|独立回访任务，来源FK明确，不复活原结束流程|
|GET /notifications；POST /notifications/{id}/read|仅本人；timer投递去重，URL仍需授权|
|GET/POST/PATCH /knowledge[/{id}]；POST /knowledge/{id}/publish|学生只读PUBLISHED；危险电气自助内容提示停止危险操作|
|GET /statistics/overview；GET /statistics/service-quality；GET /audit-events|授权范围/日期上限/脱敏；统计区分等待、维修、暂停、返工和公共原单数量|

## 6. 契约一致性检查清单

ANY/WINDOW与正式预约分离；defaultDuration→estimateHistory→appointment绑定；travel有向配置；proposal与participant/decision历史；revision快照与附件；cycle/epoch与record；position一致400；SLA规则快照与timer；净材料/冲正；tasks/activities/branchStates/stage均有数据来源。细节OpenAPI和DTO在开发前冻结，本轮不宣称这些设计接口已上线。


GET /public-incidents/{id}/results返回脱敏且管理员已确认的resultVersion/时间/摘要；GET /orders/{id}/cycles对公共验收返回kind=PUBLIC_ACCEPTANCE与publicResultId，不能返回伪造repairRecord。巡检GET /inspection-findings/{id}含conversionState/orderId，PENDING/FAILED不提示已创建成功。


公共关联POST /public-incidents/{id}/links须本人提议同意且当前REVIEW或DISPATCH可用；其他阶段409 LINK_STAGE_CONFLICT。POST /incident-links/{id}/unlink-decision只在PUBLIC_LINKED_WAIT且未送达结果执行，否则409 USE_DISPUTE。公共阶段与status以05补充表为准，响应仍使用同一tasks/activities/epoch结构。

材料交接请求含originalIssueMovementId/fromHolderId/toHolderId/qty及版本，操作者仍来自JWT；from/to是业务目标而非冒充操作者。POST /material-handovers/{id}/confirm由相应本人或授权仓库确认，双确认结果可查，不能单方改holder。/workflow/task-counts明确taskTotal和distinctOrderTotal。


## C阶段获批局部补充

2026-11-27：见[C局部补充](C-LOCAL-ADDENDUM.md)，实际结构与可编辑ER见[实际结构](../v2-development/V2.0-C-SCHEMA.md)，接口见根目录V2.0-C-API.md。旧R2标签不变，新空间与项目实体以补充记录为准；验收增加层级/循环、复合外键、权限迁移、二维码真实解码和并发/回滚。


## D阶段获批局部补充

独立资料准备聚合及不可变版本，见[D局部补充](D-LOCAL-ADDENDUM.md)。实际新增4表及ER见根目录V2.0-D-SCHEMA.md；接口见V2.0-D-API.md。新增验收：默认地址并发/所有权，入住范围即时重验，快照不可变/修订链，ANY/WINDOW、估时历史与无人值守意愿，V7逐用户授权及Token撤销。不将V2.1正式转换原子性、防重或E附件上传计为本轮已实现。
