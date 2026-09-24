# Aurora Outlook

An early project foundation for a location-based aurora viewing assistant. This repository does **not** calculate a viewing chance or offer AI advice yet. Its map displays the latest available short-range NOAA OVATION model grid, not ground-level visibility.

## What works now

- Search for a named place through Open-Meteo's geocoding API and select one of the returned locations. The selected record supplies coordinates and an IANA time zone. The first release does not support map-pin selection or arbitrary coordinates.
- Show the selected place's current local date and the following two local dates. Date labels include the UTC offset at the start of each local date. Displayed timestamps use the place's time zone and show the offset for that instant. Every night is marked **Insufficient data** until the observation inputs and rule thresholds are validated.
- Expose a Spring Boot health endpoint and separate API endpoints for place search and the three-night response.
- Show a global NOAA OVATION map on the home page through a backend endpoint. The MapTiler basemap needs a browser key before map tiles can load; city-level viewing ratings remain unimplemented while rules are being validated.

The map overlay displays NOAA's short-range model grid, with separate observation and forecast times. It does not account for local clouds or darkness. The AI entry is explicitly unavailable.

## Run locally

Requirements: Java 21 and Node.js 22 or newer.

```sh
cd backend
./mvnw spring-boot:run
```

In another terminal:

```sh
cd frontend
npm install
npm run dev
```

Open the local URL printed by Vite. Its development proxy sends `/api` requests to Spring Boot on port 8080.

Local checks:

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

External HTTP connection timeouts are configurable with `APP_HTTP_CONNECT_TIMEOUT`; provider request timeouts use `APP_GEOCODING_REQUEST_TIMEOUT` and `APP_OVATION_REQUEST_TIMEOUT`.

The home page map uses NOAA SWPC's public OVATION grid through the backend; this feed does not require an API key. MapLibre GL JS renders the interactive map, and MapTiler supplies the basemap tiles. To enable tiles, create a MapTiler Cloud key, restrict its allowed origins to `http://localhost:5173` and your deployed site origin, copy `frontend/.env.example` to `frontend/.env.local`, and set `VITE_MAPTILER_KEY`. The key is visible in browser requests by design, so keep its allowed origins restricted. Never commit `.env.local`.

API examples:

- `GET /api/v1/locations?q=Dublin`
- `GET /api/v1/outlooks/2964574`
- `GET /actuator/health`

Set `APP_GEOCODING_ENABLED=false` to disable calls to Open-Meteo. The free Open-Meteo endpoint is limited to non-commercial use; review its terms before changing the use or deploying publicly.

## Boundaries and next work

The backend currently has `controller`, `service`, `provider`, `config`, and `dto` packages. There is no database, so no repository or entity classes have been added. The location service caches successful searches for 10 minutes and location records for one hour, with a limit of 256 entries in each cache. The provider distinguishes invalid responses, timeouts, rate limits, and other failures. Request rate control, concurrent request coalescing, and deployment checks still need implementation.

Before implementing viewing levels, complete the [Phase 0 source and rule review](docs/phase-0-data-and-rules.md). This includes MET Norway identification and cache handling, darkness calculation across polar and date-line cases, source freshness rules, and evidence-based level thresholds. A provider failure must never turn into a low viewing level.

See [API access instructions](docs/api-access.md) for provider endpoints, API key requirements, and request identity setup.

The planned output is advice for unaided-eye viewing, not a measured probability or a guarantee. Aurora model regions do not account for clouds, darkness, terrain, local light pollution, or the observer's horizon.

## Sources

- [NOAA OVATION short-range aurora forecast](https://www.spaceweather.gov/products/aurora-30-minute-forecast)
- [NOAA Kp forecast feed](https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json)
- [MET Norway Locationforecast data model](https://docs.api.met.no/doc/locationforecast/datamodel.html) and [terms](https://docs.api.met.no/doc/TermsOfService)
- [Open-Meteo geocoding documentation](https://open-meteo.com/en/docs/geocoding-api) and [terms](https://open-meteo.com/en/terms)

Location results are based on Open-Meteo geocoding data under CC BY 4.0. Credit: Open-Meteo.
