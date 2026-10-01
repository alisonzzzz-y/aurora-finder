# 手动接入数据库

Aurora Finder 的后端仍部署在 Render。数据库可以单独部署，不需要搬迁后端。以下以 Neon PostgreSQL 免费方案为例；创建时确认选择 Free，不启用付费升级。

## 1. 创建数据库

1. 打开 https://console.neon.tech ，注册或登录。
2. 新建项目，名称填 `aurora-finder`。
3. 选择 PostgreSQL 17；若有 Frankfurt 区域可选，优先选它以靠近 Render 后端。
4. 数据库名可保留 `neondb`，其余名称使用控制台默认值。
5. 打开 Connect，查看数据库的 Host、Database、Role 和 Password。迁移使用直接连接，关闭 Pooling。

无需手动创建表，后端启动时会自动迁移。密码不要发到聊天、GitHub 或前端配置。

## 2. 配置 Render 后端

打开 Render → `aurora-finder` → Environment，新增：

| Key | Value |
| --- | --- |
| APP_RUN_RECORD_ENABLED | true |
| APP_RUN_RECORD_JDBC_URL | jdbc:postgresql://你的Host:5432/你的Database?sslmode=require&connectTimeout=15&socketTimeout=30 |
| APP_RUN_RECORD_USERNAME | Neon 的 Role |
| APP_RUN_RECORD_PASSWORD | Neon 的 Password |

URL 里不放用户名或密码，也不要原样粘贴 `postgresql://用户名:密码@...`。四项确认无误后一起保存并重新部署。

不要修改现有 OPENAI、CORS 或天气来源配置。这些数据库变量只填在 Render 后端，不能填到 Vercel 前端。

## 3. 完成后检查

- Render Deploy 显示成功，Logs 中能看到 Flyway 已完成迁移。
- `/actuator/health` 返回 UP。
- 打开后端 `/api/v1/facts/2964574`，返回非空 runId。
- 在 Neon SQL Editor 中运行：

```sql
SELECT run_id, kind, result_status, created_at_utc
FROM evaluation_run
ORDER BY created_at_utc DESC
LIMIT 10;
```

若此处能读到刚才查询的 ID，说明写入成功。后续还需验证 AI 工具记录、后端重新部署后读回和保留期清理。完成设置后告诉 Codex“数据库已配置”，由 Codex 继续验收。

## 4. GitHub CI 权限

如果推送 CI 配置提示缺少 workflow scope，在 Mac Terminal 中执行：

```bash
gh auth refresh --hostname github.com --scopes workflow
```

按提示完成授权。该权限用于更新仓库的自动测试配置；不会修改数据库权限。完成后告诉 Codex“workflow 权限已补”，由 Codex 推送 CI 配置并验证 PostgreSQL。

参考：Neon 连接文档 https://neon.com/docs/connect/connect-from-any-app 。免费方案的额度以创建时控制台为准。
