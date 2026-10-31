import test from 'node:test'
import assert from 'node:assert/strict'
import { distanceKm, filterNearby, commonCloudTimes, darknessAt } from '../src/components/location/nearbyMath.ts'
const origin={id:1,latitude:53.3,longitude:-6.2}
test('distance crosses the date line without taking the long route',()=>{
 assert.ok(distanceKm({latitude:0,longitude:179.9},{latitude:0,longitude:-179.9})<23)
})
test('nearby candidates exclude duplicates, origin and out-of-range matches',()=>{
  const close={id:2,latitude:53.4,longitude:-6.2}
  assert.deepEqual(filterNearby(origin,[origin,close,close,{id:3,latitude:55,longitude:-6}],25),[close])
})
const now=Date.parse('2026-10-13T12:00:00Z')
const facts=(times,status='CURRENT')=>({cloudForecast:{status,data:{expiresAt:'2026-10-13T13:00:00Z',cloudForecast:times.map(([validAt,cloudCoverPercent])=>({validAt,cloudCoverPercent}))}}})
test('comparison uses exact shared times and excludes missing or expired cloud data',()=>{
  const a=facts([['2026-10-13T13:00:00Z',0],['2026-10-13T14:00:00Z',null]])
  const b=facts([['2026-10-13T13:00:00Z',90],['2026-10-13T15:00:00Z',10]])
  assert.deepEqual(commonCloudTimes([a,b],now),[Date.parse('2026-10-13T13:00:00Z')])
  assert.deepEqual(commonCloudTimes([a,{...b,cloudForecast:{...b.cloudForecast,status:'EXPIRED'}}],now),[])
  assert.deepEqual(commonCloudTimes([a,b],now+3600000),[])
})
test('darkness checks half-open intervals and keeps unknown distinct from daylight',()=>{
  const f={outlook:{nights:[{evaluationWindowStartUtc:'2026-10-13T12:00:00Z',evaluationWindowEndUtc:'2026-10-14T12:00:00Z',solarDarkness:{thresholds:[{threshold:'NAUTICAL_TWILIGHT',status:'INTERVALS_FOUND',intervals:[{startUtc:'2026-10-13T20:00:00Z',endUtc:'2026-10-14T05:00:00Z'}]},{threshold:'ASTRONOMICAL_TWILIGHT',status:'NO_INTERVAL',intervals:[]}]}}]}}
  assert.equal(darknessAt(f,now),false)
  // Uses the same −12° rule as the sky summary, even when the Sun never reaches −18°.
  assert.equal(darknessAt(f,Date.parse('2026-10-13T20:00:00Z')),true)
  assert.equal(darknessAt(f,Date.parse('2026-10-14T05:00:00Z')),false)
  assert.equal(darknessAt(f,now+48*3600000),null)
})
