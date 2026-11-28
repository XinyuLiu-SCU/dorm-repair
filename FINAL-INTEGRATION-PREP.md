# V2.0 Final 联调准备与前端现状核查

2026-11-28。本轮完成核心归档、隔离环境准备和前端核查；未修改业务源码、Vue页面或视觉设计，未开展最终浏览器验收。

## 核心归档

本地提交 `5e468171f0fd3b8cec9b4ab3d57289177bb5913b`，标签 `v2.0-core-complete`，分支 `feature/v2-development`。归档21个文件，包含核心源码、V12/V13、真实BPMN、测试、API/完成说明和HANDOFF。归档前核对全量215项与随后核心11组/370次HTTP记录，原测试与迁移保留，敏感扫描无发现，Git diff --check通过。没有重复声称本轮重跑后端全量测试。

历史标签全部保留；未合并master、未push。核心提交后工作区干净，本报告是归档之后新增的交接文件，未提交。

## 前端与远端实况

已成功执行git fetch origin --prune并用ls-remote核对，远端仅见：

|分支|最新提交|
|---|---|
|master|addca1f，V1.1稳定整合|
|feature/vue-frontend|3a67637，队友原前端提交|
|feature/v2-development|56fe755，旧V2设计提交|

本地独立前端工作树 `../dorm-repair-frontend-v1.1` 位于 `integration/v1.1-review`，提交9cf30b2，比队友远端分支多一个本地V1.1修复提交；工作区干净。主工作树frontend/src与此工作树源码一致，差异仅6个README、V1.1报告和历史测试运行说明文件，未自动覆盖或合并。

当前源码：Vue3、Element Plus、Vue Router、Axios、Vite，保留App.vue侧栏/顶栏布局与既有绿色主题。LoginView.vue登录，RepairWorkspace.vue共享三角色工作区；已有V1工单分页筛选、allowedActions按钮、详情、维修记录、LEGACY及流程状态、待办计数、退出登录和基础加载错误处理。

现有调用仍为 `/api/auth/*`、`/api/repair-orders` 和V1工作流接口；身份路由读取user.role。orders.js仍有显式demo分支和localStorage模拟数据，当前live模式不走模拟分支。尚未接V2登录与permissions、地址/入住/资料/图片、预约/异常/评价、推荐评分、实际BPMN Viewer、设施及RBAC管理面板。package.json尚无bpmn-js依赖；当前“流程状态”展示不等于真实BPMN动态流程图。

## 服务与账号

|地址|现状|
|---|---|
|127.0.0.1:18081|V2核心后端，PID41060，v2-dev,v2-core；V2隔离库|
|127.0.0.1:5173|原Vue服务，PID47524，HTTP200；当前仍代理18080|
|8080 / 18080|原服务PID39932 / 41116保持不变|

**5173目前不是V2完整系统入口。** 未为了表面“已接入”提前改代理；否则旧接口/身份结构会造成误用。后续V2适配落地后，再在本地.env.local切到18081并重启确认归属的Vite。

现有安全管理员工具显式初始化，合成目录工具执行；其他账号、角色、入住、地址和技能日程均通过既有真实API准备。没有默认密码，没有直接改数据库授权或复制真实用户资料。

|账号|用途|有效权限数量|
|---|---|---|
|synthetic_final_superadmin|超级管理员，原管理端内负责用户/角色/密码/审计|20|
|synthetic_final_admin|合成业务管理员|11|
|synthetic_final_student1|合成学生甲|10|
|synthetic_final_student2|合成学生乙|10|
|synthetic_final_repairer1|合成维修甲|7|
|synthetic_final_repairer2|合成维修乙|7|
|synthetic_final_repairer3|合成维修丙|7|

普通管理员使用独立FINAL_BUSINESS_ADMIN角色，仅11项既有业务权限：个人中心、legacy:admin、分类/空间/设施读写、入住管理。未分配user:manage、role:manage、permission:read、audit:read；这些接口真实返回403。超级管理员对应接口读取200，并用其创建其他账号，不新增超管模块或权限点。

开发库当前7用户、4角色、20权限、48房间、60设施，保留既有合成数据；两名学生各有独立房间的有效管理确认入住和默认地址；三名维修人员配置32个具体故障项目技能（5/4/3级）、不同基础楼栋、容量3，以及未来14天每日08–12/14–18的可用时间。初始正式V2工单0，仪表盘应真实显示0，完整工单留待浏览器操作产生。可用时间到期时应通过既有接口更新，不能伪造“始终可用”。

密码独立随机生成，只保存在本机 `work/final-demo-credentials.local.json`，文件ACL去继承并仅授予当前Windows用户；该路径被gitignore排除。报告、Git和输出附件不含密码/JWT/完整二维码定位码。请通过本机私密文件取用，不将文件发到仓库或截图。重复初始化已验证：仍7用户/4角色、未增加维修配置、未重置密码，所有账号仍能登录；不覆盖人工配置。

## 实际验证

- 环境准备首次67次HTTP检查通过；幂等复执行54次检查全部符合预期（含预期403），最后证据 `work/final-environment-check.json`。三角色及超级管理员身份、用户/角色/审计隔离、工单/待办/统计、本人地址入住和维修日程接口均验证。
- 当前主工作树frontend执行npm run lint、npm run build通过；构建有压缩后JS大于500kB提示，本轮只记录，不借机重构。原Vue HTTP200；没有把构建或HTTP检查称为浏览器业务验收。
- 正式库用户、工单、维修记录和结构摘要与原保护基线一致；8080/18080及正式密钥未改。只在V2开发库追加合成业务数据，无迁移或业务源码改动。

## 接入顺序与同步分工建议

不能从GitHub判断队友电脑上是否还有未推送的V2工作。开始页面写入前，应让队友明确已改文件及分支，将工作推到其独立功能分支或提供补丁；只fetch并对比，不强合并、不替换其工作树。当前未联系队友或替其提交。

建议以本地feature/v2-development内frontend为集成基线，保留旧integration/v1.1-review工作树供对照。不要同时在两份目录改同一页面；确认文件负责人后按小补丁逐项合入。拟由队友负责学生/维修业务表单与日程的页面呈现，Codex负责公共API/session/router、管理端真实统计/推荐/BPMN及集成测试；这是建议分工，尚未与队友确认。

优先接公共身份与接口适配，再接学生资料/图片/正式成单、管理审核派单、维修预约/异常、验收评价，最后贯通实际流程图与浏览器完整链路。复用现有布局及Element Plus面板，不另建工程，不新增范围外业务。

两个已核实的前端适配点：V2 /auth/me返回legacyRole与permissions而不是现有user.role；普通业务管理员availableWorkspaces当前为空，前端管理入口须依据既有legacy:admin权限映射，不能因此新增角色名硬编码或扩大其账号管理权限。V1数组工单、单currentTask、小写动作与V2分页对象、tasks数组、大写allowedActions不同，不能只把路径替换成/v2即宣称完成。

三类端按用户清单全部保留；智能推荐显示综合分/分项/过滤理由，BPMN使用实际XML及真实活动高亮，禁用模拟分支参与最终验收。后续真实浏览器验证正常、驳回、改期、异常改派、返工评价及权限隔离。详细契约沿用CORE-API.md、FRONTEND-HANDOFF.md及B/C/D/E文档，不重新设计数据库或权限。

本轮停止于准备和现状核查，等待队友未同步代码范围及接入责任确认；未擅自改页面或声称V2前端验收通过。
