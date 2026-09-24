import type { WeatherForecast } from '../types/weatherForecast'
import { apiUrl } from './apiUrl'

export async function getWeatherForecast(latitude: number, longitude: number, timezone: string, signal?: AbortSignal) {
  const query = new URLSearchParams({ latitude: String(latitude), longitude: String(longitude), timezone })
  const response = await fetch(apiUrl(`/api/v1/weather-forecast?${query}`), { signal })
  if (!response.ok) throw new Error('Local cloud forecast is unavailable right now.')
  return (await response.json()) as WeatherForecast
}
