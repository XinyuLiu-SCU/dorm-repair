# 前端并行对接清单（V2.0 Final）

继续复用仓库frontend/，已存在LoginView.vue、RepairWorkspace.vue、session.js及orders.js；不要另起Vue工程或重做布局。当前旧工作台仍面向V1，新V2功能按下面稳定接口增加页面/面板。

## 现在就能接

|页面/组件|稳定后端|建议|
|---|---|---|
|登录/个人中心/用户角色管理|V2.0-B-API.md|按permissions控制菜单按钮，不以角色名授权|
|故障树/项目选择/资产及二维码|V2.0-C-API.md|真实分类/级联/服务器分页；正常扫码需登录及数据权限|
|地址簿/本人入住/管理确认|V2.0-D-API.md|默认切换后刷新版本，地址不等于入住|
|资料编辑/修订历史|V2.0-D-API.md|明确“尚未提交”，完整PUT+version|
|上传/预览/移除/历史图片|E-API.md|FormData上传、Blob授权读取、按新修订保存图片集合|

学生先完成分类/扫码/地址/图片/资料历史；管理员先完成资产与权限及入住管理。维修端可复用旧工作台布局；新预约日程、正式维修任务、异常、统计与BPMN高亮已提供本地核心API，详见CORE-API.md；不对未接入页面返回假成功。

## 本地接入

在frontend/.env.local设置（不要提交）：
```dotenv
VITE_BACKEND_URL=http://127.0.0.1:18081
VITE_DATA_MODE=live
```

这是现有vite.config.js实际读取的变量，开发端5173；保留原18080配置可用于V1联调，切环境重新登录。现有orders.js baseURL=/api，新路径写/v2/...。不要继续使用模拟数据展示已完成的V2业务。

当前session.rolePath依赖user.role，而V2 /auth/me含legacyRole与permissions：不要直接覆盖旧结构导致导航报错。可以保留V1兼容身份用于旧布局，并单独读取/v2/auth/me权限，或显式映射legacyRole；数据范围仍由后端决定。

开发库不设默认密码。本机已通过既有安全工具及B接口准备超级管理员、普通业务管理员、学生和维修人员合成账号；本机受限凭据文件仅供私下取用，不进Git，不在前端硬编码账号。其他新环境仍使用scripts/initialize-v2-admin.ps1显式初始化。详见FINAL-INTEGRATION-PREP.md。C合成空间设施可直接使用，PRIVATE设施只有管理确认入住后普通学生才可见。

## 核心后端已提供

正式成单、真实dormRepairV2、预约子流程、推荐前三名、异常改派、连续返工、五星评价、统计、任务与实际BPMN XML参见CORE-API.md。start-v2.ps1启用v2-dev,v2-core；V2独立数据库中的业务迁移与引擎建表受环境保护。E归档标签v2.0-e-images保留；核心后端已归档为v2.0-core-complete；远端同步入口为feature/v2-development，队友拉取及本机启动见V2-TEAM-START.md。联调账号、当前V1前端状态及安全同步建议见FINAL-INTEGRATION-PREP.md。

## 最终联调展示目标

学生：报修、扫码、地址、图片、预约、进度、评价。维修：任务工作台、日程、处理及异常。管理：真实统计、审批派单、流程可视化、设施、权限。沿用现有组件和视觉体系，小步接API；最终用真实浏览器走完整角色链，不以HTTP测试替代浏览器验收。

本轮代码仍在本地，未擅自推送远程；联调应使用当前本地后端，或取得同步后的实现再运行，不能把远程旧版当作A–E已部署。

队友首次拉取请先阅读[V2-TEAM-START.md](V2-TEAM-START.md)，本机凭据、私有图片及本地环境文件不随Git交接。
