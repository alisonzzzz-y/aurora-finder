import assert from 'node:assert/strict'
import test from 'node:test'
import { sortByValidAt } from '../src/utils/sortByValidAt.ts'

test('sorts forecast records chronologically without mutating the source array', () => {
  const late = { validAt: '2026-09-29T11:00:00Z', cloudCoverPercent: 80 }
  const early = { validAt: '2026-09-29T09:00:00Z', cloudCoverPercent: 20 }
  const middle = { validAt: '2026-09-29T10:00:00Z', cloudCoverPercent: 50 }
  const records = [late, early, middle]

  assert.deepEqual(sortByValidAt(records), [early, middle, late])
  assert.deepEqual(records, [late, early, middle])
})

test('keeps invalid timestamps after valid forecast records', () => {
  const invalid = { validAt: 'not-a-date' }
  const valid = { validAt: '2026-09-29T09:00:00Z' }

  assert.deepEqual(sortByValidAt([invalid, valid]), [valid, invalid])
})
