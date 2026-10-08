import { NoticeDetail } from '@/components/cms-content'
export const metadata = { title: '공지사항' }
export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  return <NoticeDetail id={id} />
}
