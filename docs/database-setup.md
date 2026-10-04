# Railway 数据库 + Render 后端

后端留在 Render，运行记录存到专用 Railway PostgreSQL。不要复用其他项目的业务表。创建前确认 Railway 当前计划和新增资源费用。

## Railway

1. 登录现有 Railway 工作区，新建 Aurora Finder 项目并添加 PostgreSQL。
2. 在 PostgreSQL 的 Variables 或 Connect 中找公网连接参数。Render 无法使用 Railway 内网的 `.railway.internal` 地址。
3. 使用公网代理的 Host 和 Port，以及 PGDATABASE、PGUSER、PGPASSWORD。不要把密码发到聊天或提交到 Git。

## Render Environment

| Key | Value |
| --- | --- |
| APP_RUN_RECORD_ENABLED | true |
| APP_RUN_RECORD_JDBC_URL | jdbc:postgresql://公网Host:公网Port/数据库名?sslmode=require&connectTimeout=15&socketTimeout=30 |
| APP_RUN_RECORD_USERNAME | PGUSER |
| APP_RUN_RECORD_PASSWORD | PGPASSWORD |
| APP_RUN_RECORD_RETENTION_DAYS | 7 |

一起保存并重新部署。后端自动执行 Flyway 表迁移。URL 不包含用户名和密码，数据库变量只放在 Render。

## 线上验收

1. 健康接口返回 UP。
2. 查询 `/api/v1/facts/2964574`，记录返回的 runId。
3. 在 Railway 数据库查询该 ID：

```sql
SELECT run_id, kind, result_status, created_at_utc
FROM evaluation_run
ORDER BY created_at_utc DESC LIMIT 10;
```

4. 发起一条 AI 查询，检查对应 runId 和 assistant_tool_call。不要保存聊天全文。
5. 重新部署 Render 后，查询同一个 runId，确认数据保留。
6. 保留期清理只使用隔离测试数据验收，不修改真实记录时间或删除真实记录来测试。

## 独立 PostgreSQL 回归测试

在专用测试库配置 TEST_POSTGRES_URL、TEST_POSTGRES_USER、TEST_POSTGRES_PASSWORD 后，在 backend 运行：

```sh
./mvnw -Dtest=JdbcRunRecordStoreTest test
```

测试自动创建随机 schema，并在结束后删除这些测试 schema。不要对无建表权限的业务账号运行。未设置 TEST_POSTGRES_URL 时使用 H2，不能声称已验证 PostgreSQL。
