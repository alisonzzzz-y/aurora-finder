# Aurora Finder Demo Guide

[Live demo](https://aurora-finder.vercel.app)

## English

### Suggested walkthrough

1. Open the live demo and point out the global map. Switch between the available base map styles. Explain that the coloured band represents modelled aurora activity, not a ground-level chance of seeing aurora.
2. Search for `Dublin`. Select the Ireland result from the suggestions. The page should stay in place and show the selected location, its local time zone, the three-night outlook, cloud forecast, and darker hours when those data are available.
3. Scroll below the map to show the recent global activity chart and the strongest current model areas. Select a listed area to see the map move to that coordinate.
4. Open the AI chat and ask a question such as, “What does the latest Kp forecast mean for aurora activity?” For a place-specific question, select a location first or include the country to disambiguate it.
5. Switch to Simplified Chinese and show that the main interface and AI chat support both languages.

### What to explain

- Aurora Finder brings together public aurora, geomagnetic, weather, and location data, with source and update information shown in the interface.
- A short-term activity model and the global Kp index describe different things. Neither is a calibrated personal viewing probability.
- “Insufficient data” is an honest result when the current sources do not support a reliable combined outlook. Cloud cover and darkness are shown as separate conditions.
- The AI assistant is read-only. It uses the app's available data and should not be presented as an independent forecast source.

### Demo checks

- The map, its timestamp, and its attribution are visible.
- A location search requires choosing a result; selecting it does not navigate away or reload the page.
- The selected location's country and time zone are correct.
- Source failures or missing coverage are described as unavailable or missing, not as clear skies or zero activity.
- Do not show API keys, provider credentials, or account billing pages in a public recording.

## 简体中文

### 建议演示顺序

1. 打开线上演示，先介绍全球地图，并切换可用的底图样式。说明地图上的颜色表示模型估算的极光活动，不代表地面看到极光的概率。
2. 搜索 `Dublin`，从候选项中选择爱尔兰的都柏林。页面应留在当前页面，并显示所选地点、当地时区、三晚预报，以及数据可用时的云量和较暗时段。
3. 向下滚动，展示近期全球活动图和当前模型活动较强的位置。点击列表中的位置，地图应移动到对应坐标。
4. 打开 AI 对话，提问例如“最新的 Kp 预报对极光活动意味着什么？”。查询具体地点时，先选择地点，或在问题里说明国家以区分同名地点。
5. 切换到简体中文，展示主要界面和 AI 对话支持中英文。

### 需要说明的内容

- Aurora Finder 汇总公开的极光、地磁、天气和地点数据，并在界面中标注来源与更新时间。
- 短时极光活动模型和全球 Kp 指数描述的是不同信息，两者都不是经过校准的个人观测概率。
- 当现有来源不足以支持可靠的综合预报时，页面会显示“数据不足”。云量和黑暗时段会作为独立条件展示。
- AI 助手是只读的。它依据应用当前可用的数据回答，不应被介绍成独立的预报来源。

### 演示检查

- 地图、时间戳和版权署名均可见。
- 地点搜索需要从候选项中选择；选择后不会跳转或刷新页面。
- 所选地点的国家和时区正确。
- 数据源失败或缺测时，页面应明确显示不可用或缺少数据，不应解释成晴空或活动为零。
- 公开录屏时不要展示 API key、服务商凭据或账户账单页面。
