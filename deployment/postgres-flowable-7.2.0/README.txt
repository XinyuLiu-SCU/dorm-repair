正式迁移候选方案（未获执行授权，当前仅准备和测试）

适用：Spring Boot 3.5.16、Flowable 7.2.0、PostgreSQL 17.6，当前正式库dorm_repair_db尚无act_表和process_instance_id。

结构差异
正式库仅sys_user、repair_order、repair_record业务表，没有流程字段、历史登记或引擎表。
隔离A/B/C/D库另有30张act_表和2张flw_表（共32张引擎表）、repair_order_legacy及测试专用stage_a_tx_probe。
正式迁移只新增process_instance_id、唯一索引、repair_order_legacy、30张act_表和2张flw_表（共32张引擎表）和dormrepair_schema_history；不迁移测试探针、测试账号或测试数据。
不删除或重建任何业务表，不更新已有业务字段。历史ID1/2/3和全部其他现存工单登记为LEGACY，process_instance_id保持null。

引擎SQL来源和版本
010-common.sql来自flowable-engine-common:7.2.0。
020-engine.sql、030-history.sql来自flowable-engine:7.2.0。
只将引号外SQL标识符和关键字转为小写，保留字符串字面量，MANIFEST.json记录官方原文及交付文件SHA256。
引擎内部schema.version/common.schema.version预期7.2.0.2，和Maven版本7.2.0是不同版本字段。
040-business-association.sql是B001关联迁移去除独立事务后的内容，不能同时再执行B001。
apply.sql统一事务、目标库校验、5秒锁等待、120秒statement超时、历史数据逐行对比断言、版本记录。
Invoke-Migration.ps1先校验MANIFEST再执行apply.sql和verify.sql。尚未对正式库运行该脚本。

审批后执行顺序
1. 确认目标库、数据库用户、SQL清单和维护窗口，停止所有写入者/旧版本应用；不得边写边迁移。
2. 执行完整备份并记录业务表行数、工单1/2/3、数据摘要；当前schema-only测试证据不是完整恢复备份。
   示例：D:\pgsql\bin\pg_dump.exe -h localhost -U sota -w -d dorm_repair_db -Fc -f <批准的备份绝对路径>
   备份包含用户哈希，须保存在受控位置，不发给前端成员。
3. 核对MANIFEST和目标连接。使用已有认证方式，不把数据库密码放入命令参数/源码。
4. 审批后才可执行：
   .\deployment\postgres-flowable-7.2.0\Invoke-Migration.ps1 -Database dorm_repair_db
   该命令尚未执行；不要为验证脚本而对正式库尝试运行。
5. 检查verify.sql：30张act_表和2张flw_表，schema.version=7.2.0.2，版本记录D001；ID1=REJECTED、2/3=COMPLETED且实例字段均null。
   首次激活前linked应为0，legacy_orders应等于原工单总数；missing_legacy和unexpected_legacy_association均0。
   用户/记录及原工单字段与备份摘要一致。数据库层DDL不会自动完成BPMN部署。
6. 配置正式实例：JWT_SECRET保持现有安全密钥，DB_PASSWORD按现有认证方式；可用SPRING_DATASOURCE_URL和SPRING_DATASOURCE_USERNAME显式设正式库连接。
   APP_WORKFLOW_ENABLED=true、FLOWABLE_PROCESS_ENABLED=true、FLOWABLE_DATABASE_SCHEMA_UPDATE=false。
   FLOWABLE_HISTORY_LEVEL=audit、FLOWABLE_IDM_ENABLED=false、FLOWABLE_EVENTREGISTRY_ENABLED=false、FLOWABLE_ASYNC_EXECUTOR_ACTIVATE=false。
   不激活integration或stage-c profile，不使用INTEGRATION_JWT_SECRET、测试库或测试端口作为正式配置。
7. 经批准重启当前后端，确认部署dormRepair，版本1，且共用事务资源启动检查通过。
8. 审批范围允许时，仅新增演示工单做正式冒烟，不能操作历史工单1/2/3；核对前端登录、查询、任务和写入。

演练记录
独立演练库dorm_repair_migration_test_20261123_d，从正式库空结构复制，使用合成用户、合成工单1/2/3和合成维修记录，不复制正式数据。
apply.sql事务提交成功，30张act_表和2张flw_表（共32张引擎表），历史登记3条，linked=0，版本7.2.0.2。
故意传错expected_db时psql退出3且未改结构，验证目标库防护。
MigrationRehearsalTest进一步验证关闭自动DDL仍能启动并创建关联流程的新工单，历史合成数据保持原样。

失败与回退
目标错误、校验失败或SQL异常时ON_ERROR_STOP退出，未提交事务由连接关闭回滚；先查原因，不跳过错误继续启用。
迁移已提交但尚未启用业务：保持工作流开关关闭，保留新增表/字段，排错后继续；没有自动drop回退脚本。
已有新流程工单后：关闭写入并保留全部流程和业务数据，修复当前版本；不能回退到绕过Flowable写工单的旧应用。
若确需从备份恢复，会丢失备份后的数据，是另一个需单独批准的破坏性操作；本轮不实施。
升级引擎时不覆盖这些脚本，不开启自动schema update。准备下一版本官方升级脚本，独立演练并新增版本记录后再审批。

当前状态
未执行正式迁移，未重启正式应用，正式写入开关保持false，JWT_SECRET未更改。只有用户批准上述操作后才能上线正式工作流。
