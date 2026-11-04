import assert from 'node:assert/strict'
import test from 'node:test'
import { placeLabel } from '../src/components/location/placeLabel.ts'

test('a place without a country has no trailing comma', () => {
  assert.equal(placeLabel({ name: 'Longyearbyen', country: '' }), 'Longyearbyen')
  assert.equal(placeLabel({ name: 'Tromsø', country: 'Norway' }), 'Tromsø, Norway')
})
