// A small Markdown subset for assistant answers. It builds a tree that React renders as elements,
// so model output is never inserted as HTML.
export type Inline =
  | { type: 'text'; text: string }
  | { type: 'break' }
  | { type: 'strong'; children: Inline[] }
  | { type: 'code'; text: string }
  | { type: 'link'; href: string; children: Inline[] }

export type Block =
  | { type: 'paragraph'; children: Inline[] }
  | { type: 'heading'; children: Inline[] }
  | { type: 'list'; ordered: boolean; items: Inline[][] }

const LIST_ITEM = /^\s*(?:([-*•])|(\d+)[.)、])\s+(.*)$/
const HEADING = /^\s{0,3}#{1,6}\s+(.*?)\s*#*\s*$/
// Bold, inline code, [text](url) links and bare web addresses. Trailing punctuation stays outside a bare link.
const INLINE = /\*\*(.+?)\*\*|`([^`\n]+)`|\[([^\]\n]+)\]\((https?:\/\/[^\s)]+)\)|(https?:\/\/[^\s<>]*[^\s<>.,;:!?)\]'"，。；：！？）】])/g

export function parseAssistantMarkdown(source: string): Block[] {
  const blocks: Block[] = []
  let paragraph: string[] = []
  let list: { ordered: boolean; items: string[] } | null = null
  const flushParagraph = () => {
    if (paragraph.length) blocks.push({ type: 'paragraph', children: parseInline(paragraph.join('\n')) })
    paragraph = []
  }
  const flushList = () => {
    if (list) blocks.push({ type: 'list', ordered: list.ordered, items: list.items.map(item => parseInline(item)) })
    list = null
  }
  for (const line of source.replace(/\r\n?/g, '\n').split('\n')) {
    const item = LIST_ITEM.exec(line)
    const heading = HEADING.exec(line)
    if (!line.trim()) {
      flushParagraph()
      flushList()
    } else if (item) {
      flushParagraph()
      const ordered = item[2] !== undefined
      if (list && list.ordered !== ordered) flushList()
      list ??= { ordered, items: [] }
      list.items.push(item[3])
    } else if (heading) {
      flushParagraph()
      flushList()
      blocks.push({ type: 'heading', children: parseInline(heading[1]) })
    } else if (list && /^\s{2,}\S/.test(line)) {
      // An indented line continues the previous list item.
      list.items[list.items.length - 1] += `\n${line.trim()}`
    } else {
      flushList()
      paragraph.push(line)
    }
  }
  flushParagraph()
  flushList()
  return blocks
}

export function parseInline(text: string): Inline[] {
  const result: Inline[] = []
  const pushText = (value: string) => {
    value.split('\n').forEach((part, index) => {
      if (index > 0) result.push({ type: 'break' })
      if (part) result.push({ type: 'text', text: part })
    })
  }
  let last = 0
  for (const match of text.matchAll(INLINE)) {
    const start = match.index ?? 0
    pushText(text.slice(last, start))
    if (match[1] !== undefined) result.push({ type: 'strong', children: parseInline(match[1]) })
    else if (match[2] !== undefined) result.push({ type: 'code', text: match[2] })
    else if (match[3] !== undefined) result.push({ type: 'link', href: match[4], children: parseInline(match[3]) })
    else result.push({ type: 'link', href: match[5], children: [{ type: 'text', text: match[5] }] })
    last = start + match[0].length
  }
  pushText(text.slice(last))
  return result
}
