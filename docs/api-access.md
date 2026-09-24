# 数据源 API 获取指南

NOAA OVATION 和 Open-Meteo 地点搜索接口不需要 API key。MapTiler 只为地图底图瓦片需要一个前端 Key。MET Norway 天气接口也不使用 API key，但必须发送真实、可联系的 User-Agent。

## Open-Meteo 地点搜索与时区

地点搜索：

~~~sh
curl 'https://geocoding-api.open-meteo.com/v1/search?name=Dublin&count=10&language=en&format=json'
~~~

响应包含地点 ID、坐标、国家/地区和 IANA 时区。同名地点可能返回多个结果，应用应让用户选择。该免费接口只用于非商业用途，并按 CC BY 4.0 标注 Open-Meteo。若产品用于商业场景，应先查看其最新使用条款并申请适用的服务计划。

后端按标准化后的搜索词缓存 10 分钟，地点记录缓存 1 小时；默认每个 Spring Boot 进程最多处理 500 次/分钟、4,500 次/小时、9,000 次/日的未命中请求。当前 Open-Meteo 免费额度公开为每天 10,000、每小时 5,000、每分钟 600 次。相同并发请求会合并，失败请求不自动重试。多个后端实例需要共享限流计数，或将总预算分配到各实例。

获取所选地点：

~~~sh
curl 'https://geocoding-api.open-meteo.com/v1/get?id=2964574'
~~~

## NOAA 极光与 Kp

OVATION 短时极光预测：

~~~sh
curl 'https://services.swpc.noaa.gov/json/ovation_aurora_latest.json'
~~~

Kp 预测：

~~~sh
curl 'https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json'
~~~

以上是公开数据 URL，不要求 API key。NOAA 将 OVATION 描述为短期极光位置和强度预测，通常提前约 30–90 分钟。`/api/v1/aurora-map` 与 `GET /api/v1/aurora-activity?latitude=...&longitude=...` 均分别返回 NOAA 观测时间、预报目标时间和本服务获取时间。选定地点的接口会读取最近 1° 网格点，返回短时活动等级（low/medium/high）和原始网格值。分级规则 `ovation-local-v1` 为 `<18` 低、`18–49` 中、`>=50` 高；18 对应 NOAA 研究采用的可见极光边界，50 是便于阅读的产品分组线。它不表示个人观测概率，也不覆盖云量、黑暗、地形或视野。个人观测条件和未来几晚继续使用 `INSUFFICIENT_DATA`，直到相关来源及规则完成验证。后端缓存 NOAA 响应五分钟。

## MapTiler 底图 Key

本项目用开源 MapLibre GL JS 绘制交互地图，用 MapTiler Cloud 提供底图瓦片。MapLibre 本身不要求 Key，MapTiler 在线样式/瓦片需要 Key。个人或非商业原型可先查看 MapTiler 的 Free 方案和当前配额；公开部署或用途变化前，要重新核对其条款与限额。

1. 在 [MapTiler Cloud](https://cloud.maptiler.com/) 注册或登录。
2. 打开 **API keys**，创建一个专供本项目使用的 Key。
3. 为 Key 限制可用网站来源，先加入 `localhost`。部署后再加入实际域名，例如 `aurora-finder.vercel.app`；只填域名，不带协议或端口。
4. 将 Key 写入本地 `frontend/.env.local`：

~~~dotenv
VITE_MAPTILER_KEY=粘贴你的受限Key
~~~

5. 重启 Vite 开发服务器。可从 `frontend/.env.example` 复制文件名模板。

浏览器地图需要把这个 Key 发送给 MapTiler，因此它不是服务端秘密；通过来源限制保护它。不要把未受限 Key 提交到 Git、写入 README 或发送到聊天中。仓库 `.gitignore` 已排除 `.env.local`。

## MET Norway 云量预报

接口：

~~~text
https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=53.3331&lon=-6.2489
~~~

请求必须设置真实 User-Agent，其中包含项目名称以及可联系的项目网址或邮箱。MET Norway 不需要 API key。官方要求提供可联系的身份，遵守缓存响应头、最多四位小数坐标和数据署名规则。 [MET Norway 使用条款](https://docs.api.met.no/doc/TermsOfService)

~~~sh
curl -H 'User-Agent: AuroraOutlook/0.1 alison.zhangyan@gmail.com' \
  'https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=53.3331&lon=-6.2489'
~~~

本项目后端默认通过 `app.metno.user-agent` 发送 `AuroraOutlook/0.1 alison.zhangyan@gmail.com`，也可以用服务端环境变量 `METNO_USER_AGENT` 覆盖。当前仓库未配置 Git remote，尚未确认真实仓库 URL，因此没有伪造或猜测 GitHub 地址。仓库地址确认后，可把 User-Agent 改为 `AuroraOutlook/0.1 github.com/账户名/仓库名`。MET Norway 官方文档接受应用名加 GitHub 项目地址作为识别方式，也要求对方能联系到项目维护者。[Getting Started](https://docs.api.met.no/doc/GettingStarted.html)

## 在本项目中配置

- Open-Meteo 地点搜索地址目前在后端 app.geocoding.base-url 配置。
- MapTiler Key 由前端通过 `VITE_MAPTILER_KEY` 读取；它用于底图请求并受来源限制，不是 NOAA Key。
- MET Norway User-Agent 由后端 `app.metno.user-agent` 配置，可通过部署环境变量 `METNO_USER_AGENT` 替换。
- 若以后接入需要密钥的服务，只在服务端环境变量中配置；不要写进 React 前端、提交到 Git 或粘贴到公开聊天中。
- 天气接口尚未接入。完成部署身份配置、缓存和来源验证后，再添加天气 Provider。

## GitHub 身份与推送

推送 GitHub 使用 GitHub CLI 登录身份，不需要 NOAA 或天气 API key：

~~~sh
gh auth login --web
gh auth status
~~~

在浏览器完成 GitHub 登录后，再创建仓库并推送本地分支。不要把 Personal Access Token 写进远程 URL 或仓库文件。
