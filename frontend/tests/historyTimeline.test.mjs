import test from 'node:test'
import assert from 'node:assert/strict'
import {quarterHour,historyScale,historyDays} from '../src/components/monitoring/historyTimeline.ts'
test('quarter-hour buckets preserve gaps and handle day rollover',()=>{
 assert.equal(quarterHour('2026-10-13T23:59:00Z'),Date.parse('2026-10-13T23:45:00Z'))
 assert.notEqual(quarterHour('2026-10-13T23:59:00Z'),quarterHour('2026-10-14T00:00:00Z'))
 assert.equal(historyDays(Date.parse('2026-10-13T23:59:00Z'),Date.parse('2026-10-14T00:00:00Z')).length,2)
})
test('history scale expands low signals without hiding zero or clipping high readings',()=>{
 assert.equal(historyScale([0,13,5]),20)
 assert.equal(historyScale([0,0]),1)
 assert.equal(historyScale([0,0.2,0.8]),1)
 assert.equal(historyScale([0,1.3]),2)
 assert.equal(historyScale([3,4,5]),5)
 assert.equal(historyScale([5.1]),10)
 assert.equal(historyScale([42]),50)
 assert.equal(historyScale([99,100]),100)
 assert.equal(historyScale([NaN,-1,101]),1)
})
