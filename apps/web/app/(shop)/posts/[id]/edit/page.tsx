import { PostWriter } from '@/components/post-writer'
export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  return <PostWriter id={id} />
}
