# 07 区：地面观测验证数据候选

状态：已完成 cleaned CSV 本地质量审计；未纳入产品或等级规则。资料核对日期：2026-09-26。

## 候选来源：Aurorasaurus Web Observations

Zenodo 发布了 2014-08-01 至 2025-08-02 的 Aurorasaurus 网页观测 CSV，含 transformed 与 cleaned 两种文件，总大小约 16.8 MB。记录说明这是社区提交的极光目击观测数据，供科学研究使用。NASA 的项目介绍说明报告者可提交“看到”或“未看到”的观测。2026 年技术报告已在 Frontiers 发布，Aurorasaurus 的数据发布说明及官方清理 notebook 进一步概述了处理流程：transformed 数据会移除或遮盖垃圾、测试和个人信息；cleaned 数据还会排除持续超过 12 小时、`sky_id` 为 cloudy (`clou`) 或 bright (`brig`) 的记录，并处理重复报告。notebook 使用 `see_aurora` 区分看到/未看到，`time_start` 和 `time_end` 作为时间字段，`st_y`/`st_x` 作为地理纬度/经度输入来计算地磁坐标。时间筛选将 `time_start` 解析为 UTC。

该处理说明支持一个重要边界：清晰天空下明确提交的“未看到”可以作为候选负标签；没有报告仍然不是负标签。发布方也提醒，报告受观察视角、人口分布和光污染影响，报告地点并不等于极光实际所在的位置；数据在强地磁风暴期间尤其丰富。因此不能假定全时段、全球和地点覆盖均衡，也不能直接把记录比例解释为某地的观测概率。仍需直接核对实际 CSV 的列类型、空值、坐标精度和时间格式。notebook 将人工重复项复核标为可选，并注明仅对 2024 年 5 月 Gannon 风暴数据执行过；因此不能假设整个 cleaned 文件都经过相同的人工排重。发布流程文档没有给出本项目所需的空间/时间匹配误差。

## 本项目可用来回答什么

若最终使用许可与字段语义明确，这类数据可用于离线评估短时间、地点匹配下的模型信号是否与经质量控制的地面观测相符。它比用全球 Kp 标签更贴近地面观测，但不能单独推出未来整晚的等级：社区报告不是系统性巡天，负面样本的天空条件和参与者覆盖会影响结果，地点周边天气也必须按同一时间窗口匹配。

可先设计离线评估，不训练或部署模型：按地点、时间、日照/黑暗、云量、OVATION 和 Kp 形成可追溯样本；把明确的正向观测与符合条件的明确负向观测分开；按时间留出测试集，并按地区与风暴强弱报告样本量和误差。报告数量不足或标签条件不完整的样本仅用于描述，不参与阈值选择。

## 使用前置检查

- [ ] 对照检查 transformed 与 cleaned 两个 CSV 的列、类型和处理差异。
- [ ] 联系数据发布方，确认 Zenodo 数据记录中空白的 Rights/License 栏是否允许本项目所需的下载、离线分析、衍生评估和公开展示。官方 notebook 仓库的 Apache-2.0 LICENSE 与 `.zenodo.json` 授权对象是清理/分析软件，不能据此推断其关联 CSV 数据也采用 Apache-2.0。Frontiers 论文的 CC BY 条款同样只明确适用于论文。确认前不将文件提交仓库、不重新分发、不用于线上产品。
- [x] 核实负面标签边界：仅把 `see_aurora=False` 且 `sky_id=clea` 的记录作为候选负样本；天空字段为空的 1,683 条未看到报告排除。没有报告不能作为负例。
- [ ] 将报告位置、时间与 NOAA/云量数据匹配，并量化时间误差、空间误差、地区与事件覆盖偏差。
- [ ] 先预注册/冻结评估切分和指标，再查看留出集结果，避免用同一批样本调阈值又报告准确度。
- [x] 按用户要求在本地临时目录审计 cleaned CSV，校验 MD5 与 Zenodo 发布值一致；原始 CSV 没有加入仓库。

## 2026-09-25 来源复核记录

