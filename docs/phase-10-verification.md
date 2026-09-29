# Phase 10 Verification Record

Updated: 2026-09-30

## English

### Automated evidence

| Scenario | Verification | Result and scope |
| --- | --- | --- |
| Search, local facts, map, and AI tool flow | `ObservationWorkflowIntegrationTest` uses real Spring controllers, services, solar calculations, and tools, with fixed time and mocked external providers. | Same-name candidates remain distinct; three local nights and map data agree with the selected place; AI receives the same facts JSON as the page API. Weather timeout remains partial in both paths. Model responses are scripted, so this does not evaluate model reasoning or a production browser. |
| Weather provider timeout | `MetNoWeatherProviderTest.classifiesAnActualHttpRequestTimeout` delays a local HTTP response beyond the configured request timeout. | The provider reports `TIMEOUT`. This is a local integration test, not an outage test against MET Norway. |
| Partial source outage | `ObservationFactsServiceTest` injects a weather timeout while NOAA activity is current. | The response remains `PARTIAL`; aurora and solar darkness remain available, while cloud data is unavailable. |
| Expired aurora data | `AuroraMapServiceTest.expiresOvationAtForecastTimeAndSuppressesTheOldGrid`. | Expired grid values are suppressed and the local activity level becomes insufficient data. |
| Missing cloud values | `MetNoWeatherProviderTest` parses missing cloud fields; `ObservationFactsServiceTest` checks empty forecast coverage. | Missing values remain missing and are not converted to clear skies. |
| AI unavailable and rate limited | `OpenAiAssistantProviderTest` mocks upstream `429`, `503`, and malformed JSON; `AssistantControllerTest` exercises the safe `503` response and the `429` application limit. | Upstream errors map to safe application exceptions without exposing the upstream body; application-limited requests do not call the assistant. This does not simulate an OpenAI account outage. |
| Request cost controls | `AssistantRequestLimiterTest`, `AssistantServiceTest`, and `OpenAiAssistantProviderTest` cover request limits, bounded tool interaction, and returned token usage. | Per-call controls and token reporting are tested. The app does not calculate dollar spend or provide an aggregate usage dashboard. |
| Database failure | No database or Repository is part of the current MVP. | Not applicable to the current runtime; database outage behavior has not been tested. |

The backend `./mvnw verify` run completed with 82 tests, 0 failures, 0 errors, and 0 skipped tests on 2026-09-28. Production browser checks recorded elsewhere confirm the normal user path; they do not demonstrate production fault injection.

### Production read-only API check (2026-09-29)

`python3 scripts/smoke_production.py --timeout 60` passed against the deployed Render API at 2026-09-29 16:00 UTC. Health, Dublin search, selected Dublin facts, aurora map, Kp index, and geomagnetic storm forecast all returned HTTP 200. The Dublin result contained three local nights; aurora and cloud sources were current with an overlapping window. The response correctly retained `NOT_VALIDATED` for the overall viewing rule. The check did not call the AI or test browser rendering, provider failure injection, or long-term availability.

A separate production AI request on 2026-09-29 16:14 UTC asked what the latest Kp forecast means for aurora activity. The assistant returned HTTP 200 in 20.2 seconds, cited current NOAA forecast data, and stated that Kp is not a local viewing probability. This was one normal-path request and does not establish model accuracy across the evaluation matrix.

On 2026-09-30, the repository verification also passed locally: backend `./mvnw --batch-mode verify` (85 tests, no failures/errors/skips), all 19 Python script tests, frontend lint, all 11 chart regression tests, and the production frontend build. The first backend attempt could not attach Mockito's test agent under the restricted process sandbox; rerunning with test-agent permissions passed. The frontend build reports the existing large MapLibre map chunk warning.

### Operational limits still to verify

- Review actual OpenAI usage and spend in the provider account. Application logs report model, duration, token counts, and request ID for successful responses, but do not replace the provider billing page.
- Confirm MapTiler plan, quota, and public portfolio usage permission in the account.
- A production read-only API smoke check passed on 2026-09-29. Still repeat the full browser flow, including a real AI question, and inspect Render logs during the same session. External provider outages have not been deliberately triggered in production.
- The current flow ends at read-only tool results. Persistent tool traces and decision records depend on the separate storage design and are not part of the deployed flow yet.

## 简体中文

### 自动化验证证据

