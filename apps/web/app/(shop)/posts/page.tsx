import { serverApi } from '@/lib/server-api'
import { PostsList } from '@/components/posts-list'
import type { Category, PostSummary } from '@/lib/api'
export const dynamic = 'force-dynamic'
export const metadata = {
  title: '작업 이야기',
  description: '담담공방의 작업 과정과 소식',
}
export default async function Page() {
  const [initial, categories] = await Promise.all([
    serverApi<{ items: PostSummary[]; total: number }>('/posts'),
    serverApi<Category[]>('/posts/categories'),
  ])
  return <PostsList initial={initial} categories={categories} />
}
