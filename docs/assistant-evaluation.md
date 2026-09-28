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
