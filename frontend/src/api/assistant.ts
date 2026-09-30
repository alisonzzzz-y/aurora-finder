import { apiUrl } from './apiUrl'
import { fetchJson } from './fetchJson'
import type { Location } from '../types/location'

export type AssistantMessage = {
  role: 'user' | 'assistant'
  content: string
  locationCandidates?: Location[]
}

type AssistantChatResponse = {
  answer: string
  model: string
  locationCandidates: Location[]
}

export async function askAssistant(
  message: string,
  language: 'en' | 'zh',
  history: AssistantMessage[],
  locationId?: number,
): Promise<AssistantChatResponse> {
  return fetchJson<AssistantChatResponse>(apiUrl('/api/v1/assistant/chat'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, language, history: history.slice(-10).map(item => ({
      ...item, content: item.content.slice(0, 8000),
    })), locationId }),
  }, 'The AI assistant is temporarily unavailable.', 240_000)
}
