import type { ReactNode } from 'react'
import { parseAssistantMarkdown, type Inline } from './parseAssistantMarkdown'

function renderInline(nodes: Inline[]): ReactNode[] {
  return nodes.map((node, index) => {
    switch (node.type) {
      case 'text': return node.text
      case 'break': return <br key={index} />
      case 'strong': return <strong key={index}>{renderInline(node.children)}</strong>
      case 'code': return <code key={index}>{node.text}</code>
      case 'link': return <a key={index} href={node.href} target="_blank" rel="noopener noreferrer">{renderInline(node.children)}</a>
    }
  })
}

export function AssistantMarkdown({ text }: { text: string }) {
  return <div className="assistant-markdown">{parseAssistantMarkdown(text).map((block, index) => {
    if (block.type === 'list') {
      const items = block.items.map((item, itemIndex) => <li key={itemIndex}>{renderInline(item)}</li>)
      return block.ordered ? <ol key={index}>{items}</ol> : <ul key={index}>{items}</ul>
    }
    if (block.type === 'heading') return <p key={index} className="assistant-markdown-heading"><strong>{renderInline(block.children)}</strong></p>
    return <p key={index}>{renderInline(block.children)}</p>
  })}</div>
}
