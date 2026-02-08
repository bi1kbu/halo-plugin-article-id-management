# 文章编号插件与主题联动适配说明

## 1. 目的
本文档用于指导在 Halo 2 中将 `plugin-article-id-management` 与主题模板联动，使前台/模板可直接显示文件编号、状态、日期等信息。

适用对象：
- 插件开发者
- 主题开发者
- 运维/实施人员

## 2. 推荐联动方案
推荐使用 **文章注解（`metadata.annotations`）作为联动桥梁**。

原因：
- 主题模板可直接读取，不依赖额外前端请求。
- 插件与主题解耦，主题只关心约定的键。
- 插件禁用时可平滑降级（模板判空即可）。

## 3. 注解键约定
建议统一使用以下键（命名空间固定为 `article-id-management`）：

| 键名 | 含义 | 示例 |
| --- | --- | --- |
| `article-id-management/fullCode` | 文件编号全文 | `MS-A/Res-0001/2026` |
| `article-id-management/status` | 编号状态 | `BOUND` |
| `article-id-management/ledgerId` | 台账记录 ID | `691f70c5-f6fd-457d-a503-43a1b3fabac1` |
| `article-id-management/effectiveDate` | 生效日期 | `2026-02-07` |
| `article-id-management/supersededDate` | 替代日期 | `2026-03-01` |
| `article-id-management/voidDate` | 作废日期 | `2026-04-01` |

状态建议中文映射：
- `REGISTERED` -> 已注册
- `BOUND` -> 已绑定
- `SUPERSEDED` -> 已替代
- `VOID` -> 已作废
- `DELETED` -> 已删除

## 4. 插件侧实现要求

### 4.1 同步时机
在以下场景同步文章注解：
- 注册编号并绑定文章（创建）
- 修改编号绑定信息（修改）
- 状态变更（替代、作废、删除标记等）

### 4.2 清理策略
当编号与文章解绑时，建议清理上述注解键；若需审计，可保留历史，但前台应以当前有效键为准。

### 4.3 最小实现步骤
1. 在服务层新增“文章注解同步器”组件（例如 `PostAnnotationSyncService`）。
2. 在 `register/update` 成功后调用同步器。
3. 同步失败时记录日志，且不影响编号台账主流程（避免主功能因外部失败阻断）。

## 5. 主题侧实现要求

### 5.1 插件可用性判断（必须）
模板中先判断插件是否可用，再渲染编号信息：

```html
<th:block th:if="${pluginFinder.available('plugin-article-id-management')}">
  <!-- 编号信息区域 -->
</th:block>
```

### 5.2 读取注解并显示
示例（可放在 `post.html`、`post-announcement.html`）：

```html
<th:block th:if="${pluginFinder.available('plugin-article-id-management')}">
  <div class="doc-id-box"
       th:if="${post.metadata != null and post.metadata.annotations != null and post.metadata.annotations['article-id-management/fullCode'] != null}">
    <span class="doc-id-label">文件编号</span>
    <span class="doc-id-value"
          th:text="${post.metadata.annotations['article-id-management/fullCode']}">-</span>

    <span class="doc-id-status"
          th:text="${post.metadata.annotations['article-id-management/status']}">-</span>
  </div>
</th:block>
```

### 5.3 主题样式建议
- 将编号显示为信息条或徽标，不要抢正文层级。
- 状态标签使用低饱和色，避免视觉噪音。
- 移动端下改为上下布局，避免挤压标题。

## 6. 当前插件现状说明（本仓库）
当前插件已经具备：
- 台账中保存文章绑定字段：`articleName/articleTitle/articleLink/articlePublishedDate`
- 创建/修改页面通过文章选择器选择并提交绑定信息

当前插件尚需补充：
- 将台账字段自动回写到 Halo 文章 `metadata.annotations`（如第 4 节所述）

说明：在未补充回写能力前，主题仍可通过插件 API 获取信息，但不满足“模板直接读取注解”的最佳实践。

## 7. 联调验证清单

### 7.1 插件侧
1. 在“编号创建”中选择一篇文章并注册编号。
2. 在“编号修改”中变更状态或日期字段并保存。
3. 确认日志中记录了对应操作。

### 7.2 主题侧
1. 打开文章详情页，检查是否显示“文件编号”。
2. 禁用插件后刷新页面，确认主题无报错且编号区域自动隐藏。
3. 切换移动端宽度，确认布局无溢出。

## 8. 常见问题

### 8.1 为什么主题里拿不到编号？
常见原因：
- 插件尚未实现注解回写。
- 文章未绑定编号。
- 模板未做 `pluginFinder.available(...)` 判定或键名写错。

### 8.2 注解键可以改名吗？
可以，但必须：
- 插件写入键名与主题读取键名保持一致。
- 升级时提供迁移策略，避免历史文章丢失显示。

## 9. 推荐后续演进
1. 提供“台账 -> 文章注解”一次性补偿任务，用于历史数据回填。
2. 在插件中提供只读公开 API，供主题做复杂展示（如替代链、依赖链）。
3. 增加自动化测试：注册、修改、解绑、状态变更对应注解同步断言。
