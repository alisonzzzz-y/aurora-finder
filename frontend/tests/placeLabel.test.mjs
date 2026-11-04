import assert from 'node:assert/strict'
import test from 'node:test'
import { placeLabel, timezoneLabel } from '../src/components/location/placeLabel.ts'

test('a place without a country has no trailing comma', () => {
  assert.equal(placeLabel({ name: 'Longyearbyen', country: '' }), 'Longyearbyen')
  assert.equal(placeLabel({ name: 'Tromsø', country: 'Norway' }), 'Tromsø, Norway')
})

test('Chinese readers get a time zone name instead of an English ID', () => {
  assert.equal(timezoneLabel('Europe/Oslo', 'en'), 'Europe/Oslo')
  assert.match(timezoneLabel('Europe/Oslo', 'zh'), /\p{Script=Han}/u)
  assert.equal(timezoneLabel('Not/AZone', 'zh'), 'Not/AZone')
})
