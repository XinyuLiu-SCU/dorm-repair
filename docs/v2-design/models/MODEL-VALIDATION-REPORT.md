# R2候选BPMN实际静态验证报告

日期2026-11-25。全部模型isExecutable=false，目录不在Spring自动部署路径。

## 实际运行并通过

- 五份XML：XML可解析、ID唯一、sequenceFlow端点属于同一scope。
- Flowable 7.2.0 BpmnXMLConverter.validateModel及convertToBpmnModel：五份全部通过XSD与本地转换器解析。
- DI：13个流程/子流程平面，正交连线对非端点节点矩形内部的碰撞数为0；包括边界事件退出路线。
- 原十份文档、变更日志及候选模型均包含在合订本/打包清单中；UTF-8、Markdown围栏、压缩包与逐文件SHA256校验。

## 未执行与限制

没有创建ProcessEngine，没有部署、创建job、启动实例、连接数据库或修改8080/密钥。未跑运行时路径/权限/并发/回滚测试，未执行引擎ProcessValidator的全部语义检查或真实bpmn-js浏览器渲染。

DI检查不等于人工视觉验收；连线之间允许交叉/共享走廊，条件名称、标签、子图下钻的最终编辑器展示仍应人工核对。可用包内SVG基础预览查看13个平面，但SVG不是生产前端渲染验收。

callActivity定义ID表达式、局部变量输出、所有占位delegate、候选权限/assignee、定时执行器行为及回滚必须在实施阶段真实验证。候选XML中的r2ReviewOnlyDelegate未实现；任何人不得把通过XSD误读为可直接投产。

## 技术参考

[Flowable官方BPMN构件文档](https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs/)用于核对非中断timer、call按定义ID、multi-instance。Flowable7.2.0本地模型/转换器源码还核对了useLocalScopeForOutParameters。网页及静态解析均不能替代运行验证。
