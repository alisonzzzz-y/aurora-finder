import test from 'node:test'
import assert from 'node:assert/strict'
import { parseAssistantMarkdown, parseInline } from '../src/components/assistant/assistantMarkdown.ts'

test('bold, code and links become structured nodes instead of raw symbols', () => {
  assert.deepEqual(parseInline('**Kp 5** at `22:00`, see [NOAA](https://www.swpc.noaa.gov/).'), [
    { type: 'strong', children: [{ type: 'text', text: 'Kp 5' }] },
    { type: 'text', text: ' at ' },
    { type: 'code', text: '22:00' },
    { type: 'text', text: ', see ' },
    { type: 'link', href: 'https://www.swpc.noaa.gov/', children: [{ type: 'text', text: 'NOAA' }] },
    { type: 'text', text: '.' },
  ])
})

test('only web links are linked and trailing punctuation stays outside bare links', () => {
  assert.deepEqual(parseInline('[x](javascript:alert(1))'), [{ type: 'text', text: '[x](javascript:alert(1))' }])
  assert.deepEqual(parseInline('来源：https://api.met.no/。'), [
    { type: 'text', text: '来源：' },
    { type: 'link', href: 'https://api.met.no/', children: [{ type: 'text', text: 'https://api.met.no/' }] },
    { type: 'text', text: '。' },
  ])
})

test('lists, headings and paragraphs are grouped by line', () => {
  const blocks = parseAssistantMarkdown('### Tonight\nClouds clear later.\nStill windy.\n\n- 22:00: 40%\n- 23:00: 20%\n1. First\n2. Second')
  assert.deepEqual(blocks.map(block => block.type), ['heading', 'paragraph', 'list', 'list'])
  assert.deepEqual(blocks[1].children.map(node => node.type), ['text', 'break', 'text'])
  assert.equal(blocks[2].ordered, false)
  assert.equal(blocks[2].items.length, 2)
  assert.equal(blocks[3].ordered, true)
})

test('plain text without Markdown is unchanged', () => {
  assert.deepEqual(parseAssistantMarkdown('Kp is 3 * 2 today'), [{ type: 'paragraph', children: [{ type: 'text', text: 'Kp is 3 * 2 today' }] }])
})
