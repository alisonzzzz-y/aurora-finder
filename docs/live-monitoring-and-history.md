# Live monitoring and history / 实时监测与历史回看

## Data sources / 数据来源

- Regional activity uses [AuroraWatch UK API v0.2](https://aurorawatch.lancs.ac.uk/api-info/0.2/). The current alerting station can change. Its name, attribution, licence and official alert colours are preserved. Hourly values are not local viewing probabilities; the latest hour may still be incomplete. The API is cached for at least three minutes. This integration is for non-commercial use under the source licence. The provider asks API users to contact them; no email was sent by this implementation.
- 区域活动来自 AuroraWatch UK，显示当前告警测站及原始预警颜色、署名和许可。小时值不是当地观测概率，最新一小时可能尚未结束。接口至少缓存三分钟。此接入限非商业用途；提供方要求使用者联系他们，本次未代发邮件。
- Solar wind uses [NOAA real-time solar wind](https://www.spaceweather.gov/products/solar-wind): `rtsw_mag_1m.json` and `rtsw_wind_1m.json`. Only active satellite samples with numeric quality flag 0 are retained. Bz, Bt and speed have independent availability; samples older than 15 minutes are labelled stale. Gaps over five minutes are not connected. Satellite identity remains available in the chart readout.
- 太阳风只保留当前使用的卫星及质量标记正常的测量。Bz、Bt 和速度分别处理缺测，超过十五分钟标记过期，超过五分钟的间隔不连线。悬停读数包含卫星来源。
- Camera cards link to IRF Kiruna, UAF Poker Flat and the Finnish Meteorological Institute. Images and capture times are viewed on the original websites. No images are copied, and no automatic sighting claim is made.
- 摄像头卡片链接至三个机构的原站，画面和拍摄时间在原站查看。本站不复制图片，也不自动声称看到了极光。

## Public history / 公开历史

`GET /api/v1/history/{locationId}` returns city-level snapshots, never assistant conversations, run IDs or user identifiers. It uses the existing run-record JDBC configuration and Flyway V3 migration. With storage disabled it returns `DISABLED`; read failures return `UNAVAILABLE`.

公开历史仅包含城市级预报快照，不含聊天、运行记录编号或用户标识。沿用已有 JDBC 配置，启动时通过 V3 迁移建表。未启用存储与数据库故障分别显示不同状态。

Retention is seven days, with one snapshot per city per 15-minute UTC bucket. Dublin, Carlow and Reykjavik are sampled on a bounded schedule. Other cities are captured after a successful search. Service sleep and source failures can leave gaps. No historical values are fabricated for times before collection started.

保留七天，每个城市每十五分钟最多一份。三个固定城市定时采样，其他城市查询后保存。服务休眠或数据故障可能留下空白，上线前没有历史记录。

A snapshot preserves the model reading and its original status/times, the evaluated cloud value and darkness, sky-condition level, and rule version. It is not a full archive of global grids or every hourly weather value. Expired model/cloud samples are excluded from history curves. Snapshot selection and observation notes can be exported as JSON. User reports remain unverified and do not calibrate a probability automatically.

快照保留模型读数、原始状态和时间、参与判断的云量与黑暗状态、天空条件等级及规则版本，并非全球模型或完整天气序列的档案。过期数据不进入历史曲线。可导出快照及观测备注，但报告仍未经核实，不会自动校准概率。

## Verification / 验证

Backend tests cover source parsing, quality filtering, stale data, XML external-entity protection, station identity, and database restart/deduplication/retention. Frontend checks cover chart hover, snapshot switching, review export and narrow layouts. Live provider checks are separate from fixtures: fixtures verify interactions, not present-day conditions.

后端测试覆盖来源解析、质量过滤、过期状态、XML 安全、测站一致性和数据库重启、去重、清理。前端检查悬停、快照切换、导出和窄屏布局。交互样例与真实接口检查分开，不把测试数据作为当前实况。
