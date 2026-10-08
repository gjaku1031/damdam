import { PolicyLayout } from '@/components/policy-layout'
import { PrivacyOfficer } from '@/components/cs-contact'
export const metadata = { title: '개인정보처리방침' }
export default function PrivacyPage() {
  return (
    <PolicyLayout title="개인정보처리방침" date="시행일 2026년 3월 1일">
        <p>
          담담공방(이하 &quot;회사&quot;)은 이용자의 개인정보를 중요시하며, 「개인정보 보호법」을
          준수하고 있습니다. 회사는 개인정보처리방침을 통하여 이용자가 제공하는 개인정보가 어떠한
          용도와 방식으로 이용되고 있으며, 개인정보 보호를 위해 어떠한 조치가 취해지고 있는지
          알려드립니다.
        </p>

        <section>
          <h2 className="mb-3 text-lg font-semibold text-foreground">1. 수집하는 개인정보 항목</h2>
          <p className="mb-2">회사는 서비스 제공을 위해 다음과 같은 개인정보를 수집합니다.</p>
          <ul className="list-disc space-y-1 pl-5">
            <li>회원가입: 아이디, 비밀번호(해시로 저장), 약관 동의 기록</li>
            <li>주문: 주문자·수령인 이름, 휴대전화번호, 배송지 주소</li>
            <li>자동수집항목: 접속 IP, 쿠키, 서비스 이용 기록, 접속 로그</li>
          </ul>
        </section>

        <section>
          <h2 className="mb-3 text-lg font-semibold text-foreground">
            2. 개인정보의 수집 및 이용 목적
          </h2>
          <ul className="list-disc space-y-1 pl-5">
            <li>회원 가입 및 관리: 회원제 서비스 이용에 따른 본인확인, 개인 식별</li>
            <li>상품 주문 및 배송: 주문 상품 배송, 결제 및 환불</li>
            <li>고객 서비스: 문의사항 처리, 공지사항 전달</li>
            <li>마케팅 및 광고: 이벤트 정보 제공 (동의한 경우에 한함)</li>
          </ul>
        </section>

        <section>
          <h2 className="mb-3 text-lg font-semibold text-foreground">
            3. 개인정보의 보유 및 이용 기간
          </h2>
          <p className="mb-2">
            회사는 개인정보 수집 및 이용 목적이 달성된 후에는 해당 정보를 지체 없이 파기합니다. 단,
            관련 법령에 의해 보존할 필요가 있는 경우 아래와 같이 보관합니다.
          </p>
          <ul className="list-disc space-y-1 pl-5">
            <li>계약 또는 청약 철회 기록: 5년 (전자상거래법)</li>
            <li>대금결제 및 재화 공급 기록: 5년 (전자상거래법)</li>
            <li>소비자 불만 또는 분쟁 처리 기록: 3년 (전자상거래법)</li>
            <li>접속 로그 기록: 3개월 (통신비밀보호법)</li>
          </ul>
        </section>

        <section>
          <h2 className="mb-3 text-lg font-semibold text-foreground">4. 개인정보의 제3자 제공</h2>
          <p>
            회사는 이용자의 개인정보를 원칙적으로 외부에 제공하지 않습니다. 다만, 이용자가 사전에
            동의한 경우와 법령의 규정에 의거하거나 수사기관의 요청이 있는 경우에는 예외로 합니다.
          </p>
        </section>

        <section>
          <h2 className="mb-3 text-lg font-semibold text-foreground">
            5. 개인정보의 파기 절차 및 방법
          </h2>
          <ul className="list-disc space-y-1 pl-5">
            <li>
              파기 절차: 이용자가 회원가입 등을 위해 입력한 정보는 목적이 달성된 후 별도의 DB로
              옮겨져 일정 기간 보관 후 파기됩니다.
            </li>
            <li>
              파기 방법: 전자적 파일 형태로 저장된 개인정보는 기록을 재생할 수 없는 기술적 방법을
              사용하여 삭제합니다.
            </li>
          </ul>
        </section>

        <section>
          <h2 className="mb-3 text-lg font-semibold text-foreground">
            6. 이용자의 권리와 행사 방법
          </h2>
          <ul className="list-disc space-y-1 pl-5">
            <li>이용자는 언제든지 자신의 개인정보를 조회하거나 수정할 수 있습니다.</li>
            <li>이용자는 언제든지 개인정보 수집 및 이용 동의를 철회할 수 있습니다.</li>
            <li>
              개인정보 조회, 수정, 탈퇴는 마이페이지에서 가능하며, 고객센터를 통해서도 처리
              가능합니다.
            </li>
          </ul>
        </section>

        <section>
          <h2 className="mb-3 text-lg font-semibold text-foreground">7. 개인정보 보호 책임자</h2>
          <PrivacyOfficer />
        </section>

        <div className="rounded-xl bg-muted p-4 text-sm text-muted-foreground">
          <p>시행일: 2026년 3월 1일</p>
          <p className="mt-1">
            본 방침은 시행일로부터 적용되며, 변경 시 웹사이트를 통해 공지합니다.
          </p>
        </div>
    </PolicyLayout>
  )
}
