import { CmsList } from '@/components/cms-content'
export const metadata = {
  title: '자주 묻는 질문',
  description: '주문·입금·배송·교환에 대해 자주 받는 질문',
}
export default function Page() {
  return <CmsList faq />
}
