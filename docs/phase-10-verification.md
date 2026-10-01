# Phase 10 Verification Record

Updated: 2026-09-30

Update / 更新 2026-10-01: the database and AI statements below describe the earlier deployed build. A PostgreSQL run-record implementation is now in the working branch. Local migration, restart readback, retention, and database-failure tests pass; production storage is not enabled until Render is connected to PostgreSQL. Five live AI scenarios with six requests passed manual fact checks on the previously deployed build. The current rule-validation conclusion is in [observation-rule-validation-gate.md](observation-rule-validation-gate.md).

更新：下文数据库和 AI 的描述是当时已部署版本的历史记录。目前工作分支已实现 PostgreSQL 运行记录，本地迁移、重启读回、保留期和数据库故障测试通过；Render 接入 PostgreSQL 前，线上尚未启用持久化。已部署旧版本的五类线上 AI 场景共六次请求通过人工事实核对。当前综合规则验证结论见 [观测规则验证结论](observation-rule-validation-gate.md)。

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

The existing backend test `AssistantServiceTest.resolvesExactFullLocationLabelFromAmbiguousResults` passed (1 test, 0 failures/errors). It verifies that selecting the exact Dublin, Ireland candidate from prior assistant choices retrieves that candidate's local-night facts. The first restricted run could not attach Mockito's agent; the same test passed on rerun with test-agent permission. This covers the selection-to-data path in an automated test; the successful production second-turn interaction is recorded separately below.

On 2026-09-30, a production browser follow-up selected the Dublin City candidate and the assistant rendered a local cloud forecast, confirming that the second-turn selection flow completes in production. The answer cited MET Norway and local time, but contained a self-correction that changed the stated 10:00 value and repeated 11:00, with a stray question mark. This is a response-quality concern, not evidence that the provider data itself is wrong: the hourly values were not independently compared with the selected-location forecast in this session. Record this as a remaining data-to-answer consistency check rather than treating the AI flow as unavailable. The earlier browser automation timeout was caused by checking that historical candidate buttons disappeared; those buttons correctly remain in chat history. A later browser interaction showed the final response.

To prevent that observed failure from reaching users unchanged, the backend now detects a final answer that uses correction language and assigns different cloud percentages to the same local hour. It substitutes a short message directing users to the local cloud chart. The model instructions also ask it to check for conflicting hourly values before finishing. `AssistantServiceTest` covers both conflicting values and a correction that repeats the same value. The full backend `./mvnw --batch-mode verify` run passed 87 tests with no failures, errors, or skips on 2026-09-30. This guard targets contradictory corrections; it does not independently prove every generated value matches the source forecast.

The assistant now also compares final hourly cloud percentages with the selected location's structured weather response. Local hours are derived from the location's IANA time zone; rounded whole percentages allow a 0.6-point tolerance, while decimal values allow 0.15 points. If an answer gives a cloud percentage for an hour that is absent from the returned forecast or does not match any returned value for that local hour, it is replaced with a message pointing to the chart. If the forecast was requested but contains no usable cloud points, time-and-percentage claims about cloud cover are also rejected. The check only applies when hourly time-and-percentage pairs can be extracted from an answer that mentions clouds. Because answers may omit the date, values are matched by local clock hour across the returned forecast window; this confirms a value is present for that hour but does not prove which date the model intended. `AssistantServiceTest` covers a mismatched value, matching decimal values, and a forecast with no cloud points. The full backend verification passed 90 tests with no failures, errors, or skips on 2026-09-30.

On 2026-09-30, the repository verification also passed locally: backend `./mvnw --batch-mode verify` (85 tests, no failures/errors/skips), all 19 Python script tests, frontend lint, all 11 chart regression tests, and the production frontend build. The first backend attempt could not attach Mockito's test agent under the restricted process sandbox; rerunning with test-agent permissions passed. The frontend build reports the existing large MapLibre map chunk warning.

### Operational limits still to verify

- Review actual OpenAI usage and spend in the provider account. Application logs report model, duration, token counts, and request ID for successful responses, but do not replace the provider billing page.
- Confirm MapTiler plan, quota, and public portfolio usage permission in the account.
- The production API smoke check, normal browser flow, and the complete production AI location-selection flow have passed. Source-backed hourly cloud validation is now covered by automated tests but has not yet been exercised against a live AI response after deployment. Render logs still need review during a browser session. External provider outages have not been deliberately triggered in production.
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

后端既有测试 `AssistantServiceTest.resolvesExactFullLocationLabelFromAmbiguousResults` 已通过（1 项测试，失败/错误为 0），验证用户从先前的候选项中选择爱尔兰 Dublin 后，会查询该地点的当地夜间事实。第一次受限运行无法附加 Mockito 测试代理；在允许测试代理后重跑通过。自动化测试覆盖了选项到数据查询的路径；生产页面第二轮交互结果另见下文。

