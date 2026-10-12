import test from 'node:test'
import assert from 'node:assert/strict'
import { activityAreaName, activityCoordinates, activityPointKey } from '../src/components/aurora/activityAreaNames.ts'
const feature = (type, text, context) => ({ place_type: [type], text, context })

test('names containing country and region without presenting a municipal centre as the model point', () => {
  const features = [feature('country', '俄罗斯'), feature('region', '远东联邦管区'),
    feature('subregion', '萨哈共和国'), feature('municipality', 'Remote settlement')]
  assert.equal(activityAreaName({ features }, 'zh'), '俄罗斯 · 萨哈共和国一带')
  assert.equal(activityAreaName({ features: [feature('region', 'Troms', [{ id: 'country.1', text: 'Norway' }])] }, 'en'), 'Norway · Troms area')
})

test('keeps ocean and Antarctic labels instead of inventing nearby cities', () => {
  assert.equal(activityAreaName({ features: [feature('continental_marine', '南冰洋/南極海')] }, 'zh'), '南冰洋')
  assert.equal(activityAreaName({ features: [feature('continental_marine', 'Antarctica')] }, 'en'), 'Antarctica')
  assert.equal(activityAreaName({ features: [feature('municipality', 'Distant city')] }, 'en'), null)
})

test('handles country-only, duplicate regions and unavailable names', () => {
  assert.equal(activityAreaName({ features: [feature('country', 'Iceland'), feature('region', 'Iceland')] }, 'en'), 'Iceland')
  assert.equal(activityAreaName({ features: [feature('country', 'Canada')] }, 'en'), 'Canada')
  assert.equal(activityAreaName({ features: [] }, 'zh'), null)
  assert.equal(activityAreaName({}, 'en'), null)
  assert.equal(activityAreaName({ features: [null, feature('region', '')] }, 'en'), null)
})

test('retains the original coordinate key and tooltip for map navigation', () => {
  const point = { latitude: -64, longitude: 53 }
  assert.equal(activityPointKey(point), '53,-64')
  assert.equal(activityCoordinates(point), '64.0° S · 53.0° E')
  assert.equal(activityCoordinates({ latitude: 71, longitude: -122 }), '71.0° N · 122.0° W')
})
