# Aurora Finder

[Live demo / 在线体验](https://aurora-finder.vercel.app) · [English](#english) · [简体中文](#简体中文)

<a id="english"></a>

## English

Aurora Finder helps people explore current aurora activity and check conditions for a chosen place. Search for a city, choose the right match, and see the local forecast, cloud cover, darker hours, and when each piece of information was updated.

The global map shows modelled aurora activity. It does not tell you the exact chance of seeing aurora from the ground. Clouds, darkness, and other viewing conditions are shown separately, and some full-night results may say “Insufficient data” while the rules are being checked.

### What you can do

- Explore the global aurora map and switch between dark, street, and satellite views.
- Search for a place and choose the matching city.
- Check short-term aurora activity, cloud cover, and local darker hours when data is available.
- See the latest global activity outlook and the time and source behind the data.
- Switch between English and Simplified Chinese.

The AI chat feature is not available yet. Aurora Finder is an ongoing project, so some information and features are still being checked and improved.

### Run it locally

You will need Java 21 and Node.js 22 or newer. Start the backend and frontend in separate terminals:

```sh
cd backend
./mvnw spring-boot:run
```

```sh
cd frontend
npm install
npm run dev
```

To show the map locally, add a restricted MapTiler key as `VITE_MAPTILER_KEY` in `frontend/.env.local`. Setup details are in [API access instructions](docs/api-access.md). Do not commit this local key file.

### Data and credits

Aurora activity, weather, and place information come from separate public data services. The app shows their sources and update times so you can understand what each result is based on. The map background is provided by MapTiler. Place search uses Open-Meteo data under CC BY 4.0. Credit: Open-Meteo.

<a id="简体中文"></a>

## 简体中文

Aurora Finder 帮助普通观测者了解当前极光活动，并查看指定地点的观测条件。搜索城市并选择正确地点后，可以查看当地极光活动、云量、较暗时段，以及各项数据的更新时间。

全球地图展示的是模型预测的极光活动，并不代表人在地面看到极光的具体概率。云量、黑暗时段等观测条件会分别展示；部分整晚结果在规则核查完成前可能显示“数据不足”。

### 目前可以做什么

- 查看全球极光地图，并切换深色、街道和卫星影像底图。
- 搜索地点，并从候选项中选择正确的城市。
- 在数据可用时查看短时极光活动、云量和当地较暗时段。
- 查看最新的全球活动趋势，以及数据来源和更新时间。
- 在英文和简体中文之间切换。

AI 对话功能目前尚未开放。项目仍在持续开发，部分数据和功能还在核查与完善中。

### 本地运行

需要 Java 21 和 Node.js 22 或更新版本。在两个终端中分别启动后端和前端：

```sh
cd backend
./mvnw spring-boot:run
```

```sh
cd frontend
npm install
npm run dev
```

如需在本地显示地图，请在 `frontend/.env.local` 中设置受来源限制的 MapTiler key，变量名为 `VITE_MAPTILER_KEY`。具体说明见 [API 获取说明](docs/api-access.md)。不要将本地 key 文件提交到 Git。

### 数据与署名

极光活动、天气和地点信息来自不同的公开数据服务。页面会标明数据来源和更新时间，方便了解每项结果的依据。地图底图由 MapTiler 提供。地点搜索使用 Open-Meteo 数据，遵循 CC BY 4.0。署名：Open-Meteo。
