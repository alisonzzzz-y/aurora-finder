import assert from 'node:assert/strict'
import test from 'node:test'
import { displayNames, knownPlaceNames, rememberPlaceNames, storePlaceNames } from '../src/api/placeNames.ts'

const names = (name, country = '') => ({ name, region: '', subregion: '', country })

test('Chinese names are shown in Simplified characters and English ones are left alone', () => {
  assert.equal(displayNames(names('倫敦'), 'zh').name, '伦敦')
  assert.equal(displayNames(names('Tromsø'), 'en').name, 'Tromsø')
})

test('a place without a country stays empty rather than undefined in Chinese', () => {
  assert.equal(displayNames({ name: '朗伊爾城', region: null, subregion: null, country: null }, 'zh').country, '')
})

test('a search result is remembered only when it really is in the page language', () => {
  rememberPlaceNames(1, 'zh', names('Reykjavik Airport'))
  assert.equal(knownPlaceNames(1, 'zh'), undefined)
  rememberPlaceNames(2, 'zh', names('雷克雅未克'))
  assert.equal(knownPlaceNames(2, 'zh')?.name, '雷克雅未克')
  rememberPlaceNames(3, 'en', names('东京'))
  assert.equal(knownPlaceNames(3, 'en'), undefined)
})

test('names from a lookup are kept whatever their script, and per language', () => {
  storePlaceNames(4, 'zh', names('Saariselka'))
  assert.equal(knownPlaceNames(4, 'zh')?.name, 'Saariselka')
  assert.equal(knownPlaceNames(4, 'en'), undefined)
})
