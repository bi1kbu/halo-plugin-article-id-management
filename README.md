# article-id-management

Halo 2 插件：文章编号台账与分配管理（SOP 编号规则）。

## 1. 目标

1. 在插件后台先注册编号，形成“可分配编号池”。
2. 创建/编辑文章时，只能选择“未使用编号”进行绑定。
3. 建立可审计、可筛选、不可回收复用的编号台账。
4. 支持按权限分层操作：完全管理、修改、创建、访问。

## 2. 编号规则（V1）

默认格式：

`MS-{dept}/{type}-{serial[.sub]}/{year}[.RevN]`

示例：

- `MS-A/Res-0001/2025`
- `MS-A/Res-0002/2025.Rev1`
- `MS-A/SR-0003.1/2025`
- `MS-P/Res-0001.105/2026`

规则口径：

1. 流水号按 `部门 + 年份` 递增，每自然年重置。
2. `sub`（子文件号）可选，仅在打包/文件夹场景使用。
3. `Rev`（修订号）可选，且允许手动指定。
4. 文章删除或改回草稿后，已绑定编号不回收复用。

## 3. 存储策略

采用“台账主存 + 文章 annotations 回写”。

1. 台账表是业务真相（状态、绑定关系、审计信息）。
2. 文章 `metadata.annotations` 回写编号，便于检索/主题展示。

建议 annotation 键：

- `run.halo.article-id-management/code`
- `run.halo.article-id-management/ledger-id`

## 4. 权限模型

1. `view`：查看编号与筛选查询。
2. `create`：创建新编号、创建修订编号。
3. `modify`：在原有基础上修改编号信息。
4. `manage`：增删改查 + 规则配置 + 作废/解绑等完全管理。

继承关系：`manage > modify > create > view`

## 5. 核心流程

### 5.1 注册编号（插件后台页面）

1. 选择部门、类型、年份。
2. 输入/自动计算流水号，按需填写子文件号与 Rev。
3. 生成完整编号并入台账，状态为 `REGISTERED`。

### 5.2 文章绑定编号

1. 在文章创建/编辑时选择未使用编号。
2. 绑定成功后状态改为 `BOUND`。
3. 同步回写文章 annotations。

### 5.3 修订流程

1. 基于主号创建修订（Rev 可手动指定）。
2. 新修订记录入台账，未绑定前状态 `REGISTERED`，绑定后 `BOUND`。

## 6. 状态定义

- `REGISTERED`：已注册，未绑定文章。
- `BOUND`：已绑定文章，不可再分配。
- `SUPERSEDED`：被新修订替代（可选策略）。
- `VOID`：作废，不可使用。

## 7. 数据模型（V1）

### 7.1 规则配置（rule_config）

- `prefix`（默认 `MS`）
- `serial_width`（默认 `4`）
- `reset_per_year`（默认 `true`）
- `departments`（部门编码字典）
- `doc_types`（文件类型编码字典）
- `enabled`

### 7.2 编号台账（article_id_ledger）

- `id`（UUID）
- `full_code`（唯一）
- `prefix`
- `dept_code`
- `doc_type`
- `serial`
- `sub_serial`（可空）
- `year`
- `rev`（可空）
- `status`
- `article_name`（可空）
- `article_title_snapshot`（可空）
- `created_by/created_at`
- `updated_by/updated_at`
- `remark`（可空）

### 7.3 审计日志（ledger_audit_log）

- `ledger_id`
- `action`
- `operator`
- `before_json`
- `after_json`
- `created_at`

## 8. API 规划（V1）

### Console API（后台）

- `GET /apis/api.article-id-management.console/v1/rules`
- `PUT /apis/api.article-id-management.console/v1/rules`
- `POST /apis/api.article-id-management.console/v1/ledger/preview`
- `POST /apis/api.article-id-management.console/v1/ledger/register`
- `GET /apis/api.article-id-management.console/v1/ledger`
- `GET /apis/api.article-id-management.console/v1/ledger/{id}`
- `PATCH /apis/api.article-id-management.console/v1/ledger/{id}`
- `POST /apis/api.article-id-management.console/v1/ledger/{id}/bind`
- `POST /apis/api.article-id-management.console/v1/ledger/{id}/revise`
- `POST /apis/api.article-id-management.console/v1/ledger/{id}/void`

### Public API（可选）

- `GET /apis/api.article-id-management.halo.run/v1/articles/{articleName}/id`

## 9. 实施阶段

1. Phase 1：后端模型、规则引擎、Console API
2. Phase 2：后台 UI（规则配置、注册、台账）
3. Phase 3：文章绑定入口 + annotations 回写
4. Phase 4：权限、审计、并发一致性与测试
5. Phase 5：联调验收与发布

## 10. 验收标准

1. 编号可注册、可筛选、可绑定、可修订、可作废。
2. 并发注册不重复（`full_code` 唯一）。
3. 已绑定编号在文章删除/回草稿后不回收。
4. Rev 支持手动指定并通过校验。
5. 四级权限行为与预期一致。

## 11. 本地开发

```bash
# 启动 Halo 开发容器并加载插件
./gradlew.bat haloServer

# 构建插件
./gradlew.bat build
