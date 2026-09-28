# Phase 10 Verification Record

Updated: 2026-09-28

## English

### Automated evidence

| Scenario | Verification | Result and scope |
| --- | --- | --- |
| Weather provider timeout | `MetNoWeatherProviderTest.classifiesAnActualHttpRequestTimeout` delays a local HTTP response beyond the configured request timeout. | The provider reports `TIMEOUT`. This is a local integration test, not an outage test against MET Norway. |
| Partial source outage | `ObservationFactsServiceTest` injects a weather timeout while NOAA activity is current. | The response remains `PARTIAL`; aurora and solar darkness remain available, while cloud data is unavailable. |
| Expired aurora data | `AuroraMapServiceTest.expiresOvationAtForecastTimeAndSuppressesTheOldGrid`. | Expired grid values are suppressed and the local activity level becomes insufficient data. |
| Missing cloud values | `MetNoWeatherProviderTest` parses missing cloud fields; `ObservationFactsServiceTest` checks empty forecast coverage. | Missing values remain missing and are not converted to clear skies. |
| AI unavailable and rate limited | `AssistantControllerTest` exercises the safe `503` response and the `429` application limit. | The client receives a stable error shape; rate-limited requests do not call the assistant. This does not simulate an OpenAI account outage. |
| Request cost controls | `AssistantRequestLimiterTest`, `AssistantServiceTest`, and `OpenAiAssistantProviderTest` cover request limits, bounded tool interaction, and returned token usage. | Per-call controls and token reporting are tested. The app does not calculate dollar spend or provide an aggregate usage dashboard. |
| Database failure | No database or Repository is part of the current MVP. | Not applicable to the current runtime; database outage behavior has not been tested. |

The backend `./mvnw verify` run completed with 78 tests, 0 failures, 0 errors, and 0 skipped tests on 2026-09-28. Production browser checks recorded elsewhere confirm the normal user path; they do not demonstrate production fault injection.

### Operational limits still to verify

- Review actual OpenAI usage and spend in the provider account. Application logs report model, duration, token counts, and request ID for successful responses, but do not replace the provider billing page.
- Confirm MapTiler plan, quota, and public portfolio usage permission in the account.
- Repeat the production end-to-end flow and inspect Render logs during the same session. External provider outages have not been deliberately triggered in production.
- The current flow ends at read-only tool results. Persistent tool traces and decision records depend on the separate storage design and are not part of the deployed flow yet.

## 简体中文

### 自动化验证证据

| 场景 | 验证方式 | 结果与范围 |
| --- | --- | --- |
| 天气服务超时 | `MetNoWeatherProviderTest.classifiesAnActualHttpRequestTimeout` 让本地 HTTP 测试服务延迟返回，超过配置的请求时限。 | Provider 正确返回 `TIMEOUT`。这是本地集成测试，不代表对 MET Norway 线上服务进行过故障测试。 |
| 单一数据源故障 | `ObservationFactsServiceTest` 模拟天气请求超时，同时让 NOAA 极光数据保持有效。 | 总体状态为 `PARTIAL`；极光活动和太阳黑暗时段仍可用，云量数据标记为不可用。 |
| 极光数据过期 | `AuroraMapServiceTest.expiresOvationAtForecastTimeAndSuppressesTheOldGrid`。 | 过期网格值会被隐藏，当地活动等级返回“数据不足”。 |
| 云量缺失 | `MetNoWeatherProviderTest` 验证云量字段缺失；`ObservationFactsServiceTest` 验证预报没有覆盖数据。 | 缺失值保持缺失，不会被转换成晴空。 |
| AI 服务不可用与限流 | `AssistantControllerTest` 验证安全的 `503` 响应和应用侧 `429` 限流。 | 前端得到稳定的错误格式；被限流的请求不会调用 AI。此测试没有模拟 OpenAI 账户或服务整体故障。 |
| 请求成本控制 | `AssistantRequestLimiterTest`、`AssistantServiceTest` 和 `OpenAiAssistantProviderTest` 覆盖请求频率限制、工具交互上限和接口返回的 token 用量。 | 单次调用限制和 token 记录已测试。应用不会计算美元费用，也没有汇总用量面板。 |
| 数据库故障 | 当前 MVP 没有数据库或 Repository。 | 现阶段不适用；尚未测试数据库故障行为。 |

2026-09-28 的后端 `./mvnw verify` 全量检查通过：78 项测试，失败 0、错误 0、跳过 0。其他记录中的生产浏览器测试验证了正常使用流程，但不代表在生产环境注入过故障。

### 仍需进行的运行环境核验

- 在 OpenAI 账户中查看实际用量和费用。应用日志会记录成功响应的模型、耗时、token 数和请求 ID，但不能代替服务商账单页面。
- 在 MapTiler 账户确认套餐、配额及公开作品集使用许可。
- 再跑一次生产环境完整流程，并在同一时段检查 Render 日志。目前没有在生产环境中主动触发外部服务故障。
- 当前流程提供只读工具结果。持久化工具调用轨迹和判定记录依赖单独的数据存储设计，尚未接入已部署流程。
