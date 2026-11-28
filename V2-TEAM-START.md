# V2.0 Final 队友拉取与本地启动

本次交接仅同步已验收后端及文档，不修改Vue页面。使用分支 `feature/v2-development`；核心基线标签 `v2.0-core-complete`。master和队友feature/vue-frontend保留原状。

## 获取代码，保留正在开发的前端

新目录克隆：

```powershell
git clone --branch feature/v2-development https://github.com/XinyuLiu-SCU/dorm-repair.git dorm-repair-v2
cd dorm-repair-v2
git fetch origin --tags
```

如果已有队友正在编辑的仓库，建议另建工作树，不在原分支直接pull或覆盖：在原仓库根目录执行下面命令。新分支名已存在时请另选新名字，不强制重建。

```powershell
git status
git fetch origin --tags
git worktree add -b frontend/v2-integration ../dorm-repair-v2 origin/feature/v2-development
cd ../dorm-repair-v2
```

已经在干净的本地feature/v2-development上时，可用 `git pull --ff-only origin feature/v2-development`。如果有未提交代码或分叉，先保留并报告，不使用reset --hard或强制合并。页面分工及队友未推送内容仍需确认，不能把本轮同步视作已合入其电脑上的工作。

## 后端：队友本机隔离环境

需要Java21、Maven、PostgreSQL17及psql/createdb命令。准备一个可创建开发数据库的本机数据库账号；数据库固定为dorm_repair_v2_dev，端口5432。不要连接正式dorm_repair_db，不使用正式JWT密钥。

在仓库根目录的PowerShell终端A：

```powershell
$env:V2_DB_USER = Read-Host '本机PostgreSQL用户名'
$env:V2_DB_PASSWORD = [Net.NetworkCredential]::new('', (Read-Host '数据库密码（隐藏输入；本机免密时可留空）' -AsSecureString)).Password
$jwtBytes = New-Object byte[] 32
$jwtRng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $jwtRng.GetBytes($jwtBytes) } finally { $jwtRng.Dispose() }
$env:V2_JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
./scripts/prepare-v2.ps1 -PsqlPath psql
./scripts/start-v2.ps1
```

psql不在PATH时，将-PsqlPath改为本机psql.exe完整位置。prepare-v2创建白名单内V2开发/测试数据库，已存在时不重建；正式库不会被该工具使用。start-v2启用v2-dev,v2-core，监听127.0.0.1:18081，Flyway迁移业务表至V13，随后隔离引擎建表/部署；异步执行器关闭。若18081已占用，脚本拒绝启动，不自行杀进程。

保留终端A运行服务。另开终端B，在同一仓库根目录再次设置本机V2_DB_USER/V2_DB_PASSWORD，然后显式初始化自己本机的首个管理员：

```powershell
$env:V2_DB_USER = Read-Host '本机PostgreSQL用户名'
$env:V2_DB_PASSWORD = [Net.NetworkCredential]::new('', (Read-Host '数据库密码' -AsSecureString)).Password
$env:V2_BOOTSTRAP_ADMIN_USERNAME = Read-Host '首个合成管理员账号名（3至50个合法字符）'
$env:V2_BOOTSTRAP_ADMIN_PASSWORD = [Net.NetworkCredential]::new('', (Read-Host '管理员随机强密码（至少12字符，最多72个UTF8字节）' -AsSecureString)).Password
try { ./scripts/initialize-v2-admin.ps1 } finally {
  Remove-Item Env:V2_BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
  Remove-Item Env:V2_BOOTSTRAP_ADMIN_USERNAME -ErrorAction SilentlyContinue
}
./scripts/initialize-v2-demo.ps1
```

初始化不会覆盖已有管理员或重置密码；已有不同初始化账号时会安全拒绝，请使用原有账号，不能清库绕过。合成目录脚本不创建默认密码，会幂等准备校园、房间、项目及设施。数据库内账号/入住/排班和私有图片不随Git同步，本机已有7个联调账号也不会传到队友数据库。队友通过B接口创建自己的学生和维修账号、独立普通业务管理角色，再通过D接口确认入住，核心接口配置技能和未来可用时间。权限模板与本机演示现况见FINAL-INTEGRATION-PREP.md；不要把凭据提交到仓库。

V2_DB_PASSWORD/V2_JWT_SECRET仅为当前PowerShell进程环境；上述命令不打印密钥。重开终端需要重新设置；更换JWT密钥后重新登录。此运行方式适用于队友本机独立后端，127.0.0.1指各自电脑，不能直接当作跨电脑访问地址。

## 前端启动及尚待接入的边界

在frontend/.env.local中设置（该文件不入Git）：

```dotenv
VITE_DATA_MODE=live
VITE_BACKEND_URL=http://127.0.0.1:18081
```

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

**目前Vue源码仍是V1.1工作台，尚未完成V2适配。** 上述代理仅供V2前端开发，设置环境变量不等于完整页面已经可用。先接/v2/auth及permissions、统一分页与错误，再接资料/图片/成单等真实API；不要用旧V1提交入口代替V2草稿正式转换。axios baseURL=/api，调用写/v2/...。当前原本机5173服务仍代理18080，已原样保留。

## 文档入口

|文档|内容|
|---|---|
|[FRONTEND-HANDOFF.md](FRONTEND-HANDOFF.md)|页面、身份及接入边界|
|[FINAL-INTEGRATION-PREP.md](FINAL-INTEGRATION-PREP.md)|前端进度、建议分工、演示环境与权限隔离|
|[V2.0-B-API.md](V2.0-B-API.md)|登录、用户/角色/权限、个人中心、审计|
|[V2.0-C-API.md](V2.0-C-API.md)|分类、空间、设施、二维码|
|[V2.0-D-API.md](V2.0-D-API.md)|地址、入住、资料修订与时间偏好|
|[E-API.md](E-API.md)|私有图片上传、绑定、历史与授权读取|
|[CORE-API.md](CORE-API.md)|正式成单、工作流、预约、推荐、维修、验收、评价、统计|

业务范围继续以FINAL-SCOPE.md为准，不按长期R2清单扩展。本轮没有修改业务源码、迁移数据库或部署服务；远端同步后仍须完成真实Vue集成与浏览器最终验收。
