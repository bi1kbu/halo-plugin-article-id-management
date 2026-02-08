# article-id-management

Halo 2 插件：文件编号台账管理、编号规则配置、绑定关系维护、操作审计与主题联动展示。

## 功能概览

1. 编号规则管理
- 支持前缀、流水号位数、是否按年份重置、编号显示规则（占位符）配置。
- 支持部门编码与文件类型字典配置（`code + label`）。

2. 编号注册与台账
- 先注册编号，再绑定内容。
- 支持 `serial/sub/rev` 组合生成编号，`Rev` 可手动指定。
- 台账支持筛选、分页、状态管理、依赖/替代关系维护。

3. 绑定信息管理
- 支持绑定对象名称、标题、链接、发布日期快照。
- 支持保存时自动刷新绑定信息快照（标题/链接/发布日期）。
- 已绑定对象不可重复被其他编号占用。

4. 状态与审计
- 状态：`REGISTERED`、`BOUND`、`SUPERSEDED`、`VOID`、`DELETED`。
- 显示态支持动态拆分：`BOUND_PENDING`（即将生效）、`BOUND_EFFECTIVE`（现行有效）。
- 全量操作日志（操作人、时间、摘要、字段变更）。

5. 权限模型
- 四级权限模板：查看、创建、修改、完全管理。
- 前端菜单按权限显示。

6. 主题联动
- Post 场景：回写 `metadata.annotations`。
- Docs/非 Post 场景：提供按 URL 查询的公开接口，主题可前端拉取渲染。

## 编号规则

默认规则表达式（可配置）：

`{prefix}-{dept}/{type}-{serial}{subPart}/{year}{revPart}`

常见示例：
- `MS-A/Res-0001/2026`
- `MS-A/Res-0002/2026.Rev1`
- `MS-A/SR-0003.1/2026`

占位符支持中英双套：
- 英文：`{prefix} {dept} {type} {serial} {sub} {year} {rev} {subPart} {revPart}`
- 中文：`{前缀} {部门编码} {文件类型} {流水号} {子文件号} {年份} {修订号} {子文件片段} {修订片段}`

## 注解键约定（Post 场景）

插件维护以下注解键：
- `article-id-management/fullCode`
- `article-id-management/status`
- `article-id-management/ledgerId`
- `article-id-management/effectiveDate`
- `article-id-management/supersededDate`
- `article-id-management/voidDate`
- `article-id-management/issuingAuthority`
- `article-id-management/issuingAgency`
- `fileNumber`

## API

### Console API

Base: `/apis/api.article-id-management.console/v1`

- `GET /rules` 获取规则
- `PUT /rules` 更新规则
- `POST /ledger/preview` 预览编号
- `POST /ledger/register` 注册编号
- `GET /ledger` 台账列表
- `PATCH /ledger/{id}` 修改台账条目
- `POST /ledger/{id}/mark-delete` 标记删除
- `GET /logs` 操作日志

### Public API（主题联动）

Base: `/apis/api.article-id-management.halo.run/v1alpha1`

- `GET /health` 健康检查
- `GET /lookup?link=/docs/manual/num` 按页面链接查询编号信息

返回字段（节选）：
- `fullCode`
- `status` / `statusDisplayKey` / `statusDisplay`
- `effectiveDate` / `supersededDate` / `voidDate`
- `issuingAuthority`
- `deptCode` / `docType`
- `articleTitle` / `articleLink` / `articlePublishedDate`

## 主题适配方法

详细说明见：`THEME_PLUGIN_ADAPTER.md`

推荐两种接入方式：

1. Post 页面（注解直读）
- 在模板中读取 `post.metadata.annotations['article-id-management/fullCode']` 等字段。

2. Docs/非 Post 页面（公开接口）
- 用当前路径调用：
  `GET /apis/api.article-id-management.halo.run/v1alpha1/lookup?link=${window.location.pathname}`
- 命中后渲染编号信息；未命中保持隐藏。

## 权限模板

文件：`src/main/resources/extensions/article-id-role-templates.yaml`

已提供 4 套角色模板：
- `article-id-management-role-view`
- `article-id-management-role-create`
- `article-id-management-role-modify`
- `article-id-management-role-manage`

对应 UI 权限键：
- `plugin:article-id-management:view`
- `plugin:article-id-management:create`
- `plugin:article-id-management:modify`
- `plugin:article-id-management:manage`

## 本地开发

1. 开发运行（推荐）
```bash
./gradlew.bat haloServer
```

2. 构建插件
```bash
./gradlew.bat build
```

3. 构建产物
- `build/libs/plugin-article-id-management-<version>.jar`

## 发布/联调建议

1. 每次打包前递增版本号（例如 `+0.0.1`）。
2. 上传新 JAR 到 Halo 后台插件页完成升级。
3. 刷新控制台并验证：
- 插件版本变化
- 菜单与权限显示
- `lookup` 接口可用
- 主题展示生效
