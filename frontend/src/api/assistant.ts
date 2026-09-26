import { apiUrl } from './apiUrl'
import { ApiRequestError } from './requestError'
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
  const response = await fetch(apiUrl('/api/v1/assistant/chat'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, language, history: history.slice(-10), locationId }),
  })
  if (!response.ok) {
    let messageText = 'The AI assistant is temporarily unavailable.'
    try {
      const problem = await response.json() as { detail?: string }
      if (problem.detail) messageText = problem.detail
    } catch {
      // Keep the stable fallback when the server did not return problem JSON.
    }
    throw new ApiRequestError(messageText, response.status)
  }
  return response.json() as Promise<AssistantChatResponse>
}
