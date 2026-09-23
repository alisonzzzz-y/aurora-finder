# Phase 0: source and rule review

Review date: 2026-09-24. This is a record of a few direct samples and provider documentation, not approval of the whole data pipeline.

| Area | Confirmed so far | Still required before viewing advice |
| --- | --- | --- |
| NOAA OVATION | A direct sample returned `Observation Time`, `Forecast Time`, `Data Format`, and 65,160 `[longitude, latitude, aurora]` points. Longitudes were 0–359 and latitudes -90–90. NOAA describes this as a 30–90 minute forecast of aurora location and intensity. | Repeat samples to measure update behavior and failures. Define freshness from both timestamps. Validate coordinate wrapping and map rendering at the longitude seam, in both hemispheres. Do not treat cell intensity as a user's sighting probability. |
| NOAA Kp | A direct sample contained objects with `time_tag`, `kp`, `observed`, and `noaa_scale`. The status values included `observed`, `estimated`, and `predicted`; timestamps had no explicit offset. | Confirm the documented time zone and operational date range. Keep past observations, estimates, and future predictions separate. Kp is a geomagnetic trend, not a location-level visibility forecast. |
| MET Norway clouds | The official data model lists `cloud_area_fraction` in percent in `instant.details`, with global forecast coverage. Terms require a real identifying User-Agent, attribution, coordinate rounding to at most four decimals, and cache behavior based on response headers. | Obtain a genuine contact URL or address for the backend User-Agent. Then sample northern Europe, Ireland, and a southern location from the local machine and deployment host; inspect field availability, timestamps, cache headers, 203/403/429 behavior, and forecast gaps. No weather request has been made as part of this scaffold. |
| Place search and time zone | Open-Meteo's search response includes an ID, coordinates, country/region, and IANA time zone. A direct search for Dublin returned Ireland and several US locations, so selection must remain explicit. Its free API is non-commercial and requires CC BY 4.0 attribution. | Check southern, polar, date-line, and non-city examples; confirm desired coverage and usage model before public deployment. Add caching and rate control. Replace the provider if the use falls outside the free terms. |
| Darkness | IANA time zones can produce the three local calendar dates after a place is selected. The Apache-2.0 Java library `commons-suncalc` is a candidate for local solar-position calculations; NOAA publishes an independent solar calculator and defines astronomical twilight at a solar elevation of -18°. | Compare the candidate's solar elevations with NOAA at ordinary and high latitudes before adopting it. Define the product's darkness threshold for unaided-eye viewing; the -18° astronomy definition is not automatically the viewing rule. Test continuous daylight, continuous darkness, midnight crossings, daylight saving changes, and the date line. A sunrise/sunset-only shortcut is insufficient at high latitude. |
| Viewing levels | The product will use high, medium, low, and insufficient-data labels for unaided-eye viewing. | Establish a versioned rule from verified examples and official guidance. Define source freshness, valid forecast windows, cloud aggregation over dark hours, missing/conflicting data handling, and what each level means. Until then all API nights return `INSUFFICIENT_DATA`. No percentage probability is computed. |
| Agent | Planned tools are read-only and must share the backend's facts and rules. | Decide the model, cost limit, privacy and retention policy, tool trace storage, and evaluation cases before enabling questions. |

## Direct sample details

- The OVATION response sampled on 2026-09-24 reported observation time `2026-09-23T18:55:00Z` and forecast time `2026-09-23T20:26:00Z`. This is one snapshot, not a measured refresh interval.
- The Kp response sampled on 2026-09-24 covered `2026-09-16T00:00:00` through `2026-09-26T00:00:00`, with 62 observed, 2 estimated, and 17 predicted entries.
- Open-Meteo returned `Europe/Dublin` for Dublin, Ireland, and `America/New_York` for Dublin, Georgia. The `/v1/get?id=...` endpoint returned the selected record.
- The local shell could not resolve the provider host inside its network sandbox. Direct samples were fetched with approved network access. The locally running backend successfully searched Dublin and loaded its selected record. Backend access from a planned deployment has not been checked.

## Sources checked

- [NOAA OVATION product explanation](https://www.spaceweather.gov/products/aurora-30-minute-forecast)
- [NOAA OVATION JSON](https://services.swpc.noaa.gov/json/ovation_aurora_latest.json)
- [NOAA Kp forecast JSON](https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json)
- [MET Norway Locationforecast data model](https://docs.api.met.no/doc/locationforecast/datamodel.html)
- [MET Norway API terms](https://docs.api.met.no/doc/TermsOfService) and [licensing](https://docs.api.met.no/doc/License)
- [Open-Meteo geocoding API](https://open-meteo.com/en/docs/geocoding-api) and [terms](https://open-meteo.com/en/terms)
- [commons-suncalc Java library](https://github.com/shred/commons-suncalc) and [NOAA solar glossary](https://gml.noaa.gov/grad/solcalc/glossary.html)