- Zenodo 页面列出两个 CSV（transformed 8.8 MB、cleaned 8.0 MB），说明数据面向科学研究，但 Rights 下的 License 项没有给出许可标识或文本。配套 GitHub 仓库标注 Apache-2.0，`.zenodo.json` 的对象类型是 software，许可是 Apache-2.0；这是代码仓库的许可证，不是 CSV 许可。
- 2026 技术报告发表于 Frontiers，论文页面说明论文采用 CC BY；这不是 CSV 数据许可的替代说明。
- 官方 notebook 使用 `see_aurora` 表示报告者是否看到极光，以 `time_start`/`time_end` 表示观测区间，以 `st_y`/`st_x` 表示输入地理坐标，并基于 UTC 过滤。它只在一个指定 2024 风暴分析流程中展示人工复核，且将该步骤注明为可选，不能视为完整数据集都人工排重。Aurorasaurus 发布说明和 notebook 足以排除“无报告等于没看到”的做法，但不足以验证本项目能否构造可靠的负样本。
- 该次来源复核尚未下载数据文件；后续本地审计已按用户要求进行。

## 2026-09-26 cleaned CSV 本地数据审计

文件：`web_observations_2014-08-01_to_2025-08-02_cleaned.csv`，Zenodo 发布文件的 MD5 为 `2319009e2e2f3dfb596ad5368e719860`，本地下载校验一致。审计副本保存在系统临时目录，不在 Git 仓库。

- 文件含 22,280 条记录、33 列；所有行列数一致。`time_start`/`time_end` 均可解析并带 UTC 偏移；时间跨度为 2014-08-28 至 2025-07-31。坐标列在此文件中均可解析，且都在经纬度范围内。
- `see_aurora=True` 有 16,368 条；`see_aurora=False` 有 5,912 条。负标签中，4,229 条的 `sky_id` 明确为 `clea`，另有 1,683 条天空状况为空。离线评估仅把“明确未看到且 sky_id=clea”纳入候选负样本；天空字段为空的未看到记录排除，不把无报告当负样本。
- 2024 年有 11,465 条，占全部记录约 51.5%，显示报告强烈集中于特定年份/事件；不能将全表正负记录比例解释为一般地点的看到概率。
- 按官方 notebook 的候选重复签名找到 714 组、1,994 条候选记录。这只是供人工复核的筛选结果；其中 429 组的最小坐标间距低于约 2 km，坐标不同本身不能证明重复，也不能直接删除。`raw_row_num` 在本文件中无重复。
- 该审计只验证了 cleaned 文件的结构与标签可用性，不是 OVATION 预测能力验证，也没有确定任何阈值、等级或概率。

## 当前结论

Aurorasaurus cleaned 文件已按用户要求用于一次本地离线质量审计，但数据许可仍未由发布方明确确认，文件未加入仓库或重新分发。数据可用于继续设计受限的回溯评估：仅把明确报告看到极光作为候选正样本，把明确未看到且天空字段为 `clea` 的报告作为候选负样本，排除天空状况未知的负报告，并对重复候选组进行人工/规则复核。强烈的事件和地区选择偏差仍未解决。当前线上规则继续保持 `NOT_VALIDATED`，接口继续返回 `INSUFFICIENT_DATA`；没有生成极光概率。

## 来源

- 数据集：[Aurorasaurus Web Observations (2014–2025), Zenodo](https://zenodo.org/records/16783265)。
- 项目说明：[Aurorasaurus, NASA Science](https://science.nasa.gov/citizen-science/aurorasaurus/)。
- 技术报告：[MacDonald et al. (2026), Frontiers](https://www.frontiersin.org/journals/astronomy-and-space-sciences/articles/10.3389/fspas.2026.1883317/abstract)。
- 发布方处理流程说明：[A solar cycle of data: Aurorasaurus reports 2014–2025](https://blog.aurorasaurus.org/?p=2520)。
- 官方清理 notebook：[Aurorasaurus_on_Jupyter, 2_data_cleaning.ipynb](https://github.com/aurorasaurus/Aurorasaurus_on_Jupyter/blob/main/2_data_cleaning.ipynb)。
- 软件许可证记录：[Aurorasaurus_on_Jupyter, LICENSE](https://github.com/aurorasaurus/Aurorasaurus_on_Jupyter/blob/main/LICENSE)（仅用于确认代码许可范围）。
- 方法与局限：[Kosar et al. (2018), NOAA Central Library copy](https://repository.library.noaa.gov/view/noaa/21676/noaa_21676_DS1.pdf)。
- 数据使用条款：[Aurorasaurus Privacy Policy and Terms](https://blog.aurorasaurus.org/?page_id=1064)。
