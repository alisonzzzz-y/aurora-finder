# 07 区：地面观测验证数据候选

状态：研究候选，尚未导入、处理或用于等级规则。资料核对日期：2026-09-25。

## 候选来源：Aurorasaurus Web Observations

Zenodo 发布了 2014-08-01 至 2025-08-02 的 Aurorasaurus 网页观测 CSV，含 transformed 与 cleaned 两种文件，总大小约 16.8 MB。记录说明这是社区提交的极光目击观测数据，供科学研究使用。NASA 的项目介绍说明报告者可提交“看到”或“未看到”的观测。2026 年技术报告已在 Frontiers 发布，Aurorasaurus 的数据发布说明进一步概述了处理流程：transformed 数据会移除或遮盖垃圾、测试和个人信息；cleaned 数据还会排除持续超过 12 小时、报告为多云/光污染/月光过亮以及重复的记录。说明指出，遮挡天空的记录无法确认或否定极光，因此科学分析通常关注明确表示天空清晰的负面报告。

该处理说明支持一个重要边界：清晰天空下明确提交的“未看到”可以作为候选负标签；没有报告仍然不是负标签。发布方也提醒，报告受观察视角、人口分布和光污染影响，报告地点并不等于极光实际所在的位置；数据在强地磁风暴期间尤其丰富。因此不能假定全时段、全球和地点覆盖均衡，也不能直接把记录比例解释为某地的观测概率。清理流程的公开概述并没有解决具体字段语义、坐标与时间精度、各文件差异及本项目所需空间/时间匹配误差。

## 本项目可用来回答什么

若最终使用许可与字段语义明确，这类数据可用于离线评估短时间、地点匹配下的模型信号是否与经质量控制的地面观测相符。它比用全球 Kp 标签更贴近地面观测，但不能单独推出未来整晚的等级：社区报告不是系统性巡天，负面样本的天空条件和参与者覆盖会影响结果，地点周边天气也必须按同一时间窗口匹配。

可先设计离线评估，不训练或部署模型：按地点、时间、日照/黑暗、云量、OVATION 和 Kp 形成可追溯样本；把明确的正向观测与符合条件的明确负向观测分开；按时间留出测试集，并按地区与风暴强弱报告样本量和误差。报告数量不足或标签条件不完整的样本仅用于描述，不参与阈值选择。

## 使用前置检查

- [ ] 继续逐字段读取 2026 技术报告和配套 notebook/字典，核实标签、时间精度、坐标、质量标记以及 cleaned/transformed 文件之间的精确差异。已查到技术报告和发布方对清理步骤的摘要，但本次没有下载或解析 CSV，也未确认字段级定义。
- [ ] 联系数据发布方，确认 Zenodo 数据记录中空白的 Rights/License 栏是否允许本项目所需的下载、离线分析、衍生评估和公开展示。Frontiers 论文的 CC BY 条款只明确适用于论文，不能据此推断 CSV 数据许可。确认前不将文件提交仓库、不重新分发、不用于线上产品。
- [x] 初步核实负面标签边界：发布方说明清晰天空下明确报告“未看到”具有科学价值，云遮、光污染或月光过亮的记录会从 cleaned 文件排除；没有报告不能作为负例。实际 CSV 是否保留足够字段以可靠筛出这些记录仍待字段审查。
- [ ] 将报告位置、时间与 NOAA/云量数据匹配，并量化时间误差、空间误差、地区与事件覆盖偏差。
- [ ] 先预注册/冻结评估切分和指标，再查看留出集结果，避免用同一批样本调阈值又报告准确度。

## 2026-09-25 来源复核记录

- Zenodo 页面列出两个 CSV（transformed 8.8 MB、cleaned 8.0 MB），说明数据开放获取、面向科学研究，但 Rights 下的 License 项没有给出许可标识或文本。
- 2026 技术报告发表于 Frontiers，论文页面说明论文采用 CC BY；这不是 CSV 数据许可的替代说明。
- Aurorasaurus 发布说明明确概述 transformed 与 cleaned 的处理差异，也说明负面报告的天空条件和人口/观察视角偏差。该说明足以排除“无报告等于没看到”的做法，但不足以验证本项目能否构造可靠的负样本。
- 因为仍未核实 CSV 许可和字段级定义，本项目没有下载数据文件，没有新增外部数据依赖，也没有调整线上规则。

## 当前结论

Aurorasaurus 是值得进一步查字段与许可的地面报告候选，不是已经获准使用或已验证本项目规则的数据集。当前规则继续保持 `NOT_VALIDATED`，接口继续返回 `INSUFFICIENT_DATA`；没有导入原始报告，也没有生成极光概率。

## 来源

- 数据集：[Aurorasaurus Web Observations (2014–2025), Zenodo](https://zenodo.org/records/16783265)。
- 项目说明：[Aurorasaurus, NASA Science](https://science.nasa.gov/citizen-science/aurorasaurus/)。
- 技术报告：[MacDonald et al. (2026), Frontiers](https://www.frontiersin.org/journals/astronomy-and-space-sciences/articles/10.3389/fspas.2026.1883317/abstract)。
- 发布方处理流程说明：[A solar cycle of data: Aurorasaurus reports 2014–2025](https://blog.aurorasaurus.org/?p=2520)。
- 方法与局限：[Kosar et al. (2018), NOAA Central Library copy](https://repository.library.noaa.gov/view/noaa/21676/noaa_21676_DS1.pdf)。
- 数据使用条款：[Aurorasaurus Privacy Policy and Terms](https://blog.aurorasaurus.org/?page_id=1064)。
