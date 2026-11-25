# 宿舍报修系统：前后端联调启动教程

更新日期：2026-11-24。面向没有后端开发经验的Vue成员。

## 1. 当前本机已经准备好的环境

- 后端目录：`D:\dorm-repair\dorm-repair-backend`。
- 运行地址：`http://127.0.0.1:18080`，配置为integration，仅本机可以连接。
- 数据库：`dorm_repair_flowable_a_test_20261123`，自动建表关闭。
- 临时随机JWT密钥仅在进程内存，没有复用或覆盖正式JWT_SECRET。
- 已允许来源：`http://localhost:5173`、`http://127.0.0.1:5173`。
- 待审核演示工单206，可供前端继续操作；验证工单207已完成。
- 原8080服务未操作，不要使用8080执行本次联调写入。

| 角色 | 用户名 | ID |
|---|---|---|
| 学生 | frontend_demo_1795510791769_student | 1077 |
| 管理员 | frontend_demo_1795510792081_admin | 1078 |
| 维修人员 | frontend_demo_1795510792186_repairer | 1079 |

三个账号的随机登录密码仅保存在本机文件：

`D:\dorm-repair\dorm-repair-backend\.env.frontend-demo.json`

用本地编辑器打开即可查看。该文件已被Git忽略，不要上传GitHub；向队友提供密码请私下交付。账号仅存在于当前隔离库，新电脑需要另行创建。

本机后台服务日志：`target/frontend-integration.log`。实际进程ID保存在`target/frontend-integration.pid`，本次确认PID为38952。不要重复启动18080实例。需停止时先核对该端口仍由本项目进程占用，再由操作者停止它，不终止8080。

## 2. 新电脑安装与检查

需要Git、JDK21、Maven3.9.x、PostgreSQL17。数据库服务使用5432端口。IDEA可选，终端也可以启动。

```powershell
java -version
mvn -version
psql --version
git --version
```

Maven输出的Java版本应为21。PostgreSQL安装时设置的数据库用户名和密码要记下，常见用户名是postgres，不能照搬作者电脑的sota。

如果psql不存在，在当前终端追加实际安装目录，例如：

```powershell
$env:Path = "C:\Program Files\PostgreSQL\17\bin;" + $env:Path
```

作者安装路径是D:\pgsql\bin，队友需使用自己的真实路径。Vue的Node.js与包管理器版本按前端项目package.json准备；不要在本后端目录新建Vue项目。

## 3. 克隆代码并补上本轮CORS修正

```powershell
git -c core.autocrlf=false clone https://github.com/XinyuLiu-SCU/dorm-repair.git dorm-repair-backend
cd dorm-repair-backend
git config --local core.autocrlf false
git switch -c frontend-integration backend-v1.0-flowable
```

仓库根目录直接包含pom.xml和src，不需要再进入另一个backend目录。标签对应提交ff78d7730e0851cabdc683136a2ba0f3adea937c。

迁移脚本有字节校验和，克隆时保留换行字节。如果校验失败，先检查文件与换行转换，不跳过校验强行初始化。

**本次教程和CORS修正尚未提交或推送。**队友从现有标签克隆后，需打开`src/main/java/com/dormrepair/security/SecurityConfig.java`，仅将以下方法名cors改为corsConfigurationSource：

```java
// 修改前
@Bean CorsConfigurationSource cors(@Value("${app.cors.origins}") List<String> origins) {
// 修改后
@Bean CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.origins}") List<String> origins) {
```

保留参数和方法体不变。这让Spring Security识别已有CORS配置，没有关闭认证。本机已修复并测试；若后续获准发布修复，克隆已修复版本就不必手动改。

## 4. 配置数据库身份

以下三种秘密不要混淆：数据库密码用于连接PostgreSQL；JWT密钥用于后端签名；业务账号密码用于学生/管理员/维修人员登录。

在后端根目录的PowerShell设置自己电脑的数据库用户：

```powershell
$env:INTEGRATION_DB_USER = "postgres"  # 换成实际数据库用户
$secureDbPassword = Read-Host "请输入PostgreSQL数据库密码" -AsSecureString
$env:INTEGRATION_DB_PASSWORD = [System.Net.NetworkCredential]::new("", $secureDbPassword).Password
$env:PGPASSWORD = $env:INTEGRATION_DB_PASSWORD
psql -h localhost -p 5432 -U $env:INTEGRATION_DB_USER -w -d postgres -c "select current_user, current_database();"
```

输入不会显示。只有已经确认本机允许免密连接时才可使用空密码，不要为方便联调更改数据库认证规则。

## 5. 全新隔离库初始化，只执行一次

**作者电脑上的隔离库已准备完毕，跳过本节。不得初始化或迁移正式库dorm_repair_db。**

