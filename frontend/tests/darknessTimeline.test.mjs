import test from 'node:test'
import assert from 'node:assert/strict'
import {localNightPosition,darknessHours} from '../src/components/outlook/darknessTimeline.ts'
const night={evaluationWindowStartUtc:'2026-10-24T11:00:00Z',evaluationWindowEndUtc:'2026-10-25T12:00:00Z'}
test('local clock alignment survives a 25-hour Dublin night',()=>{
 assert.equal(localNightPosition('2026-10-24T17:00:00Z',night,'Europe/Dublin'),6)
 assert.equal(localNightPosition('2026-10-25T06:00:00Z',night,'Europe/Dublin'),18)
 assert.equal(darknessHours([{startUtc:night.evaluationWindowStartUtc,endUtc:night.evaluationWindowEndUtc}]),25)
})
test('interval edges clip to the shared noon-to-noon axis',()=>{
 assert.equal(localNightPosition('2026-10-24T10:00:00Z',night,'Europe/Dublin'),0)
 assert.equal(localNightPosition('2026-10-25T13:00:00Z',night,'Europe/Dublin'),24)
 assert.equal(darknessHours([]),0)
 assert.equal(darknessHours([{startUtc:night.evaluationWindowEndUtc,endUtc:night.evaluationWindowStartUtc}]),0)
})
