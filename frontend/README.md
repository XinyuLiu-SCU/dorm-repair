# Vue前端

从仓库根目录阅读README.md：后端在backend/，前端在frontend/。默认开发需复制.env.example为本地.env.local并启用live，不把模拟页面当真实验收。

安装：`npm.cmd --prefix frontend ci`（仓库根目录执行）。启动：`powershell -ExecutionPolicy Bypass -File scripts/start-frontend.ps1`。

验证：在frontend目录执行`npm.cmd run lint`和`npm.cmd run build`。

Vite使用5173并代理/api到18080，Token保存在当前标签页sessionStorage。源码保持Vue3、Element Plus及现有布局。详细接口见根目录FRONTEND-API.md；历史浏览器报告见docs/v1.1/V1.1-FINAL-REVIEW.md。
