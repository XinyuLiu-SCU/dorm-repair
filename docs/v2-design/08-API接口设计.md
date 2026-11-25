# 08 API接口设计

本文件是拟议V2契约，不代表接口已经上线。实际可用V1接口以根目录FRONTEND-API.md为准。V1 `/api/auth`、`/api/repair-orders`、`/api/workflow/tasks`及管理员维修人员列表继续兼容；新增能力走 `/api/v2`。禁止把下列设计示例当作已通过的HTTP测试。

## 通用协议

JWT继续使用Authorization: Bearer <accessToken>；操作者从认证上下文读取，不允许请求体指定actorId/studentId冒充。DB实时检查enabled、auth_version及权限；角色菜单只作展示。V2 ID为字符串，时间ISO8601含偏移，page从0开始、size默认20最大100；列表必须服务端过滤分页与稳定排序。V1现有数组/主要字段维持原状。

```json
{"items":[],"page":0,"size":20,"total":0}
```

V2错误结构：

```json
{"status":409,"code":"ORDER_STATE_CONFLICT","message":"工单已被其他人处理，请刷新","requestId":"opaque-id","fieldErrors":[]}
```

400格式/校验，401未认证/过期/账号失效，403已认证但无功能权限，404资源不存在或不可见，409状态/版本/预约/库存/幂等冲突，503工作流或必需服务暂不可用。V1原整数code用适配器保留。未知内部异常500不向前端返回堆栈/SQL。

更新命令提交orderVersion或If-Match，服务端行锁后重验，陈旧版本409；关键创建、预约确认、领料发放支持Idempotency-Key，按操作者+命令作用域保存哈希，同key同体返回原资源，同key异体409。普通页面仍需按钮loading防双击；幂等不能替代事务和权限。

CORS仅明确允许开发来源，Vite代理仍优先。实现V2时才增加允许Idempotency-Key/If-Match/X-Request-Id及暴露ETag，不能声称当前V1配置已支持这些新头。不得允许任意来源携带凭证。所有下述列表有同一分页上限，批量ID参数也限制数量。

## 认证、用户和基础资料

|方法与路径|授权/用途|
|---|---|
|POST /api/auth/login；GET /api/auth/me|保留V1登录/身份契约；V2新增permissions、authVersion需兼容扩展或独立/me|
|GET/PATCH /api/v2/profile；POST /api/v2/profile/password|本人资料/校验旧密码后修改密码，撤销旧Token|
|GET/POST /api/v2/users；PATCH /api/v2/users/{id}|user:manage；最小字段、分页；创建密码不返回；重置密码独立受审计命令|
|POST /api/v2/users/{id}/enable 或 disable|user:manage；DB auth_version递增；避免停用最后一个可用管理者|
|GET/POST/PATCH /api/v2/roles[/{id}]；GET /api/v2/permissions|role:manage；权限点只读，禁止随意构造服务端不存在的权限|
|PUT /api/v2/users/{id}/roles；PUT /api/v2/roles/{id}/permissions|授权管理；防止越权授予，影响用户Token失效；最后管理者保护|
|GET /api/v2/categories/tree；GET /api/v2/categories/quick|启用类别树/快捷入口；管理写接口需catalog:manage|
|GET /api/v2/addresses；POST/PATCH/DELETE /api/v2/addresses[/{id}]；POST /{id}/default|仅本人；删除为停用，不修改历史快照|
|GET /api/v2/buildings；GET /api/v2/rooms?buildingId=|最小位置字典，不返回住户名册；维护需asset:manage|
|GET/POST/PATCH /api/v2/facilities[/{id}]；GET /{id}/maintenance-history|资产管理或已授权可见范围；历史工单需逐项过滤；公开部分仅脱敏投影|
|GET /api/v2/facilities/resolve?code=|二维码不含JWT/联系人；校验位置业务权限后返回可报修资料，不授权读取宿舍历史|
|GET /api/v2/repairers；GET/PATCH /api/v2/repairers/{id}/skills|调度管理员或本人最小资料；技能维护需dispatch:manage|
|GET/POST /api/v2/repairers/{id}/shifts；GET/POST /api/v2/leaves；POST /leaves/{id}/decision|排班/请假；本人申请，管理员审批，不静默取消已确认预约|