2026-09-30 在生产页面选择 Dublin City 后，AI 成功显示了当地云量预报，确认第二轮地点选择流程可以在线上完成。回复注明 MET Norway 和当地时间，但对 10:00 的数值进行了自我更正，前后不一致，并在 11:00 后留下问号。本次没有把这些小时值与同一地点的预报逐项独立比对，因此这是回答质量和数据一致性待核查项，不能据此判断天气来源数据本身错误。此前浏览器自动化超时，是因为测试错误地要求聊天历史里的候选按钮消失；这些按钮本来就会保留。之后的浏览器交互已显示最终回复。

为避免这类已观察到的矛盾直接显示给用户，后端现会检查最终回复：如果回复使用了更正措辞，且同一当地小时出现不同云量数值，就替换为提示用户查看当地云量图的简短信息。模型指令也补充了检查小时数是否冲突的要求。`AssistantServiceTest` 覆盖了矛盾数值和更正后数值相同两种情况。2026-09-30 后端 `./mvnw --batch-mode verify` 全量检查通过：87 项测试，失败、错误和跳过均为 0。此保护针对自我更正造成的冲突，不能独立证明每个生成数值都与来源预报一致。

助手现在还会把最终回复中的逐小时云量与所选地点的结构化天气数据进行比对。系统按地点的 IANA 时区换算当地小时；整数百分比允许 0.6 个百分点的四舍五入误差，小数值允许 0.15 个百分点。如果回复所称小时不在天气数据中，或该小时的数值与返回数据均不匹配，就替换为提示查看图表的信息。天气查询已执行但没有有效云量点时，带有小时和百分比的云量说法也会被拦截。只有回答提到云量并且能提取出“小时 + 百分比”时才会执行该校验。由于回答可能省略日期，校验会在天气预报范围内按当地钟点匹配；这只能确认数值出现在该钟点，无法证明模型指的是哪一天。`AssistantServiceTest` 覆盖了数值不匹配、小数值匹配和来源没有云量点三种情况。2026-09-30 后端全量验证通过：90 项测试，失败、错误和跳过均为 0。

2026-09-30 的仓库检查也全部通过：后端 `./mvnw --batch-mode verify`（85 项测试，失败/错误/跳过均为 0）、Python 脚本测试 19 项、前端 lint、图表回归测试 11 项及正式前端构建。后端第一次运行时，受限进程沙箱阻止 Mockito 附加测试代理；在允许测试代理后重跑通过。前端构建仍提示 MapLibre 地图代码块较大。

### 仍需进行的运行环境核验

- 在 OpenAI 账户中查看实际用量和费用。应用日志会记录成功响应的模型、耗时、token 数和请求 ID，但不能代替服务商账单页面。
- 在 MapTiler 账户确认套餐、配额及公开作品集使用许可。
- 生产只读 API 检查、常规浏览器流程以及 AI 地点选择到最终天气回答的完整生产流程均已通过。逐小时云量来源校验已通过自动化测试，但新版本部署后尚未用线上 AI 回复验证。还需在同一时段查看 Render 日志。目前没有在生产环境中主动触发外部服务故障。
- 当前流程提供只读工具结果。持久化工具调用轨迹和判定记录依赖单独的数据存储设计，尚未接入已部署流程。

## 2026-10-01 独立浏览器与线上复查

- 使用独立 Playwright 会话，不操作用户主浏览器；正式主页地图正常加载。
- 6 项主要 API 检查全部 HTTP 200，整体通过。
- 聊天窗口打开、关闭与中文切换通过；浏览器控制台没有错误或警告。
- 输入 Dublin 自动展开候选；点击爱尔兰候选后直接按 Enter，无需再次点击输入框；预报留在当前页面。
- 线上 AI 合成验收 5 组场景、6 次请求全部 HTTP 200。地点只需确认一次；Apia 当地日期为 10 月 1、2、3 日，UTC+13；超出预报日期、个人观测概率和历史出现频率不编造数据。
- 人工核对 Dublin 回答中的 20:00、23:00、次日 04:00、07:00 云量为 87.5%、68%、100%、91.4%，与对应接口时间和值一致。
- 最新修复 PR 的后端 CI、前端 CI、Vercel 预览全部通过。合并到 main 被自动审批拒绝，理由为缺少明确合并授权；尚未合并，不代表新运行记录功能已上线。
- PostgreSQL 接入仍待用户选择；新建可能收费。综合观测规则的独立验证证据仍不足，继续弃权。此次检查不能替代持久化上线验收或模型大样本可靠性评估。
