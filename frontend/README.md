# 宿舍报修管理系统前端

启动本地演示时，运行 start-frontend.cmd 或 npm.cmd run dev。演示工单保存在当前浏览器的 localStorage。可以切换学生、管理员、维修人员三个角色，演示提交、审核、派单、维修、验收和返工。

## 联调队友的后端

1. 按后端 FRONTEND-API.md 的步骤准备隔离库、启动 integration profile 并创建联调账号。
2. 在本目录执行 Copy-Item .env.example .env.local。联调后端默认是 http://127.0.0.1:18080，必要时修改 .env.local 中的 VITE_BACKEND_URL。
3. 重启 Vite，用后端已创建的账号登录。页面依据 /api/auth/me 返回的角色打开对应工作区。
4. 用学生账号提交，管理员审核并从后端维修人员列表中派单，维修人员提交结果后再由学生验收。申请返工后由原维修人员重新开始。

后端契约基于提交 ff78d7730e0851cabdc683136a2ba0f3adea937c。Token 保存在当前标签页的 sessionStorage。退出登录清除浏览器中的 Token。请求由 Vite 代理转发。正式部署时需由部署服务器配置 /api 转发。

前端验证命令为 npm.cmd run lint 和 npm.cmd run build。
