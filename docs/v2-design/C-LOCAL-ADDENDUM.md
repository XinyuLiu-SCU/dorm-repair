# C阶段局部设计补充

依据2026-11-27人工确认。本补充不修改v2-design-r2标签。

空间唯一权威链campus→building→building_floor→room。尚未实际建表的campus文本、room.floor_no不创建冗余副本；floor_no由floor_id关联投影。楼栋code全局唯一；floor_no在楼栋内唯一；room_no在楼层内唯一，等价于原楼栋/楼层/房号组合。各层name在同父级唯一。房间kind PRIVATE/PUBLIC。公共设施可只绑定楼栋或楼层及public_zone，不强制房间。

repair_category仅为八根最多三级分类；repair_item为可选择故障，包含预计分钟、快捷标记及排序。同分类下项目名称唯一，项目不得与分类节点同名。根固定不可移动、不可新增第九根；分类结构更改通过数据库统一guard串行，避免并发交叉移动。停用不改写子孙enabled，effectiveEnabled为全部祖先及本项enabled交集，普通选择接口只返回有效路径。

facility_type是类型，facility是资产。facility_type_repair_item决定允许项目。原facility.category_id未落表，不创建第二权威来源。位置只写最深location外键组合，通过复合外键校验一致性。所有实体停用替代删除。新增权限按实际能力读写分离。V4曾把新增管理权限授予全权用户持有的全部启用角色，存在共享角色扩散风险，属于已废弃的历史机制。最终V6仅规范可核验的空用户种子模板；非空库必须有可信来源及明确完整授权映射，否则在授权写入前拒绝，不猜测保管人，也不批量盲目撤权。空库ADMIN模板保持全部有效权限，STUDENT/REPAIRER不得具有目录管理写权限。详见../v2-development/V6-CONTROLLED-MIGRATION.md。

二维码内容是随机不可猜测的设施定位URL，数据库存哈希和服务端生成的稳定定位码；不是认证凭据。解析仍要求登录及facility:read，普通分页、total和二维码解析只可见有效公共设施；真实room.kind=PRIVATE时默认不可见，解析与不存在统一404，不能仅隐藏房号代替数据范围。显式facility:write仅通过管理接口访问私人资产，角色名称和持有码均不授予授权。二维码图片由ZXing生成，并用ZXing解码测试，不提交V2工单。

验收新增：层级/环/并发移动，祖先停用，复合外键，授权覆盖迁移与Token撤销，二维码解码/最小投影，乐观版本/审计同事务。无地址簿、附件、预约及V2流程。
