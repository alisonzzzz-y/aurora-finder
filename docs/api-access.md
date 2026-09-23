# 数据源 API 获取指南

当前规划使用的 NOAA 和 Open-Meteo 地点搜索接口不需要 API key。MET Norway 天气接口也不使用 API key，但必须发送真实、可联系的 User-Agent。不要为了这些接口创建或提交密钥。

## Open-Meteo 地点搜索与时区

地点搜索：

~~~sh
curl 'https://geocoding-api.open-meteo.com/v1/search?name=Dublin&count=10&language=en&format=json'
~~~

响应包含地点 ID、坐标、国家/地区和 IANA 时区。同名地点可能返回多个结果，应用应让用户选择。该免费接口只用于非商业用途，并按 CC BY 4.0 标注 Open-Meteo。若产品用于商业场景，应先查看其最新使用条款并申请适用的服务计划。

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

以上是公开数据 URL，不要求 API key。后端应缓存 NOAA 响应，分别保留数据观测时间、预测时间和获取时间。OVATION 网格不能解释成个人看到极光的概率。

## MET Norway 云量预报

接口：

~~~text
https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=53.3331&lon=-6.2489
~~~

请求必须设置真实 User-Agent，其中包含项目名称以及可联系的项目网址或邮箱。示例格式如下，其中网址必须替换成已经可访问的真实项目网址：

~~~sh
curl -H 'User-Agent: AuroraOutlook/0.1 (https://github.com/ACCOUNT/REPOSITORY)' \
  'https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=53.3331&lon=-6.2489'
~~~

若仓库尚未创建，可使用真实联系邮箱作为身份信息。不得伪造邮箱或网址。坐标最多保留四位小数；按响应缓存头缓存数据；展示 MET Norway 来源署名。条款不要求 API key。

## 在本项目中配置

- Open-Meteo 地点搜索地址目前在后端 app.geocoding.base-url 配置。
- 暂无提供商需要 API key，所以当前没有密钥环境变量。
- 若以后接入需要密钥的服务，只在服务端环境变量中配置；不要写进 React 前端、提交到 Git 或粘贴到公开聊天中。
- 天气接口尚未接入。完成部署身份配置、缓存和来源验证后，再添加天气 Provider。

## GitHub 身份与推送

推送 GitHub 使用 GitHub CLI 登录身份，不需要 NOAA 或天气 API key：

~~~sh
gh auth login --web
gh auth status
~~~

在浏览器完成 GitHub 登录后，再创建仓库并推送本地分支。不要把 Personal Access Token 写进远程 URL 或仓库文件。