队友在自己的新电脑上创建固定名称的隔离库：

```powershell
createdb -h localhost -p 5432 -U $env:INTEGRATION_DB_USER -w dorm_repair_flowable_a_test_20261123
```

提示数据库已存在时不要删除重建，也不要继续盲目执行迁移。先检查是否已经初始化：

```powershell
psql -h localhost -U $env:INTEGRATION_DB_USER -w -d dorm_repair_flowable_a_test_20261123 -c "select table_name from information_schema.tables where table_schema='public' order by table_name;"
```

只有确认是刚创建的新隔离库，才依次执行：

```powershell
psql -h localhost -U $env:INTEGRATION_DB_USER -w -d dorm_repair_flowable_a_test_20261123 -v ON_ERROR_STOP=1 -f src/test/resources/workflow/business-fixture.sql
powershell -NoProfile -ExecutionPolicy Bypass -File .\deployment\postgres-flowable-7.2.0\Invoke-Migration.ps1 -Database dorm_repair_flowable_a_test_20261123 -UserName $env:INTEGRATION_DB_USER -PsqlPath (Get-Command psql).Source
```

ExecutionPolicy只影响该子进程，不修改全局策略。脚本校验文件后，在事务中建立引擎表、关联字段和历史登记，不导入正式数据。不能把目标库换成dorm_repair_db。

预期有30张act_表、2张flw_表，schema.version=7.2.0.2；repair_order有process_instance_id，存在repair_order_legacy。新库没有用户/工单是正常情况。重复执行迁移会被拒绝。

## 6. 创建自己知道密码的联调账号

```powershell
mvn -q test "-Dtest=AuthenticationTest,AuthorizationTest"
mvn dependency:build-classpath "-Dmdep.outputFile=target/runtime-classpath.txt" "-DincludeScope=runtime"
$cp = "target/classes;" + (Get-Content target/runtime-classpath.txt -Raw).Trim()
$env:DB_URL = "jdbc:postgresql://localhost:5432/dorm_repair_flowable_a_test_20261123"
$env:DB_USER = $env:INTEGRATION_DB_USER
$env:DB_PASSWORD = $env:INTEGRATION_DB_PASSWORD
java -cp $cp com.dormrepair.tools.CreateTestUser frontend_student STUDENT
java -cp $cp com.dormrepair.tools.CreateTestUser frontend_admin ADMIN
java -cp $cp com.dormrepair.tools.CreateTestUser frontend_repairer REPAIRER
```

每次提示New account password时，输入新业务账号的登录密码，不是数据库密码。至少12字符，最多72个UTF-8字节。Created account表示成功；同名账号保持不变，不会被重置。

工具使用DB_URL/DB_USER/DB_PASSWORD，后端integration使用INTEGRATION_DB_USER/INTEGRATION_DB_PASSWORD。不要漏设DB_URL而意外连接默认正式库。

创建完成后可移除当前终端DB_URL、DB_USER、DB_PASSWORD及PGPASSWORD；保留integration运行所需变量。

## 7. 生成随机密钥并启动18080

```powershell
$env:INTEGRATION_JWT_SECRET = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:CORS_ORIGINS = "http://localhost:5173,http://127.0.0.1:5173"
mvn spring-boot:run "-Dspring-boot.run.profiles=integration"
```

保持终端打开。看到Tomcat started on port 18080及Started DormRepairApplication后再请求接口。手工启动的服务按Ctrl+C停止。

密钥不要打印、截图或提交。当前实现直接使用完整Base64文本的UTF-8字节，不另行解码。重启换密钥后旧Token会失效，重新登录即可。

IDEA启动时激活integration，并在实际运行配置中设置上述INTEGRATION变量及CORS_ORIGINS。当前PowerShell变量不会自动传给已运行的IDEA。

## 8. 最小接口验证

在IDEA HTTP文件中执行，###分隔请求，请求头与JSON之间保留空行，不要复制Markdown三反引号。

```http
### 登录
POST http://127.0.0.1:18080/api/auth/login
Content-Type: application/json

{"username":"frontend_student","password":"填写自己设置的业务密码"}

### 查询身份
GET http://127.0.0.1:18080/api/auth/me
Authorization: Bearer 替换为登录返回的完整accessToken

### 本人列表
GET http://127.0.0.1:18080/api/repair-orders?page=0&size=20
Authorization: Bearer 替换为完整学生Token

### 创建
POST http://127.0.0.1:18080/api/repair-orders
Authorization: Bearer 替换为完整学生Token
Content-Type: application/json

{"title":"前端联调报修","description":"水龙头漏水","location":"1栋101"}

### 待办，分别使用不同角色Token查看
GET http://127.0.0.1:18080/api/workflow/tasks?page=0&size=20
Authorization: Bearer 替换为对应角色Token
```

