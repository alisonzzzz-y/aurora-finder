# Production API smoke check / 线上 API 冒烟检查

## English

Run a low-cost read-only check against the deployed backend:

```sh
python3 scripts/smoke_production.py
```

The default request timeout is 65 seconds to allow a free Render instance to wake up. The script checks the health endpoint, searches for Dublin, selects the unique Ireland result only when it is unambiguous, then checks that place's facts endpoint, the global aurora map, Kp data, and the geomagnetic storm forecast. It reports HTTP status, JSON shape, and response time for each request. It does not call the AI or make any writes. A provider may report unavailable data inside a successful API response; this check verifies that the endpoint is reachable and returns JSON, not that every upstream dataset is currently populated.

Use a different deployed backend or timeout when needed:

```sh
python3 scripts/smoke_production.py --base-url https://your-backend.example --timeout 25
```

The base URL must be an HTTPS origin. A non-zero exit code means an endpoint failed, returned malformed or unexpected JSON, the health status was not `UP`, or Dublin could not be selected safely. The check is a quick deployment diagnostic, not a substitute for browser end-to-end review or long-term reliability monitoring.

Offline tests, with no network or AI calls:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts -p test_smoke_production.py
```

## 简体中文

运行低成本、只读的线上后端检查：

```sh
python3 scripts/smoke_production.py
```

默认请求超时为 65 秒，以便 Render 免费实例有时间唤醒。脚本会检查健康接口、搜索 Dublin，并且只在结果唯一时选择爱尔兰的都柏林；随后检查该地点的事实接口、全球极光地图、Kp 数据和地磁风暴预报。每个请求都会记录 HTTP 状态、JSON 结构和响应时间。脚本不会调用 AI，也不会写入数据。提供商可能在成功的 API 响应中报告数据暂不可用；本检查确认接口可访问并返回 JSON，不保证每个上游数据集此刻都有数据。

需要时可以指定其他后端地址或超时：

```sh
python3 scripts/smoke_production.py --base-url https://your-backend.example --timeout 25
```

后端地址必须是 HTTPS 网站源。退出码非零表示接口失败、JSON 格式错误或结构不符、健康状态不是 `UP`，或者脚本无法安全地唯一选择爱尔兰都柏林。这只是快速部署诊断，不能代替浏览器端到端检查或长期稳定性监控。

离线测试不会访问网络或调用 AI：

```sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts -p test_smoke_production.py
```
