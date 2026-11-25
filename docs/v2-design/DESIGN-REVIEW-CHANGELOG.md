# 第二轮设计评审变更记录

日期2026-11-25；基线56fe755；修订仅docs/v2-design与本地评审导出，不提交、不推送、不实现业务、不部署模型、不连接数据库。本目录01–10为修订后的完整设计。候选XML均isExecutable=false，models/非自动部署目录。

|意见ID|评审意见|修改内容|影响范围/验收|状态与待决|
|---|---|---|---|---|
|RV01|保留正确架构|原栈/模块单体、V1/V2适配、快照、私图、RBAC、锁/Outbox继续|01/02/09，AT01/21|保留，不重建|
|RV02|真实并行准备与汇合|preparationScope fork/join，预约+材料独立，无材料直通，失败scope error取消另一支|02/03/04/05/08/10、root/material模型，AT06/07|已设计；运行验证未做|
|RV03|预约/维修异常子流程|明确appointmentNegotiation与repairExceptionHandling边界、重入epoch|05/models，AT05/10|已设计|
|RV04|非中断BPMN定时|验收7天timer→Outbox→独立end，保留验收任务|02/05/04/08，AT11/13|已设计；需未来隔离async executor验证，不改变当前开关|
|RV05|消息中断撤回/转派|reviewScope撤回；workCycle整体转派；预留释放、实发核对|05/models/04，AT08|已设计；不允许无条件跳节点|
|RV06|独立材料/巡检/投诉回访|四个独立key及call/实例关联；巡检顶层timer+MI后续扩展|03/04/05/models，AT16/18/19|已设计，未部署|
|RV07|公共实例消息联动|root可选publicLinkedScope，独立原单wait/验收/争议/解绑，linkEpoch防迟到|05/04/08/models，AT17|已设计；争议响应时限待决|
|RV08|多任务与allowedActions|tasks/activities/branchStates/taskActions、聚合stage、权限投影|02/05/07/08，AT06/21|已设计，不用singleResult|
|RV09|并行取消/死锁/回滚|中断整个scope、不回旧join、统一锁序、timer只写意图、实发不假退|02/05/09/10，AT07/08/13|技术运行风险仍待测|
|RV10|BPMN/DI布局|层次模型、主/子独立diagram、正交避障路由；主作用域关系README|models与05|静态检查，非运行验收；连线交叉不等于穿节点|
|RV11|期望时间/正式预约|ANY/WINDOW版本与appointment独立|01/03/04/06/08，AT05|已定规则|
|RV12|工时/通行|defaultDuration→estimateHistory人工调整；有向楼间通行配置|03/04/06/08，AT15|缺数据不能伪造；权重待定|
|RV13|预约确认历史|participant/decision/proposalVersion；学生窗口外同意，维修者最终确认|04/05/08，AT05/14|已定规则|
|RV14|退补/附件版本|revision、快照版本、revision/record附件绑定|03/04/07/08，AT02/04|已设计，6张按每资料版/每次结果组解释|
|RV15|返工轮次|cycle roundNo与准备epoch分离，record唯一cycle，独立deadline|04/05/08，AT12|不限次数，提醒阈值待定|
|RV16|位置约束|组合FK+Service校验，设施移动不改历史快照|03/04/08/09，AT04|已设计|
|RV17|SLA/暂停|rule/clock/reminder版本化，验收7天只提醒，不回溯减维修暂停|04/05/08，AT11|各维修暂停是否停SLA仍待业务批准，默认持续计时|
|RV18|库存成本/冲正|移动加权候选、原出库成本退料、累计量/幂等/尾差|04/08，AT16|计价和尾差需财务口径确认|
|RV19|采纳首版规则|01 BR01–12全量确认：6图5MiB/三级/显式技能/ANY/维修确认/授权/请假/提醒/返工/7天15天/公共验收|全部文档及AT02–19|已采用，不再当未决；5MiB/自然日口径显式披露|
|RV20|统一交付编号|MUST/SHOULD/LATER优先级，V2.0–V3.0阶段；V2.1最小真实材料/班次提前|01/07/09/10|已统一，不为版本名损坏依赖|
|RV21|工作量重估|104–168合计人日，风险机动约120–210；不承诺日期|10|实际容量/MVP待确认|
|RV22|上轮契约分歧|固定/api/v2/auth/me；ATTENDED授权可空；followup真实FK；位置快照版本化|03/04/07/08|已收敛|

## 仍未解决/不得误称通过

候选模型Flowable7.2.0解析、call版本绑定和变量映射、scope取消子调用/资源联动、并行与timer竞态/死锁、实际executor与重试、多实例大集合和历史DI渲染都未实测。XML/DI静态校验仅证明结构与几何条件，不证明可部署或业务正确。

业务待定：校方入室权限、提醒重复频率/多返工阈值、暂停SLA政策、财务均价/尾差批准、公共争议响应期限、评价编辑/留存、巡检漏跑上限/周期、推荐权重/搜索上限、实际课程MVP容量。禁止自动验收/转派等既定规则不再询问。

## 文档一致性基线

规则BR01–12→实体03/04→API08→节点/模型05/models→验收AT01–22→阶段10。原文完整重修，旧轮次备份只在本地评审包，不混入当前权威设计。新图/字段均标未实施，不作为源码或正式迁移入口。


### R2内部交叉核对补充

公共验收也配置独立7天timer；新增incident_result/incident_link_proposal以闭合管理员真实结果与原单同意关联；公共cycle不伪造本单维修记录。明确终态与提醒竞争需回滚重试、材料call输出局部作用域、MI结果局部化，避免遗留token或并发覆盖。模型静态检查与本地Flowable7.2.0 XSD/解析通过的实际结果在MODEL-VALIDATION-REPORT.md记录；依然没有运行验收。

公共关联激活限审核/未派单节点，XML增加dispatch消息边界；结果送达前才可解绑，已送达走争议。此节点限制和争议响应时限为最终评审确认项；避免从实际作业中随意跳公共等待。