## 工单与流程命令

|方法与路径|请求要点/权限|
|---|---|
|GET /api/v2/orders|分页过滤status/categoryId/keyword/date；学生本人、维修者分配范围、管理员授权范围|
|POST /api/v2/orders|categoryId,addressId,description,fileIds,desiredWindow,facilityId可选；本人创建，服务端生成快照/creator/学生/流程|
|GET /api/v2/orders/{id}|按工单可见范围；含快照、版本、allowedActions、基本流程摘要；不返回哈希/内部变量|
|GET /api/v2/orders/{id}/records|按时间稳定排序与分页，保留连续返工；详情规则一致|
|GET /api/v2/orders/{id}/workflow|generation/mode、actualDefinitionId、stage、tasks、ended、allowedActions；LEGACY不伪造实例|
|GET /api/v2/orders/{id}/workflow/diagram；GET /{id}/workflow/activities|只读真实BPMN XML/DI及历史活动实例；无运行实例用历史服务，返回迭代实例而非仅节点去重|
|GET /api/v2/workflow/tasks；GET /api/v2/workflow/task-counts|当前权限可处理待办，分页/分组计数；维修与验收按真实assignee，管理员按候选授权|
|POST /api/v2/orders/{id}/withdraw|审核前学生本人；理由、orderVersion；新增CANCELLED终态|
|POST /api/v2/orders/{id}/supplement|本人补充；新附件/内容及版本，重新进入原审核环节|
|POST /api/v2/orders/{id}/review|decision APPROVE/REJECT/RETURN_INFO，reason；order:review，任务/状态匹配|
|GET /api/v2/orders/{id}/dispatch-recommendations|dispatch:manage，返回候选和硬约束失败原因，不占用日历|
|POST /api/v2/orders/{id}/assign|repairerId,recommendationId可选,reason,orderVersion；管理员最终决定，重新校验可用性|
|POST /api/v2/orders/{id}/assignment/accept 或 refuse|被分配人员本人；拒单理由必填，回待派单不结束工单|
|POST /api/v2/orders/{id}/transfer|dispatch:manage；新维修者和原因，资源释放与新任务分配原子完成|
|GET/POST /api/v2/orders/{id}/appointments；POST /appointments/{appointmentId}/confirm 或 reject|本人相关方，startAt/endAt、reason、版本；谁可最终确认待业务决策；改期新增提议不覆盖旧记录|
|POST /api/v2/orders/{id}/access-authorizations；POST /access-authorizations/{id}/revoke|学生本人显式授权/撤销，时段/scope/termsVersion；默认不授权入室|
|POST /api/v2/orders/{id}/access-events|当前维修者人工记录入室/离室/无法入室；必须检查有效授权，不能伪称门禁验证|
|POST /api/v2/orders/{id}/start；POST /pause；POST /resume|当前维修者，合法节点、原因、预约条件；暂停不当作已完成|
|POST /api/v2/orders/{id}/finish|当前维修者；description,fileIds、version；同事务新增维修记录及推进验收|
|POST /api/v2/orders/{id}/acceptance|acceptor本人；decision ACCEPT/REWORK、reason；保留原维修者返工与完整迭代记录|
|POST /api/v2/orders/{id}/evaluation|已完成且本人；quality/attitude/responseSpeed/punctuality可空/comment；评分范围与重复约束|

V1工单保持旧命令和旧流程；V2工单不得让旧API绕过新增预约/授权节点。V1命令命中V2工单返回409并提供升级客户端说明；读取可提供兼容粗状态但不泄露错误按钮。没有泛化 `/tasks/{id}/complete` 或任意变量写入接口。

提交示例（未实施）：

```json
{"categoryId":"8","addressId":"12","description":"空调不制冷","fileIds":["28"],"desiredWindow":{"startAt":"2027-03-10T09:00:00+08:00","endAt":"2027-03-10T12:00:00+08:00"}}
```

