# R2候选模型阅读说明

全部isExecutable=false，只作设计评审，不复制到backend流程资源目录，不部署到任何引擎。

- dormRepairV2：root主图；reviewScope审核退补；workCycle包含并行准备/维修异常/验收；preparationScope两支fork/join；appointmentNegotiation预约子流程；repairExceptionHandling异常子流程；publicLinkedScope公共原单独立等待与争议（V2.3扩展）。
- materialRequestV2：独立材料审批/预留/分批实发，及中断取消/实发核对；materialRequestScope子图。
- inspectionPlanV2：顶层timer启动，目标快照及并行MI inspectionUnit；每日只是候选触发频率，不等于所有资产每日巡检。
- complaintCaseV2：投诉调查处置后call独立回访。
- serviceFollowupV2：投诉或差评来源的联系循环，明确解决后结束。

每个嵌入式subProcess在父图为折叠节点（isExpanded=false），另有同ID BPMNPlane供编辑器下钻，序列流绝不跨scope边界；独立流程通过callActivity定义ID/业务消息关联。编辑器需选择对应diagramId或支持子流程下钻；尚未进行bpmn-js视觉验收。

DI使用正交避障路线，并静态检查每条线不穿过非端点节点内部；允许连线之间交叉/共用走廊，不代表所有标签重叠已人工排除。需要后续真实渲染微调。

callActivity采用flowable:calledElementType=id与useLocalScopeForOutParameters=true，定义ID由未来版本适配器提供；非中断验收timer=acceptanceDueAt；inspection timer=R/PT24H。需未来在锁定7.2.0中验证定义表达式/输入输出/执行器行为。

r2ReviewOnlyDelegate只是占位名称，不是业务实现。用户任务的候选权限、assignee、命令guard、epoch校验、资料/资源更新和消息correlation由未来受控Service实现，不能因为XML里有task就认为权限/事务已经实现。timer委托只写Outbox意图，不锁工单。

MODEL-STATIC-CHECK.json仅记录XML引用/作用域/DI几何。未部署、未创建job、未执行数据库迁移，也不是流程路径测试报告。
