# E：私有图片接口（前端可开始接入）

基础/api/v2，沿用JWT、字符串ID、requestId与统一错误；需要draft:own及文件/草稿本人所有权。管理员没有读取他人私图的后门。使用独立18081开发环境，不切换8080/18080。

|接口|用途|
|---|---|
|POST /images，multipart字段file|上传一张，返回id/contentType/width/height/bytes/state/contentUrl|
|GET /images/{id}|本人未取消图片元信息|
|GET /images/{id}/content|授权二进制，no-store/nosniff；不要把Token放URL|
|POST /images/{id}/cancel|取消STAGED未绑定文件；已绑定409，已取消404|
|POST /drafts；PUT /drafts/{id}|原D完整表单增加imageIds字符串数组；最多6张、不得重复|
|GET /drafts/{id}/revisions/{number}/images|指定本人确切修订的图片，顺序稳定；不存在/他人404|
|POST /images/cleanup|维护人员手动清理，须同时role:manage和user:manage，不是审计只读权限|

上传/保存成功200。上传返回示例：
```json
{"id":"51","contentType":"image/jpeg","width":1200,"height":900,"bytes":123456,"state":"STAGED","contentUrl":"/api/v2/images/51/content"}
```

JPG/PNG/WebP按实际字节+ImageIO解码验证，不信扩展名及客户端MIME。每张输入及重编码结果均不超过5MiB，尺寸单边<=6000、总像素<=2000万。作为静态图片处理，重新绘制像素并写出JPEG或透明PNG，不复制EXIF/GPS/XMP/文本元数据或原文件名；不是原文件无损保留服务。解码不支持/损坏/格式或尺寸不合法400。

上传后仅STAGED。保存完整草稿时带imageIds绑定到新修订，同一图片可在同一草稿后续修订复用，不跨草稿复用。省略imageIds继承上一版，[]表示新版本不含图片，旧修订仍保留，不能取消已BOUND文件。PUT仍需version及完整D表单，不能只发图片就假定其他资料会保留。

```js
// 复用现有orders.js的http（baseURL=/api），不要重复拼/api
const form = new FormData(); form.append('file', selectedFile)
const uploaded = (await http.post('/v2/images', form)).data
// editableForm只包含D文档允许字段；sourceAddressId需映射成addressId。
await http.put(`/v2/drafts/${draftId}`, { ...editableForm, version, imageIds })
const blob = (await http.get(uploaded.contentUrl.replace(/^\/api/, ''), { responseType: 'blob' })).data
const previewUrl = URL.createObjectURL(blob) // 卸载/替换时revokeObjectURL
```

不要手动固定multipart Content-Type（浏览器需加boundary）；img直接src访问受保护URL不会自动带Authorization，应先授权fetch/axios获取Blob。上传组件选图前预检5MiB/6张，服务端仍独立验证。保存409需刷新version，失败不要显示“正式报修成功”。

400格式/限额；401重新登录；403无功能权限；404非本人/取消/不存在；409文件已绑定其他草稿或不可取消/版本冲突。返回不含storageKey、私有目录及哈希。

开发存储默认在后端工作目录的.private-images（Git忽略、非静态资源），测试使用用户临时目录，两者均按V2数据库名隔离；可用V2_PRIVATE_IMAGE_DIR指定Git仓库和静态资源目录之外的本地私有基目录。不要配置为网站static/public。原始上传仅短暂用于解码，持久存储只有重编码结果。

简单补偿：确定DB回滚删新文件；提交结果未知保留文件，之后按DB匹配清理；取消/清理先提交元数据再删文件，删除失败下次重试。未绑定超过24小时转取消，清理取消文件及超过24小时的无DB记录生成文件；不删BOUND历史图、不删近期未跟踪文件、不递归清扫其他目录。维护显式触发，无定时平台；不承诺磁盘灾难恢复。

WebP使用轻量ImageIO插件，官方来源：[TwelveMonkeys ImageIO](https://github.com/haraldk/TwelveMonkeys) 。本轮没有图片公开URL、对象存储、维修结果图片或正式工单附件业务。
