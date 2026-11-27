# 宿舍智慧维修服务管理系统

V1.1：Spring Boot 3.5.16 / Java21 / PostgreSQL17 / JdbcTemplate / JWT / Flowable7.2.0；Vue3 / Element Plus。

## 目录

- backend/：pom.xml、src、api-test.http、deployment。
- frontend/：Vue、Vite及浏览器测试。
- scripts/：隔离数据库准备及前后端启动。
- docs/v1.1/：历史验收报告，旧路径和临时PID仅供追溯。
- FRONTEND-API.md：当前接口契约。

后端已从仓库根移到backend。旧根target是被忽略的历史产物，不从那里启动归档后的应用。正式库尚未迁移，本教程仅使用隔离库和18080，不占8080。

## 1. 环境及克隆

安装JDK21、Maven3.9.x、Node22.12+或Node24、PostgreSQL17、Git。检查java -version、mvn -version、node -v、psql --version。Windows示例用PowerShell，PostgreSQL bin须在PATH。

```powershell
git clone https://github.com/XinyuLiu-SCU/dorm-repair.git
cd dorm-repair
git switch --detach fullstack-v1.1-stable
```

开发从标签创建自己的分支。旧backend-v1.0-flowable标签和原始feature/vue-frontend提交保留。

## 2. 新电脑初始化隔离库

```powershell
$env:INTEGRATION_DB_USER = "postgres" # 自己的数据库用户
$secureDbPassword = Read-Host "数据库密码" -AsSecureString
$env:INTEGRATION_DB_PASSWORD = [System.Net.NetworkCredential]::new("", $secureDbPassword).Password
$env:PGPASSWORD = $env:INTEGRATION_DB_PASSWORD
powershell -ExecutionPolicy Bypass -File scripts/prepare-integration.ps1 -UserName $env:INTEGRATION_DB_USER -PsqlPath (Get-Command psql).Source -IncludeRehearsal
```

脚本仅处理固定测试库dorm_repair_flowable_a_test_20261123和dorm_repair_migration_test_20261123_d。默认只准备第一个，IncludeRehearsal用于完整回归。已存在的库跳过且不写入，不自动删除或重建；若已有库不完整，请人工检查。

新库使用合成历史夹具，不复制正式数据。显式创建32张引擎表（30张act_及2张flw_）和业务关联；SQL固定LF换行并有校验和。没有正式库自动迁移入口。

## 3. 三类演示账号

```powershell
Push-Location backend
mvn package -DskipTests
mvn dependency:build-classpath "-Dmdep.outputFile=target/runtime-classpath.txt" "-DincludeScope=runtime"
$cp = "target/classes;" + (Get-Content target/runtime-classpath.txt -Raw).Trim()
$env:DB_URL = "jdbc:postgresql://localhost:5432/dorm_repair_flowable_a_test_20261123"
$env:DB_USER = $env:INTEGRATION_DB_USER
$env:DB_PASSWORD = $env:INTEGRATION_DB_PASSWORD
java -cp $cp com.dormrepair.tools.CreateTestUser frontend_student STUDENT
java -cp $cp com.dormrepair.tools.CreateTestUser frontend_admin ADMIN
java -cp $cp com.dormrepair.tools.CreateTestUser frontend_repairer REPAIRER
Pop-Location
```

交互设置业务密码（至少12字符、最多72个UTF-8字节），不是数据库密码。同名账号保持不变。占位哈希用户不能登录。不要提交密码、Token、.env或IDEA运行配置。

## 4. 启动

终端一保留INTEGRATION_DB变量：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-backend.ps1
```

自动为当前联调进程生成随机INTEGRATION_JWT_SECRET，不打印、不写文件；18080、integration、自动DDL关闭。换密钥后重新登录。IDEA打开backend/pom.xml并设置相同profile和变量。

终端二：

```powershell
npm.cmd --prefix frontend ci
powershell -ExecutionPolicy Bypass -File scripts/start-frontend.ps1
```

访问http://127.0.0.1:5173/#/login。首次复制公开.env.example为本地.env.local，默认live，/api代理到18080，不覆盖已有配置。Ctrl+C停止各自实例，不停止8080。

跨电脑：后端操作者明确设置SERVER_ADDRESS=0.0.0.0后重启隔离实例；前端VITE_BACKEND_URL用后端实际局域网IP:18080；CORS_ORIGINS列出浏览器页面实际来源。防火墙需管理员批准，仅私有网络18080，不开放5432或公网。默认只监听本机。

## 5. 回归

```powershell
$env:FLOWABLE_TEST_DB_USER = $env:INTEGRATION_DB_USER
$env:FLOWABLE_TEST_DB_PASSWORD = $env:INTEGRATION_DB_PASSWORD
mvn -B -f backend/pom.xml test "-Dflowable.stageA=true" "-Dflowable.stageB=true" "-Dflowable.stageC=true" "-Dflowable.stageD=true"
npm.cmd --prefix frontend run lint
npm.cmd --prefix frontend run build
```

完整HTTP测试使用18080，先停止手工联调实例。需初始化两个测试库；默认不带stage标志的mvn test不执行隔离库场景。浏览器说明在frontend/tests/e2e/RUNNING.txt；私有演示JSON不在Git，需自己准备。

正常流程：学生提交→管理员审核/派单→维修人员开始/提交→学生验收。驳回、连续返工、真实记录、待办及allowedActions已验收；接口见FRONTEND-API.md。

## 6. 边界

正式默认配置仍关闭工作流读写和自动建表。正式方案在backend/deployment/postgres-flowable-7.2.0/README.txt，未批准不能执行。不要将测试profile、测试账号或test-only固定密钥当正式配置。

Git不包含实际密钥、密码、本地环境、IDEA配置、target、node_modules或浏览器截图。历史报告的绝对路径/PID只作快照。

V2设计仅在独立feature/v2-development分支docs/v2-design/；V1.1稳定标签不包含V2业务。本轮不实现V2、不执行正式迁移。

## V2.0-A 隔离开发基础

R2设计本地标签v2-design-r2。V2仅在独立开发分支；本阶段是迁移/测试/接口公共基础，不包含RBAC等业务。

运行与测试见[隔离环境说明](docs/v2-development/V2.0-A-SETUP.md)，原理见[迁移机制](docs/v2-development/V2.0-A-MIGRATIONS.md)。使用18081和明确v2_dev/v2_test库，不停止8080/18080，不执行正式迁移。

## V2.0-B 动态身份与用户管理

V2.0-A本地归档标签v2.0-a-foundation。B阶段接口见[V2.0-B-API.md](V2.0-B-API.md)，首个合成管理员须按[初始化说明](docs/v2-development/V2.0-B-INITIALIZATION.md)显式创建，没有默认密码。scripts/test-v2.ps1同时运行原99项与B阶段新增验收，均使用V2隔离测试库。
