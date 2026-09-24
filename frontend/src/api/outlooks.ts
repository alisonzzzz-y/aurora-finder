import type { Outlook } from '../types/outlook'

export async function getOutlook(locationId: number, signal?: AbortSignal): Promise<Outlook> {
  const response = await fetch(`/api/v1/outlooks/${locationId}`, { signal })
  if (!response.ok) throw new Error('The selected location could not be loaded.')
  return (await response.json()) as Outlook
}
