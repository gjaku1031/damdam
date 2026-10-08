import { PolicyLayout } from '@/components/policy-layout'
import { CsContactInline } from '@/components/cs-contact'
export const metadata = { title: '이용약관' }
export default function TermsPage() {
  return (
    <PolicyLayout title="이용약관" date="시행일 2026년 1월 1일">
        <section>
          <h2 className="mb-3 text-lg font-semibold text-foreground">제1조 (목적)</h2>
          <p>
            이 약관은 담담공방(이하 &quot;회사&quot;)이 운영하는 온라인 쇼핑몰에서 제공하는 서비스(이하 &quot;서비스&quot;)를 이용함에 있어
            회사와 이용자의 권리, 의무 및 책임사항을 규정함을 목적으로 합니다.
          </p>
        </section>

        <section>
          <h2 className="mb-3 text-lg font-semibold text-foreground">제2조 (정의)</h2>
          <ol className="list-decimal space-y-2 pl-5">
            <li>&quot;회사&quot;란 담담공방이 재화 또는 용역을 이용자에게 제공하기 위하여 설정한 가상의 영업장을 말합니다.</li>
            <li>&quot;이용자&quot;란 본 약관에 따라 회사가 제공하는 서비스를 이용하는 회원 및 비회원을 말합니다.</li>
            <li>&quot;회원&quot;이란 회사에 개인정보를 제공하여 회원등록을 한 자로서, 회사의 서비스를 지속적으로 이용할 수 있는 자를 말합니다.</li>
          </ol>
        </section>

        <section>
          <h2 className="mb-3 text-lg font-semibold text-foreground">제3조 (약관의 효력 및 변경)</h2>
          <ol className="list-decimal space-y-2 pl-5">
            <li>본 약관은 서비스 화면에 게시하거나 기타의 방법으로 이용자에게 공지함으로써 효력이 발생합니다.</li>
            <li>회사는 관련 법령을 위반하지 않는 범위에서 본 약관을 개정할 수 있습니다.</li>
            <li>개정된 약관은 공지 후 7일이 경과한 날부터 효력이 발생합니다.</li>
          </ol>
        </section>

        <section>
          <h2 className="mb-3 text-lg font-semibold text-foreground">제4조 (서비스의 제공 및 변경)</h2>
          <ol className="list-decimal space-y-2 pl-5">
            <li>회사는 다음과 같은 서비스를 제공합니다: 목공예 제품 판매, 제품 정보 제공, 커뮤니티 서비스.</li>
            <li>회사는 서비스의 내용을 변경할 수 있으며, 변경 시 사전에 공지합니다.</li>
          </ol>
        </section>

        <section>
          <h2 className="mb-3 text-lg font-semibold text-foreground">제5조 (서비스 이용시간)</h2>
          <p>
            서비스는 연중무휴, 1일 24시간 제공함을 원칙으로 합니다. 단, 시스템 점검 등 회사가 필요한 경우
            서비스를 일시적으로 중단할 수 있으며, 이 경우 사전에 공지합니다.
          </p>
        </section>

        <section>
          <h2 className="mb-3 text-lg font-semibold text-foreground">제6조 (회원가입)</h2>
          <ol className="list-decimal space-y-2 pl-5">
            <li>이용자는 회사가 정한 가입 양식에 따라 회원정보를 기입한 후 본 약관에 동의한다는 의사표시를 함으로써 회원가입을 신청합니다.</li>
            <li>회사는 전항에 따라 회원가입을 신청한 이용자 중 다음 각 호에 해당하지 않는 한 회원으로 등록합니다.</li>
          </ol>
        </section>

        <section>
          <h2 className="mb-3 text-lg font-semibold text-foreground">제7조 (회원 탈퇴 및 자격 상실)</h2>
          <ol className="list-decimal space-y-2 pl-5">
            <li>회원은 회사에 언제든지 탈퇴를 요청할 수 있으며 회사는 즉시 회원 탈퇴를 처리합니다.</li>
            <li>회원이 다음 각 호에 해당하는 경우, 회사는 회원자격을 제한 및 정지시킬 수 있습니다.</li>
          </ol>
        </section>

        <section>
          <h2 className="mb-3 text-lg font-semibold text-foreground">제8조 (구매계약)</h2>
          <p>
            이용자는 상품 주문 시 다음의 절차에 따라 구매를 신청합니다: 상품 선택 → 배송 정보 입력 → 결제 수단 선택 → 결제.
            회사는 이용자의 구매 신청에 대해 승낙 메시지를 발송하며, 승낙 시점에 계약이 성립됩니다.
          </p>
        </section>

        <section>
          <h2 className="mb-3 text-lg font-semibold text-foreground">제9조 (면책조항)</h2>
          <p>
            회사는 천재지변, 전쟁 등 불가항력적 사유로 서비스를 제공할 수 없는 경우 책임이 면제됩니다.
            회사는 이용자의 귀책사유로 인한 서비스 이용 장애에 대하여 책임을 지지 않습니다.
          </p>
        </section>

        <div className="rounded-xl bg-muted p-4 text-sm text-muted-foreground">
          <p>시행일: 2026년 1월 1일</p>
          <p className="mt-1">본 약관에 대한 문의사항은 <CsContactInline />로 연락해주세요.</p>
        </div>
    </PolicyLayout>
  )
}
