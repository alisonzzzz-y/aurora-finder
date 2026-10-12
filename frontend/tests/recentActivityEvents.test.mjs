import test from 'node:test'
import assert from 'node:assert/strict'
import { recentActivityEvents } from '../src/components/aurora/recentActivityEvents.ts'
const now = Date.parse('2026-10-12T12:00:00Z')
const point = (hour, kp, type='OBSERVED') => ({periodStart:`2026-10-11T${hour}:00:00Z`,kp,type})
test('joins adjacent storm intervals but not gaps or quiet periods',()=>{
 const result=recentActivityEvents([point('00',5),point('03',7),point('06',2),point('09',6),point('15',5)],now)
 assert.equal(result.length,3)
 assert.equal(result[2].peak,7)
 assert.equal(result[2].end,'2026-10-11T06:00:00.000Z')
})
test('never converts predictions to past events and prefers observations',()=>{
 assert.deepEqual(recentActivityEvents([point('03',7,'PREDICTED')],now),[])
 assert.deepEqual(recentActivityEvents([point('03',7,'ESTIMATED'),point('03',2)],now),[])
 assert.equal(recentActivityEvents([point('03',7,'ESTIMATED')],now)[0].estimated,true)
})
test('excludes incomplete, invalid and out-of-window periods',()=>{
 assert.deepEqual(recentActivityEvents([{...point('03',7),periodStart:'2026-10-12T12:00:00Z'}, {...point('03',7),periodStart:'2026-10-01T00:00:00Z'},point('03',10),point('03',NaN)],now),[])
})