| 场景 | 验证方式 | 结果与范围 |
| --- | --- | --- |
| 搜索、当地事实、地图与 AI 工具流程 | `ObservationWorkflowIntegrationTest` 使用真实 Spring Controller、Service、太阳计算和工具，仅固定时间并模拟外部提供商。 | 同名地点保持区分，三晚日期和地图对应选中地点，AI 接收到的事实 JSON 与页面接口一致；天气超时在两条路径中均保持部分可用。模型响应由测试脚本提供，因此不代表模型推理评估或生产浏览器验收。 |
| 天气服务超时 | `MetNoWeatherProviderTest.classifiesAnActualHttpRequestTimeout` 让本地 HTTP 测试服务延迟返回，超过配置的请求时限。 | Provider 正确返回 `TIMEOUT`。这是本地集成测试，不代表对 MET Norway 线上服务进行过故障测试。 |
| 单一数据源故障 | `ObservationFactsServiceTest` 模拟天气请求超时，同时让 NOAA 极光数据保持有效。 | 总体状态为 `PARTIAL`；极光活动和太阳黑暗时段仍可用，云量数据标记为不可用。 |
| 极光数据过期 | `AuroraMapServiceTest.expiresOvationAtForecastTimeAndSuppressesTheOldGrid`。 | 过期网格值会被隐藏，当地活动等级返回“数据不足”。 |
| 云量缺失 | `MetNoWeatherProviderTest` 验证云量字段缺失；`ObservationFactsServiceTest` 验证预报没有覆盖数据。 | 缺失值保持缺失，不会被转换成晴空。 |
| AI 服务不可用与限流 | `OpenAiAssistantProviderTest` 模拟上游 `429`、`503` 和无效 JSON；`AssistantControllerTest` 验证安全的 `503` 响应和应用侧 `429` 限流。 | 上游错误映射为安全的应用异常，不会把上游响应正文透传给用户；应用侧限流时不会调用 AI。此测试没有模拟 OpenAI 账户或服务整体故障。 |
| 请求成本控制 | `AssistantRequestLimiterTest`、`AssistantServiceTest` 和 `OpenAiAssistantProviderTest` 覆盖请求频率限制、工具交互上限和接口返回的 token 用量。 | 单次调用限制和 token 记录已测试。应用不会计算美元费用，也没有汇总用量面板。 |
| 数据库故障 | 当前 MVP 没有数据库或 Repository。 | 现阶段不适用；尚未测试数据库故障行为。 |

2026-09-28 的后端 `./mvnw verify` 全量检查通过：82 项测试，失败 0、错误 0、跳过 0。其他记录中的生产浏览器测试验证了正常使用流程，但不代表在生产环境注入过故障。

### 生产只读 API 检查（2026-09-29）

`python3 scripts/smoke_production.py --timeout 60` 于 2026-09-29 16:00 UTC 对已部署的 Render API 检查通过。健康检查、Dublin 搜索、所选 Dublin 的当地事实、极光地图、Kp 指数和地磁风暴预报均返回 HTTP 200。Dublin 结果包含三晚数据，极光与云量来源为当前状态，且预报时间范围有重叠；综合观测规则仍正确标记为 `NOT_VALIDATED`。本检查没有调用 AI，也没有验证浏览器渲染、线上故障注入或长期可用性。

2026-09-29 16:14 UTC 另进行了一次线上 AI 请求，询问最新 Kp 预报对极光活动的含义。助手在 20.2 秒内返回 HTTP 200，引用了当前 NOAA 预报数据，并说明 Kp 不等于当地观测概率。这只是一次正常路径请求，不能证明模型已通过完整评估矩阵。

2026-09-30 的仓库检查也全部通过：后端 `./mvnw --batch-mode verify`（85 项测试，失败/错误/跳过均为 0）、Python 脚本测试 19 项、前端 lint、图表回归测试 11 项及正式前端构建。后端第一次运行时，受限进程沙箱阻止 Mockito 附加测试代理；在允许测试代理后重跑通过。前端构建仍提示 MapLibre 地图代码块较大。

### 仍需进行的运行环境核验

- 在 OpenAI 账户中查看实际用量和费用。应用日志会记录成功响应的模型、耗时、token 数和请求 ID，但不能代替服务商账单页面。
- 在 MapTiler 账户确认套餐、配额及公开作品集使用许可。
- 2026-09-29 的生产只读 API 检查已通过。仍需在浏览器中复验完整流程，包括真实 AI 提问，并在同一时段检查 Render 日志。目前没有在生产环境中主动触发外部服务故障。
- 当前流程提供只读工具结果。持久化工具调用轨迹和判定记录依赖单独的数据存储设计，尚未接入已部署流程。
