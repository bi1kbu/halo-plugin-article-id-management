# AGENTS

## 开发后固定流程

每次完成代码修改后，必须执行以下步骤：

1. 重新打包插件 JAR  
   - 在插件目录执行：`./gradlew.bat build`
   - 确认产物位于：`build/libs/plugin-article-id-management-*.jar`

2. 使用 Chrome MCP 更新 Halo 后台插件  
   - 打开 Halo 控制台插件管理页面
   - 上传最新打包的 JAR
   - 完成升级并确认版本已更新

3. 更新后验证  
   - 进入插件对应页面，确认本次改动已生效
   - 若未生效，先强制刷新页面再复测
