import 'server-only'
import { notFound } from 'next/navigation'
export async function serverApi<T>(path: string): Promise<T> {
  const response = await fetch(
    (process.env.API_INTERNAL_URL ?? 'http://127.0.0.1:4000') + '/api' + path,
    { cache: 'no-store' },
  )
  if (response.status === 404) notFound()
  if (!response.ok) throw new Error('콘텐츠를 불러오지 못했습니다.')
  return response.json()
}
