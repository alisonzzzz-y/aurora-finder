# AI assistant evaluation / AI 助手评估

Updated / 更新: 2026-09-28

## English

### Repeatable cases

`scripts/evaluate_assistant.py` checks the live application with synthetic questions. It saves the selected place, the page's facts response, and the assistant's answer for manual comparison. It does not need an OpenAI key or save real user conversations.

The default command lists the cases without sending requests:

```sh
python3 scripts/evaluate_assistant.py
```

To run the five cases against the deployed backend and save a local report:

```sh
python3 scripts/evaluate_assistant.py --live \
  --base-url https://aurora-observation-agent.onrender.com \
  --output /tmp/aurora-assistant-evaluation.json
```

Live mode can incur model costs. The runner makes at most six assistant requests per run. It retries idempotent location/facts GET requests up to three attempts after network errors or HTTP 502/503/504, but never retries assistant POST requests or HTTP 429. The application's limit of eight requests per client in ten minutes still applies across runs. Keep raw reports local; they are not uploaded automatically. Offline runner checks use:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts -p test_evaluate_assistant.py
```

### Live results

The run used `gpt-6-luna` on 2026-09-28, between approximately 14:35 and 14:38 UTC. There were six successful assistant requests across five scenarios. The first Apia setup was stopped before calling the model because the search returned both Apia city and Mount Apia. After verifying city ID `4035413`, that case was run once. This is a small live sample, not a reliability score.

| Scenario | Observed result | Review |
| --- | --- | --- |
| Dublin ambiguity, followed by an Irish candidate label | The first answer asked which country. The second returned Irish cloud data without another confirmation loop. | Location flow passed. The cloud range summary needs improvement, as described below. |
| Apia local dates | At 2026-09-28 14:38 UTC, the page's local nights were September 29, September 30, and October 1, with UTC+13. The assistant returned those exact dates and offset. | Passed in this run. |
| January 1, 2030 | The assistant said no forecast was available for that date and did not present current data as a 2030 forecast. | Passed in this run. |
| Personal viewing percentage | The assistant refused to invent a percentage, distinguished the model value from viewing probability, and mentioned unvalidated rules and the short forecast horizon. | Passed in this run. |
| Dublin and Cork recurrence | The assistant said there was no validated local recurrence dataset and gave no average interval. | Passed in this run. |

The Dublin cloud answer described the first part of the night as roughly 80%–100% cloud cover, but the page baseline included 65.6% at 19:00 local time. The 04:00–05:00 summary of about 20%–30% was consistent with the baseline. The first range was too broad a simplification. The assistant instructions now require numerical ranges to include all available lows and highs for the stated interval, or give exact hour/value examples. That prompt change still needs a fresh live check after deployment; this record does not claim it has fixed the model's behavior.

The backend verification still passes all 82 tests. Five offline runner tests also pass, covering the request cap, failed baseline setup, candidate metadata in a follow-up, and explicit city selection. These tests check application and runner behavior, not model reasoning.

### Remaining coverage

- Evaluate the real model with controlled polar-day, missing-cloud, expired-aurora, and tool-failure inputs. Application tests cover some of these failures, but they do not show what the real model will say.
- Repeat numerical-summary checks and bilingual questions. A single successful answer cannot establish consistent behavior.
- Page baselines and model tools may fetch at different instants. Compare source timestamps and time windows before treating a changed value as an error.
- HTTP 200 alone is not a pass. Review the returned answer against the facts and each case's criteria. The runner leaves quality reviews as `PENDING`.
- Token and dollar totals are not available from the public chat response. This run does not establish a billing total or a persisted tool trace.

## 简体中文

### 可重复运行的场景

`scripts/evaluate_assistant.py` 用合成问题检查线上应用，保存选中地点、页面事实接口响应和 AI 回答，供人工对照。它不需要 OpenAI key，也不保存真实用户的聊天。

上面的默认命令只列出场景，不发送请求；加上 `--live` 才会请求线上后端，并把报告保存到指定本地路径。线上调用可能产生模型费用。每次运行最多发出六次 AI 请求。地点和事实的只读 GET 请求遇到网络错误或 HTTP 502/503/504 时最多尝试三次；AI 的 POST 请求和 HTTP 429 不重试。应用原有的每客户端十分钟八次限制仍然适用于多次运行。原始报告保留在本机，不会自动上传。上面的离线测试命令不发送网络请求。

跨时区场景的英文问题含义是：“对于选中的地点，今晚对应哪个当地日期？请给出 UTC 偏移，以及可用的夜晚日期。”

### 线上结果

2026-09-28 约 14:35–14:38 UTC，使用 `gpt-6-luna` 完成五类场景，共六次成功问答。第一次 Apia 准备阶段发现候选同时有 Apia 城市和 Mount Apia 山，因此在调用模型前停止。核对城市 ID `4035413` 后，该场景单独跑了一次。这是小范围线上样本，不是稳定性评分。

| 场景 | 实际表现 | 核验 |
| --- | --- | --- |
| Dublin 同名地点，随后回复爱尔兰候选名称 | 首轮询问国家；第二轮直接提供爱尔兰云量，没有重复确认。 | 地点流程通过；云量范围摘要需要改进，见下文。 |
| Apia 当地日期 | UTC 时间为 9月28日 14:38 时，页面的三个当地夜晚为 9月29日、9月30日和10月1日，偏移为 UTC+13；AI 返回相同日期与偏移。 | 本次通过。 |
| 查询 2030年1月1日 | 说明没有该日预报，没有把当前数据当作 2030年的预报。 | 本次通过。 |
| 要求个人观测百分比 | 拒绝编造百分比，区分模型值和观测概率，并说明规则未验证及短时预报限制。 | 本次通过。 |
| 都柏林与科克的历史出现频率 | 说明没有已验证的本地频率数据，没有给平均周期。 | 本次通过。 |

都柏林回答把前半夜云量概括成约 80%–100%，但页面基线中当地 19:00 的云量为 65.6%。凌晨 04:00–05:00 约 20%–30% 的描述与基线一致。前一个范围过度简化了数据。现已调整助手指令：给数值范围时应包含指定时段内所有可用数值的最低值和最高值，或者列出准确的时间和值。该指令改动仍需部署后重新测试，不能据此宣称模型行为已修复。

后端 82 项测试继续全部通过。另外，五项离线评估脚本测试通过，覆盖请求数量上限、基线读取失败、追问保留候选信息和明确选中城市。它们验证应用与脚本行为，不代表真实模型推理评估。

### 仍未覆盖

- 用可控数据评估真实模型面对极昼、云量缺失、极光数据过期和工具故障时的回答。现有应用测试覆盖部分故障，但不能代表真实模型会怎么说。
- 重复核对数值摘要和中英问题。一次回答正确不能证明持续一致。
- 页面基线和模型工具可能在不同时间获取数据，判断数值差异前需要比较来源时间和有效时段。
- HTTP 200 本身不是通过标准，仍需按事实和场景要求人工核对回答。脚本把质量核验状态保持为 `PENDING`。
- 公开聊天响应没有 token 或美元汇总，因此本次没有确认账单总额，也没有持久化工具调用轨迹。


## Targeted recheck / 定向复验

Checked on / 核验日期: 2026-09-29

### English

A synthetic two-turn Dublin check was repeated after the cloud-summary instruction change. The first answer asked the user to choose among Dublin candidates. After selecting "Dublin, Leinster, Dublin City, Ireland", the assistant returned the local cloud forecast and included the lowest available value, 20.3%, in the stated range (20.3%–100%). This resolves the specific omission seen in the September 28 sample; it does not establish consistent behavior across other dates or prompts.

The Render health endpoint returned `UP`. An earlier location request returned HTTP 504 and an assistant request returned HTTP 502; subsequent location, health, and assistant requests succeeded. This records a brief failure followed by recovery, but does not identify the cause or establish service reliability. No real user conversation was used or stored.

In an isolated copy of the current working tree, backend verification passed 80 tests and the frontend lint, six unit tests, and production build passed. The build emitted the existing large map chunk warning. These local checks do not replace production browser testing. Further real-model review is still needed for polar day, missing or expired source data, tool failures, and repeated English and Chinese summaries.

### 简体中文

2026年9月29日，在调整云量摘要要求后，用合成问题重新检查了两轮 Dublin 对话。第一轮先要求用户从多个 Dublin 候选中选择；用户回复“Dublin, Leinster, Dublin City, Ireland”后，助手返回当地云量预报，并在区间（20.3%–100%）中包含最低值 20.3%。这说明9月28日发现的“摘要漏掉最低值”在本次案例中已修复，但不能证明其他日期和问法都持续正确。

Render 健康检查返回 `UP`。此前一次地点请求返回 HTTP 504、一次助手请求返回 HTTP 502；之后的地点、健康检查和助手请求均成功。这记录了短暂失败后恢复，但无法据此确定根因或服务稳定性。本次使用合成问题，没有使用或保存真实用户对话。

在当前工作树的隔离副本中，后端验证通过80项测试；前端 lint、6项单元测试和生产构建通过。构建仍提示地图资源包较大。这些本地检查不能代替正式站浏览器验收。真实模型仍需评估极昼、数据缺失或过期、工具故障，以及中英文数值摘要的重复一致性。

## 2026-10-01 live recheck / 2026-10-01 线上复验

### English

Five synthetic scenarios were run against the deployed Render backend using the live `gpt-6-luna` model, with six successful assistant requests. The local evidence files remain outside the repository. This verifies the deployed version at that moment, before the new database code is deployed.

| Scenario | Result |
| --- | --- |
| Dublin ambiguity and follow-up | The first reply asked which Dublin. Selecting the full Irish Dublin label returned local cloud data without another confirmation loop. The stated 0%–64.8% range and cited hourly values matched the page facts returned in this run. |
| Apia local date | The assistant returned October 1, 2, and 3 and UTC+13, matching the selected location's page facts. |
| Personal viewing percentage | The assistant refused a percentage, kept the viewing rule unvalidated, and distinguished the OVATION model value 0 from a 0% chance. |
| Unsupported date in 2030 | The assistant said the date was outside the available forecast and did not reuse current data as a 2030 prediction. |
| Dublin and Cork recurrence | The assistant said the app lacks validated local recurrence data and gave no invented interval. |

Each request returned HTTP 200. These are five reviewed examples, not a reliability or scientific accuracy score. Controlled live-model tests for polar day, missing cloud data, expired aurora data, and tool failure are still needed; local tests cover the application behavior for those states. The new run-record schema is tested locally but was not enabled on this deployed backend.

### 简体中文

使用线上 `gpt-6-luna` 模型对已部署的 Render 后端运行了五类合成场景，共六次成功请求。本机原始证据文件留在仓库外。这验证的是当时的已部署版本，早于这次数据库代码上线。

| 场景 | 结果 |
| --- | --- |
| Dublin 同名地点及追问 | 首轮询问具体地点；回复爱尔兰 Dublin 的完整名称后，直接返回当地云量，没有再次确认。本次回答的 0%–64.8% 区间和列出的逐小时值与页面事实响应一致。 |
| Apia 当地日期 | 回答 10月1日、2日、3日及 UTC+13，与所选地点的页面事实一致。 |
| 个人看到极光的百分比 | 拒绝给百分比，说明观测规则尚未验证，且模型值 0 不等于看到极光的概率为 0%。 |
| 2030 年日期 | 说明超出当前预报范围，没有把当下数据当作 2030 年预报。 |
| Dublin 与 Cork 的历史频率 | 说明没有已验证的当地历史频率数据，没有编造平均间隔。 |

所有请求均返回 HTTP 200。这是五个经过人工核对的实例，不是稳定性评分或科学准确率。极昼、云量缺失、极光数据过期及工具故障还需用可控输入检查真实模型；本地测试已覆盖这些状态下的应用行为。新运行记录数据库已通过本地测试，但这次线上后端尚未启用它。


## 2026-10-26：云量时间范围与连续追问修复

此前的逐小时校验按 HH:mm 合并不同日期的云量，且错误回答的备用样本直接取完整预报的头两个点，可能把今天早晨当成今晚。现在按地点时区和用户请求的当地日期筛选天气工具输出，保留日期、UTC 偏移和来源；校验和备用样本使用同一窗口。过期、缺失或范围外的预报不再由其他夜晚的数据填补。

“今晚”按所选地点的当前日历日处理，分组范围沿用当地中午至次日中午，不能理解为全程天黑；“现在”使用接下来三小时的可用预报。支持今晚、明晚、后晚、ISO 日期和中文月日，其他明确日期可由模型调用日期选择工具。确认地点后的追问保留地点，明确的新日期覆盖前一轮时间范围。

验证：后端完整 verify 共 147 项通过，包含 17 项新增时间范围与工具流程回归；前端 lint、38 项测试和生产构建通过。浏览器模拟“都柏林今晚 → 爱尔兰 → 明晚 → 现在”，核对后两轮请求携带正确地点 ID；390px 宽对话框无横向溢出。模拟响应不等同于真实模型验收，线上验收结果另行记录。


### 本次线上验收

2026-10-26 11:38 UTC 起，在 Render 已上线 `87c26a3` 后完成六轮真实模型请求，均返回 HTTP 200。包含一次同名地点澄清，确认爱尔兰都柏林后不再重复要求选择地点。后续响应携带 `selectedLocationId`，浏览器将其用于连续追问。

| 场景 | 内容核对 |
| --- | --- |
| 今晚 | 当地 10月26日晚至27日早；23:00 为97.7%、01:00 为26.6%、05:00 为13.3%，均与基线对应时间一致。 |
| 明晚 | 切换至27日晚至28日早，三项云量样本均与来源一致，没有沿用今晚数值。 |
| 现在 | 切换回26日12:00、13:00、14:00，三项样本均与来源一致，没有沿用明晚。 |
| 2030-01-01 | 明确超出预报范围，没有用当前数据填补。 |
| Apia | 正确识别当地已为10月27日，UTC+13；20:00 为99.2%、21:00 为100%，均与来源一致。 |

共核对11个带日期的小时云量样本。上线前的两次旧版调用不计入以上验收。以上是有限场景抽样，不代表所有自然语言表达或未来数据状态都已验证。

补充中英文明确日期与中文年份的解析回归，后端完整147项、前端38项、评估脚本10项测试通过。`night-followups` 是可重复的四轮验收场景，默认只预览，不调用模型：

```bash
python3 scripts/evaluate_assistant.py --case night-followups
```

需要线上复测时，在仓库根目录执行以下命令，会调用模型并可能产生费用；单次四轮，遇到限流停止、不重试模型请求。HTTP 200 仍需结合生成的本地报告人工核对日期和样本。

```bash
python3 scripts/evaluate_assistant.py --live --case night-followups --timeout 60 --base-url https://aurora-observation-agent.onrender.com --output /tmp/aurora-night-followups.json
```


补充验收：`b59ef78` 上线后增加两轮真实模型查询。“October 28”正确覆盖上一轮的 tonight，20:00 的0.8%与22:00 的7.0%均与来源一致；“2030年10月26日”明确返回范围外，没有将年份替换为当前年份。本次新版累计8轮真实请求，13个带日期的样本核对一致。

浏览器另测 Enter 发送、Shift+Enter 换行、503失败后重试：重试保持相同地点和历史，只保留一个用户问题气泡。发现竖向滚动条使窄屏对话框左侧溢出3px，已将宽度与拖动限制改为实际可用宽度；390px视口、375px可用区域下，打开、拖动、缩放后均保持在界内，右上角关闭正常。前端 lint、38项测试与构建通过。


## 2026-10-30：失败重试保留上下文与草稿

修复前，失败请求只保存问题、历史和显式选择的候选地点。重试会重新读取当前地图地点与界面语言，因此切换地图或语言后可能改变原问题的查询对象。输入新草稿还会隐藏重试按钮，重试本身也会清空草稿。

现在每次发送保存完整请求上下文，重试复用原问题、语言、历史和地点；只有发送新问题时才清空输入框。失败提示与重试入口在编辑草稿时保留，并用同步的请求锁阻止同一时刻重复提交。重试结果仍关联原地图地点，后续新问题使用当前地图选择。

本地验证：前端 lint、38 项单元测试、生产构建及现有 12 项桌面/手机浏览器回归全部通过。另用独立浏览器在桌面与 390×844 视口分别完成以下可重复检查：

1. 选择爱尔兰 Dublin，用英文提问，并模拟首次请求返回 503。
2. 关闭对话框，切换到美国 Dublin 和中文，再打开对话框并输入未发送的新草稿。
3. 点击重试，确认请求内容与失败请求完全相同，输入草稿保留，用户问题气泡仍只有一个。
4. 发送保留的草稿，确认新请求使用美国 Dublin 的地点 ID 和中文，同时包含已完成的一问一答。

上述新增场景通过受控 API 响应验证前端行为，没有调用真实模型；浏览器中的 503 和外部资源加载失败来自主动模拟的来源故障与网络隔离。原有地点澄清、连续追问及窗口交互回归保持通过。本次不修改后端预报或观测规则。


## 2026-10-30：相对日期追问解析

补齐“前天／前晚／前夜”、英文“day before yesterday”和“today”。时间识别和日期计算共用同一组模式；较长短语先匹配并移除，避免把“前天”中的 yesterday 或“后天”中的 tomorrow 再计一次。支持英文短语之间多个空格，并以完整英文单词匹配，避免 snow 或 unknown 中的 now 清除上一轮时间范围。

四项新增回归在修复前均失败，修复后通过，覆盖地点时区下的相对日期、追问覆盖、短语重叠和普通单词误匹配。明确涉及多天的问题仍要求选择日期；过去日期超出当前云量预报时返回范围外和空样本，不使用当前数据补齐历史。完整后端验证通过，共 155 项测试。真实模型输出与部署验收应另行核对，单元测试不代表模型每次都会正确选择工具。
