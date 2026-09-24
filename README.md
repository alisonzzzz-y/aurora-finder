# Aurora Finder

[English](#english) | [简体中文](#简体中文)

<a id="english"></a>

## English

Aurora Finder is an early location-based aurora forecast project. It combines a global NOAA OVATION map with local place search, a short-range local OVATION activity level, and time-zone-aware night outlooks. The local activity level is a model grid estimate, not the probability that a person will see aurora. The project does **not** calculate a combined personal viewing probability or provide AI advice in this foundation release.

**Live demo:** No deployment URL is recorded yet. Once deployed, put the Vercel URL here near the top of this README and in the GitHub repository's **About → Website** field.

## What works now

- Search for a named place using Open-Meteo geocoding, then select a result with its coordinates and IANA time zone. Arbitrary coordinates and map-pin selection are not supported.
- Show the selected place's current local date and the following two local dates. Dates and timestamps use the selected place's time zone and show UTC offsets.
- Display three night cards. Each currently reports **Insufficient data** because the source inputs and viewing rules have not been validated.
- Display the latest available short-range NOAA OVATION model grid on an interactive MapLibre map. MapTiler provides the basemap tiles.
- Show the next NOAA three-hour Kp forecast below the map with a low, medium, or high global activity label. This is not a local visibility rating or viewing probability.
- For a selected place, show the nearest NOAA OVATION grid value and low, medium, or high short-range local activity level, with model and forecast timestamps.
- Switch the interface between English and Simplified Chinese. The selection is saved in the browser.

The OVATION layer shows modeled aurora activity, not ground-level visibility. It does not include local clouds, darkness, terrain, light pollution, or the observer's horizon. The AI entry point is not enabled.

## Run locally

Requirements: Java 21 and Node.js 22 or newer.

Start the backend:

```sh
cd backend
./mvnw spring-boot:run
```

In another terminal, start the frontend:

```sh
cd frontend
npm install
npm run dev
```

Open the local URL printed by Vite. Its development proxy sends `/api` requests to Spring Boot on port 8080.

Run checks:

```sh
cd backend
./mvnw test
./mvnw -DskipTests package
```

```sh
cd frontend
npm run typecheck
npm run lint
npm run build
```

## Map setup

The NOAA OVATION feed is public and does not require an API key. MapTiler is used for the basemap and does require a browser key:

1. Create a key in MapTiler Cloud.
2. Restrict its allowed origins to `http://localhost:5173` and the deployed site origin.
3. Copy `frontend/.env.example` to `frontend/.env.local` and set `VITE_MAPTILER_KEY`.

The browser must use the key to request map tiles, so restrict its allowed origins. Never commit `.env.local`. External HTTP connection and provider request timeouts can be configured with `APP_HTTP_CONNECT_TIMEOUT`, `APP_GEOCODING_REQUEST_TIMEOUT`, and `APP_OVATION_REQUEST_TIMEOUT`.

## API examples

- `GET /api/v1/locations?q=Dublin`
- `GET /api/v1/outlooks/2964574`
- `GET /api/v1/aurora-map`
- `GET /api/v1/aurora-activity?latitude=64.1&longitude=-21.9`
- `GET /api/v1/kp-index`
- `GET /actuator/health`

Set `APP_GEOCODING_ENABLED=false` to disable calls to Open-Meteo. Its free endpoint is limited to non-commercial use; review the provider's terms before changing the use or deploying publicly. See [API access instructions](docs/api-access.md) for provider URLs, key requirements, and request identity setup.

## Architecture and current limits

The backend uses controller, service, provider, config, and DTO packages. It has no database, so it has no repository or entity classes. Location searches are cached for 10 minutes and location records for one hour, with up to 256 entries per cache. Provider failures distinguish invalid responses, timeouts, rate limits, and other errors. Deployment behavior still needs verification.

Before implementing viewing levels, complete the [Phase 0 source and rule review](docs/phase-0-data-and-rules.md). This includes MET Norway identification and cache handling, darkness calculations across polar and date-line cases, source freshness rules, and evidence-based thresholds. A provider failure must never be presented as a low viewing level.

The planned product is guidance for unaided-eye viewing, not a measured personal probability or a guarantee. The local OVATION card describes the nearest model grid cell and does not account for clouds, darkness, terrain, local light pollution, or the observer's horizon. See [DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md) for the phased implementation plan.

## Data sources

- [NOAA OVATION short-range aurora forecast](https://www.spaceweather.gov/products/aurora-30-minute-forecast)
- [NOAA Kp forecast feed](https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json)
- [MET Norway Locationforecast data model](https://docs.api.met.no/doc/locationforecast/datamodel.html) and [terms](https://docs.api.met.no/doc/TermsOfService)
- [Open-Meteo geocoding documentation](https://open-meteo.com/en/docs/geocoding-api) and [terms](https://open-meteo.com/en/terms)

Location results use Open-Meteo geocoding data under CC BY 4.0. Credit: Open-Meteo.

<a id="简体中文"></a>

## 简体中文

Aurora Finder 是一个早期的地点型极光预报项目，结合 NOAA OVATION 全球地图、地点搜索、短时当地 OVATION 活动等级和按当地时区显示的夜间信息。当地活动等级是模型网格估计，不代表个人看到极光的概率。当前基础版本**不会计算综合个人观测概率，也不提供 AI 建议**。

**在线演示：**目前仓库中还没有记录已部署的网址。部署后，建议把 Vercel 链接放在本 README 开头附近，同时填写 GitHub 仓库 **About → Website** 栏。

## 当前功能

- 使用 Open-Meteo 地理编码搜索地点，并由用户选择包含坐标和 IANA 时区的结果。暂不支持任意坐标和地图选点。
- 显示所选地点当地的今天及随后两天。日期和时间均按所选地点的时区显示，并附带 UTC 偏移。
- 显示三晚卡片。由于数据输入和观测规则尚未验证，目前状态均为“数据不足”。
- 通过可交互的 MapLibre 地图展示 NOAA OVATION 最新短时模型网格；底图瓦片由 MapTiler 提供。
- 在地图下显示下一段 NOAA 三小时 Kp 预报及低、中、高全球活动等级。该等级不是当地可见性判断或观测概率。
- 选择地点后，显示最近 NOAA OVATION 网格值、低/中/高短时当地活动等级，以及模型观测时间和预报有效时间。
- 支持英文与简体中文界面切换，并在浏览器中记住语言选择。

OVATION 图层展示的是模型中的极光活动，不代表地面可见范围。它没有包含当地云量、黑暗时段、地形、光污染或观察者的地平线条件。AI 问答入口尚未启用。

## 本地运行

需要 Java 21 和 Node.js 22 或更新版本。

启动后端：

```sh
cd backend
./mvnw spring-boot:run
```

另开一个终端启动前端：

```sh
cd frontend
npm install
npm run dev
```

打开 Vite 输出的本地网址。开发代理会将 `/api` 请求转发到 8080 端口的 Spring Boot 服务。

运行检查：

```sh
cd backend
./mvnw test
./mvnw -DskipTests package
```

```sh
cd frontend
npm run typecheck
npm run lint
npm run build
```

## 地图配置

NOAA OVATION 数据源公开提供，无需 API key。底图使用 MapTiler，需要浏览器端密钥：

1. 在 MapTiler Cloud 创建 key。
2. 将允许的来源限制为 `http://localhost:5173` 和已部署网站的来源。
3. 将 `frontend/.env.example` 复制为 `frontend/.env.local`，然后设置 `VITE_MAPTILER_KEY`。

浏览器需要使用该 key 请求地图瓦片，因此请限制允许的来源。不要提交 `.env.local`。外部 HTTP 连接超时和数据提供商请求超时可分别通过 `APP_HTTP_CONNECT_TIMEOUT`、`APP_GEOCODING_REQUEST_TIMEOUT` 和 `APP_OVATION_REQUEST_TIMEOUT` 配置。

## API 示例

- `GET /api/v1/locations?q=Dublin`
- `GET /api/v1/outlooks/2964574`
- `GET /api/v1/aurora-map`
- `GET /api/v1/aurora-activity?latitude=64.1&longitude=-21.9`
- `GET /api/v1/kp-index`
- `GET /actuator/health`

设置 `APP_GEOCODING_ENABLED=false` 可关闭 Open-Meteo 请求。其免费接口仅限非商业用途；若要改变用途或公开部署，请先复核服务条款。数据提供商地址、key 要求和请求身份设置请查看 [API 获取说明](docs/api-access.md)。

## 架构与当前限制

后端分为 controller、service、provider、config 和 DTO 包。项目没有数据库，因此暂时没有 repository 或 entity 类。地点搜索缓存 10 分钟，地点记录缓存 1 小时，每类缓存最多 256 条。提供商错误区分无效响应、超时、限流和其他失败。部署环境的行为仍待验证。

实现观测等级前，请先完成[数据源与规则核查](docs/phase-0-data-and-rules.md)，包括 MET Norway 身份与缓存要求、高纬度和日期变更线附近的黑暗时段计算、数据时效规则以及有证据支持的等级阈值。数据提供商失败时，绝不能把结果显示成“低”。

项目计划提供肉眼观测参考，不会给出个人观测概率或保证。当地 OVATION 卡片描述最近模型网格点，不考虑云量、黑暗时段、地形、当地光污染和观察者的地平线。分阶段开发计划见 [DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md)。

## 数据来源

- [NOAA OVATION 短时极光预报](https://www.spaceweather.gov/products/aurora-30-minute-forecast)
- [NOAA Kp 预报数据](https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json)
- [MET Norway Locationforecast 数据模型](https://docs.api.met.no/doc/locationforecast/datamodel.html)和[服务条款](https://docs.api.met.no/doc/TermsOfService)
- [Open-Meteo 地理编码文档](https://open-meteo.com/en/docs/geocoding-api)和[服务条款](https://open-meteo.com/en/terms)

地点搜索结果使用 Open-Meteo 地理编码数据，遵循 CC BY 4.0。署名：Open-Meteo。
