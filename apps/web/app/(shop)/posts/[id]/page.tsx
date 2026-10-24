import Link from 'next/link'
import { ChevronLeft, ChevronRight, MessageCircle } from 'lucide-react'
import { serverApi } from '@/lib/server-api'
import type { PostNeighbours, PostSummary } from '@/lib/api'
import { PostComments, PostEditLink } from '@/components/post-comments'
import { formatDate } from '@/lib/format-date'
export const dynamic = 'force-dynamic'
export async function generateMetadata({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  const post = await serverApi<PostSummary>('/posts/' + id)
  return {
    title: post.title,
    description: post.excerpt,
    openGraph: post.coverObjectKey
      ? {
          title: post.title,
          description: post.excerpt,
          images: ['/api/storage/files/' + post.coverObjectKey.replace(/^\//, '')],
        }
      : undefined,
  }
}
export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  const [post, neighbours] = await Promise.all([
    serverApi<PostSummary>('/posts/' + id),
    serverApi<PostNeighbours>('/posts/' + id + '/adjacent').catch(() => null),
  ])
  const published = post.publishedAt ?? post.createdAt
  return (
    <div className="page page-narrow">
      <div className="mb-8 flex items-center justify-between gap-3">
        <Link
          href="/posts"
          className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <ChevronLeft className="size-4" aria-hidden="true" />
          작업 이야기
        </Link>
        <PostEditLink id={id} />
      </div>
      <article>
        <header className="border-b border-border pb-8">
          <span className="badge badge-brand">{post.category?.name ?? '미분류'}</span>
          <h1 className="mt-4 text-[28px] font-bold leading-tight md:text-[32px]">{post.title}</h1>
          <p className="mt-4 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted-foreground">
            <time dateTime={published}>{formatDate(published)}</time>
            <a href="#comments" className="inline-flex items-center gap-1 hover:text-foreground">
              <MessageCircle className="size-4" aria-hidden="true" />
              댓글 {post.commentCount}
            </a>
          </p>
        </header>
        <div
          className="article-body mt-8 [&_figure]:max-w-full [&_img]:mx-auto [&_img]:max-h-[720px] [&_img]:w-auto [&_img]:rounded-xl [&_img]:object-contain"
          dangerouslySetInnerHTML={{ __html: post.bodyHtml ?? '' }}
        />
        {post.tags.length > 0 && (
          <ul className="mt-10 flex flex-wrap gap-2" aria-label="태그">
            {post.tags.map((tag) => (
              <li key={tag} className="badge">
                #{tag}
              </li>
            ))}
          </ul>
        )}
      </article>
      {neighbours && (neighbours.previous || neighbours.next) && (
        <nav aria-label="이전·다음 글" className="mt-12 grid gap-3 sm:grid-cols-2">
          {neighbours.previous ? (
            <Link href={'/posts/' + neighbours.previous.id} className="card group flex flex-col gap-1 p-5 hover:border-border-strong">
              <span className="inline-flex items-center gap-1 text-[13px] text-muted-foreground">
                <ChevronLeft className="size-4" aria-hidden="true" />
                이전 글
              </span>
              <span className="line-clamp-2 font-semibold group-hover:text-primary">
                {neighbours.previous.title}
              </span>
            </Link>
          ) : (
            <span className="hidden sm:block" />
          )}
          {neighbours.next && (
            <Link href={'/posts/' + neighbours.next.id} className="card group flex flex-col items-end gap-1 p-5 text-right hover:border-border-strong">
              <span className="inline-flex items-center gap-1 text-[13px] text-muted-foreground">
                다음 글
                <ChevronRight className="size-4" aria-hidden="true" />
              </span>
              <span className="line-clamp-2 font-semibold group-hover:text-primary">
                {neighbours.next.title}
              </span>
            </Link>
          )}
        </nav>
      )}
      <PostComments id={id} />
    </div>
  )
}
