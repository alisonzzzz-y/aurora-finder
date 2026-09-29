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

The read-only production smoke check was expanded to call the standalone Dublin weather forecast and geomagnetic warnings endpoints as well as the existing six checks. On 2026-09-29 16:26 UTC, all eight checks returned HTTP 200. The weather response contained 19 cloud points with values and the expected source timestamps; the warnings response contained three storm-watch days and zero active warnings. The first run exposed coordinate rounding in the weather API response (coordinates are rounded to four decimal places); the smoke validator now allows that documented precision while still checking it is the selected Dublin location. The updated 13 smoke-script unit tests passed. No AI request was made during this check.

On 2026-09-29, a read-only Playwright browser check covered the deployed Vercel page at desktop size. The map rendered, Dublin autocomplete returned distinct Irish and U.S. candidates, and selecting Dublin, Ireland loaded its local outlook and weather timeline. The current strong-activity list selected a point and marked it as selected; the page also switched to Simplified Chinese. No page errors or failed browser requests were observed. The AI was not called, and Render logs were not inspected during this browser session.

At 2026-09-29 17:58 UTC, one production assistant question was sent from the browser: “What cloud cover is forecast for Dublin, Ireland tonight?” The chat endpoint returned HTTP 200 with no browser runtime errors. Because the name still matched four Irish places (Dublin City, Dublin South, Dublin Pike, and Dublin Airport), the assistant offered those specific choices before running the weather lookup. This verifies the one-step disambiguation response, not the final answer after a choice; no second model request was sent in this check.

The existing backend test `AssistantServiceTest.resolvesExactFullLocationLabelFromAmbiguousResults` passed (1 test, 0 failures/errors). It verifies that selecting the exact Dublin, Ireland candidate from prior assistant choices retrieves that candidate's local-night facts. The first restricted run could not attach Mockito's agent; the same test passed on rerun with test-agent permission. This covers the selection-to-data path in an automated test, while the corresponding second-turn production browser response remains unverified.

On 2026-09-30, the repository verification also passed locally: backend `./mvnw --batch-mode verify` (85 tests, no failures/errors/skips), all 19 Python script tests, frontend lint, all 11 chart regression tests, and the production frontend build. The first backend attempt could not attach Mockito's test agent under the restricted process sandbox; rerunning with test-agent permissions passed. The frontend build reports the existing large MapLibre map chunk warning.

### Operational limits still to verify

- Review actual OpenAI usage and spend in the provider account. Application logs report model, duration, token counts, and request ID for successful responses, but do not replace the provider billing page.
- Confirm MapTiler plan, quota, and public portfolio usage permission in the account.
- The production API smoke check, normal browser flow, and one production AI disambiguation response passed on 2026-09-29. The final weather answer after selecting a candidate still needs a browser check. Render logs also need review during a browser session. External provider outages have not been deliberately triggered in production.
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

随后扩展了生产只读烟雾检查，新增单独的 Dublin 云量预报接口和地磁预警接口，现共检查八个接口。2026-09-29 16:26 UTC 的检查全部返回 HTTP 200。云量接口返回 19 个带数值的时段及来源时间；地磁预警接口返回未来三天的风暴观察数据，当前活动预警数量为 0。首次检查发现天气接口坐标保留四位小数，导致严格相等校验误报；现已按返回精度校验仍对应 Dublin。更新后的烟雾脚本 13 项离线测试通过。本次检查没有调用 AI。

2026-09-29 又对已部署的 Vercel 页面进行了只读 Playwright 浏览器检查。地图正常渲染；Dublin 自动补全区分了爱尔兰和美国的同名地点；选择爱尔兰 Dublin 后，当地预报与云量时间图正常显示。点击当前活动较强地点后，条目显示为选中状态；中英文切换正常。未观察到页面运行错误或失败的浏览器请求。本次没有调用 AI，也没有在浏览器检查的同时查看 Render 日志。

2026-09-29 17:58 UTC 从生产页面发送了一条 AI 问题：“What cloud cover is forecast for Dublin, Ireland tonight?” 聊天接口返回 HTTP 200，浏览器没有运行错误。由于爱尔兰境内仍有 Dublin City、Dublin South、Dublin Pike 和 Dublin Airport 四个候选地点，助手先列出这些具体选项供用户确认，再继续天气查询。这验证了单轮地点消歧响应，不代表选中地点后的最终天气回答已通过浏览器验收；本次没有发送第二条模型请求。

后端既有测试 `AssistantServiceTest.resolvesExactFullLocationLabelFromAmbiguousResults` 已通过（1 项测试，失败/错误为 0），验证用户从先前的候选项中选择爱尔兰 Dublin 后，会查询该地点的当地夜间事实。第一次受限运行无法附加 Mockito 测试代理；在允许测试代理后重跑通过。自动化测试覆盖了选项到数据查询的路径，但生产页面第二轮回复仍未验证。

2026-09-30 的仓库检查也全部通过：后端 `./mvnw --batch-mode verify`（85 项测试，失败/错误/跳过均为 0）、Python 脚本测试 19 项、前端 lint、图表回归测试 11 项及正式前端构建。后端第一次运行时，受限进程沙箱阻止 Mockito 附加测试代理；在允许测试代理后重跑通过。前端构建仍提示 MapLibre 地图代码块较大。

### 仍需进行的运行环境核验

- 在 OpenAI 账户中查看实际用量和费用。应用日志会记录成功响应的模型、耗时、token 数和请求 ID，但不能代替服务商账单页面。
- 在 MapTiler 账户确认套餐、配额及公开作品集使用许可。
- 2026-09-29 的生产只读 API 检查、常规浏览器流程和一次 AI 地点消歧响应均已通过。仍需通过浏览器确认选择地点后的最终天气回答，并在同一时段查看 Render 日志。目前没有在生产环境中主动触发外部服务故障。
- 当前流程提供只读工具结果。持久化工具调用轨迹和判定记录依赖单独的数据存储设计，尚未接入已部署流程。