登录应200；me应返回正确角色；列表200；创建返回新工单ID；待办返回items/page/size/total，空items也可能正常。

完整接口契约见FRONTEND-API.md。流程：学生创建→管理员审核→管理员分配维修人员→维修人员开始/提交→学生验收。返工继续由原维修人员开始/提交。只操作新演示工单，不碰历史1、2、3。

## 9. 同一台电脑运行前后端

Vue使用5173，后端使用18080。推荐Vite代理；保留原配置的vue插件等内容，仅合并server：

```js
server: {
  port: 5173,
  strictPort: true,
  proxy: {
    '/api': { target: 'http://127.0.0.1:18080', changeOrigin: true }
  }
}
```

不要rewrite去掉/api，后端路径本来就有/api。Axios示例：

```js
const api = axios.create({ baseURL: '/api', timeout: 15000 })
api.interceptors.request.use(config => {
  const token = sessionStorage.getItem('accessToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
```

登录后保存data.accessToken，不使用Cookie会话，不需要withCredentials。直接跨域请求18080时，页面Origin必须在CORS_ORIGINS中。localhost和127.0.0.1不同，端口变化也会改变Origin。

## 10. 不同电脑运行前后端

假设A运行后端，B运行Vue和Vite，两者在可互通的局域网。

1. A用ipconfig查真实IPv4，例如192.168.1.20（示例必须替换）。
2. 默认integration只监听127.0.0.1，B不能访问。A操作者决定开放局域网时，在A启动终端设置`$env:SERVER_ADDRESS = "0.0.0.0"`，然后重新启动integration实例。
3. B的Vite代理target改为`http://192.168.1.20:18080`，Axios仍是相对路径/api。B的localhost是B自己，不能指代A。
4. 若防火墙拦截，由A管理员批准并仅在私有局域网放行TCP18080。本轮没有更改防火墙。不需要开放数据库5432，也不要映射公网端口。
5. B先用`Test-NetConnection 192.168.1.20 -Port 18080`检查连通；GET /api/auth/me返回401表示网络已通。

页面从localhost:5173打开时，Origin仍是http://localhost:5173；页面从B的局域网IP打开时，A的CORS_ORIGINS还要加入http://B的IP:5173。代理target和页面Origin是两回事。

如其他设备还要访问B的Vue，B可配置Vite host，但不要无条件设置allowedHosts:true。**本轮运行仍只绑定127.0.0.1，跨电脑监听和网络连接尚未实际开启或验证。**

## 11. 常见报错

| 现象 | 检查 |
|---|---|
| Connection refused | 服务是否启动、是否18080、跨电脑是否仍绑定127.0.0.1 |
| 401 | Token是否完整/过期，重启是否换密钥；无Token的me返回401是正常的 |
| 403 | 角色/归属；OPTIONS为403时检查CORS方法名修正和Origin白名单 |
| 404 | ID、归属、代理是否误去掉/api |
| 409 | 重复操作或状态变化，刷新详情和workflow |
| 503 | integration是否激活、数据库是否可达；不要因此开启正式库写入 |
| 密钥长度不足 | 在实际启动进程设置完整INTEGRATION_JWT_SECRET |
| sota用户不存在 | 设置自己数据库的INTEGRATION_DB_USER |
| 表不存在 | 是否初始化了正确隔离库；不在正式库尝试建表 |
| 校验和失败 | 核对干净文件与换行，不跳过校验 |
| 18080占用 | 不重复启动，核对进程身份后停止自己的实例 |

## 12. 本轮结果与仍需联调的事项

- 28项实际HTTP检查通过：三角色登录/me、列表、创建、待办、完整流程、记录、401/403及CORS预检。
- 本轮CORS修复的AuthenticationTest和AuthorizationTest共10项通过，没有宣称重跑全部65项。完整HTTP套件会占用18080，运行前先停止联调服务。
- 正式库业务数据与验证基线一致；无正式库DDL/DML、无正式开关或密钥变更。
- 扫描已推送的1个提交、51个文件版本，未发现真实JWT、私钥、完整密码哈希或带密码URL。测试代码中的明确test-only固定值只是测试夹具，不能复用于正式环境。
- GitHub分支和标签仍指向ff78d77；本轮CORS修复、测试和教程仅在本地，未提交、未推送。
- 静态扫描不能保证识别所有形式的秘密；本次未发现需要撤销历史或轮换正式凭据的证据。
- 尚需Vue页面、路由守卫、浏览器表现及跨电脑网络联调；没有检查未提供的Vue仓库，也未修改前端文件。

参考：[Vite代理官方文档](https://vite.dev/config/server-options.html#server-proxy)、[Spring CORS官方文档](https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html)。接口细节见FRONTEND-API.md。