详情摘要示例（联系人仅在私密详情授权后出现，列表默认隐藏电话）：

```json
{"id":"301","generation":"V2","status":"ASSIGNED","workflowStage":"APPOINTMENT_NEGOTIATION","version":4,"workflow":{"mode":"FLOWABLE","ended":false},"allowedActions":["PROPOSE_APPOINTMENT"],"addressSnapshot":{"buildingName":"示例楼","roomNo":"示例房间"}}
```

推荐响应的候选包含repairerId/name、feasibleWindows、score、componentScores、reasonCodes、configVersion、expiresAt；无候选返回空items及淘汰摘要，不自动违规派单，不公开其私人请假原因。

## 附件、公共协同与资源

|方法与路径|边界|
|---|---|
|POST /api/v2/files；GET /api/v2/files/{id}/content|multipart上传、服务端类型/大小/数量检测；鉴权Blob下载；不返回存储绝对路径/无鉴权永久URL|
|GET/POST /api/v2/orders/{id}/messages|参与者可见；纯文本转义、分页；公共评论不能复用私密沟通投影|
|POST /api/v2/orders/{id}/complaints；GET /api/v2/complaints；POST /{id}/resolve|本人投诉、本人/有授权管理员查询处理；敏感投诉不默认暴露被投诉人|
|GET /api/v2/followups；POST /{id}/complete|专门客服/管理员权限；独立回访，不复活终态工单|
|GET /api/v2/public-incidents；GET /{id}|只返回公共位置、脱敏摘要、影响统计、公开进展，不返回原工单、电话、私人房号|
|GET /api/v2/orders/{id}/duplicate-candidates；POST /api/v2/public-incidents/{id}/link|管理员确认；先软关联，真正联动合并需独立阶段批准及各单验收规则|
|POST/DELETE /api/v2/public-incidents/{id}/subscription；POST /{id}/feedback|当前用户订阅/公共反馈；频控、脱敏审核；订阅不扩大工单可见范围|
|POST /api/v2/public-announcements；POST /{id}/publish|public:publish；审核后的独立公开文本|
|GET/POST/PATCH /api/v2/stock-items[/{id}]；GET /api/v2/stock-balances|库存授权；余额不提供任意PATCH|
|POST /api/v2/material-requests；GET /api/v2/material-requests；POST /{id}/approve 或 reject|当前维修人员为自己的工单申请，库存授权审批；数量与任务一致|
|POST /api/v2/material-requests/{id}/issue；POST /{id}/return|stock:issue；幂等键、行明细、版本；防超额、并发负库存|
|POST /api/v2/stock-receipts；GET /api/v2/stock-movements|实际入库/流水；不实现采购支付系统；调整需独立审计权限|
|GET /api/v2/orders/{id}/materials|参与者必要材料清单；成本金额仅授权管理者可见|
|GET/POST/PATCH /api/v2/inspection-plans[/{id}]；GET /api/v2/inspection-tasks|管理员计划、维修者本人任务；定时唯一生成|
|POST /api/v2/inspection-tasks/{id}/complete；POST /findings/{findingId}/convert|真实检查结果；异常转单幂等，source SYSTEM/STAFF、指定验收管理员|
|GET /api/v2/notifications；POST /{id}/read|只允许本人，不根据前端recipientId查询他人消息|
|GET /api/v2/knowledge；GET /{id}；POST/PATCH /api/v2/knowledge[/{id}]；POST /{id}/publish|学生只读已发布安全知识，编辑发布需权限|
|GET /api/v2/statistics/overview；GET /api/v2/statistics/service-quality|授权维度、限时间范围；口径区分响应/等待/维修/预约守时，缺失值不记0分|
|GET /api/v2/audit-events|audit:read；分页脱敏，只读，限制导出范围，不提供任意日志删除|

所有列表权限必须进入SQL筛选和count，不能先全量取出再在Java或浏览器过滤。统计不泄露可识别私人信息。具体菜单/按钮/权限矩阵见01与07；具体请求DTO和OpenAPI应在每阶段开发前冻结并与前端共享契约用例。
