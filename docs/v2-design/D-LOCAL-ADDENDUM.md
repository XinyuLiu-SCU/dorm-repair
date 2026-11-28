# V2.0-D 获批局部设计补充

日期2026-11-28。依据人工批准，不修改v2-design-r2标签，不新增正式工单草稿状态。

## 独立资料准备

采用repair_draft（所有者/current_revision/version/EDITABLE或ABANDONED生命周期）和repair_draft_revision（不可变完整修订）。每次PUT表示完整替换当前资料内容并追加一条修订，而非覆盖旧行。允许不完整草稿：格式合法不等于资料完整，complete不等于正式提交资格，formalSubmissionAvailable恒false。

本阶段不写repair_order、repair_record、order_revision、order_time_preference、order_estimate_history及Flowable表。正式表继续保持R2语义；期望、意愿、默认/人工估时及快照直接存于草稿修订，避免复制正式工单表族。

user_address只持有权威room_id与私人联系人，层级从既有空间关联投影。停用代替删除，默认地址部分唯一索引、全局既有guard及事务保障并发；默认失效后在下次地址写操作选择最小ID且启用/空间有效的地址，没有候选则没有默认。

room_resident由residency:manage明确确认，主体需有residency:own业务资格。有效区间[start,end)，撤销或到期即时失去私人设施数据范围。地址不是住宿登记，二维码不是授权，管理员/维修角色无自动入室或私人查询特权。

## 快照与附件预留

修订保存规范化位置及联系人、分类路径、故障项目、必要设施投影的结构化JSON快照，标识FK仅用于来源追踪，历史查询不重新拼接可变来源。默认估时快照保存在item_snapshot与estimated_minutes，人工调整必须带原因，以修订历史展示，不产生正式估时协商记录。

E阶段附件应关联明确repair_draft_revision.id，聚合通过revision.draft_id派生，不接受两个可冲突的归属来源。附件集合变化也应生成新修订，不向历史修订追加、删除或替换既有附件关系；若未来关联表同时存draft_id与revision_id，可用现有(id,draft_id)复合唯一键建立FK。旧修订附件关系不可被新版本重绑；未来绑定时必须核验上传者、所有权、文件状态与修订范围。本轮不创建文件表、不上传、不存储、不处理图片。

## V2.1正式提交约束（仅设计）

从本人EDITABLE聚合的明确修订发起；重新校验当前权限、入住关系、有效空间、设施范围、项目关联、完整性和时间窗口。锁聚合并核验version，正式工单/正式快照/时间偏好/初始估时/真实Flowable实例/转换关联同一DataSource同事务创建，失败整体回滚，草稿仍可编辑或重试。转换关系对draft_id唯一，防并发重复成单；成功后通过后续迁移增加明确转换生命周期并冻结原资料修改。

不在本轮实现提交API、转换表或伪造process_instance_id。正式转换原子性及防重属于V2.1验收，不将上述约束描述为已通过测试。

## 权限及迁移

新增address:own、residency:own、residency:manage、draft:own。空用户库必须为可验证种子，ADMIN完整覆盖20权限，STUDENT仅新增三个own能力，REPAIRER不自动新增。非空库必须有可信逐用户新权限映射，生成每人独立补充角色D7_U_<ID>，不改共享角色，受影响用户auth_version/version增加，并验证最后全权保管人。

V7建模与授权，V8修订链/当前版本延迟外键，V9空用户模板校验、V10恢复R2的可空入住结束时间。V1–V6不改。group=true使待执行增量一起提交，不能跳过V9运行到一半。未知来源自定义角色/模板、缺少确认映射及丢失保管人均拒绝。当前dev用户0，可以验证模板后升级，不创建默认账号。
