import { PolicyLayout } from '@/components/policy-layout'
import { CsContactInline } from '@/components/cs-contact'

export const metadata = { title: '교환·반품 안내' }

export default function ExchangePolicyPage() {
  return (
    <PolicyLayout title="교환·반품 안내" date="최종 수정일 2026년 1월 1일">
      <section>
        <h2>교환·반품 안내</h2>
        <p>
          담담공방은 고객님의 만족을 최우선으로 생각합니다. 아래 정책에 따라 교환 및 반품을
          도와드립니다.
        </p>
      </section>

      <section>
        <h2>교환·반품 가능 기간</h2>
        <div className="notice-box">
          <ul>
            <li>
              상품 수령일로부터 <strong>7일 이내</strong> 교환·반품 신청 가능
            </li>
            <li>
              상품의 하자가 있는 경우 <strong>30일 이내</strong> 교환·반품 가능
            </li>
          </ul>
        </div>
      </section>

      <section>
        <h2>교환·반품이 가능한 경우</h2>
        <ul>
          <li>상품이 표시·광고 내용과 다르거나 계약 내용과 다르게 이행된 경우</li>
          <li>배송된 상품이 주문 내용과 다른 경우</li>
          <li>배송 중 파손되거나 불량인 경우</li>
          <li>상품의 하자가 있는 경우 (자연 소재 특성에 따른 미세한 결 차이는 제외)</li>
          <li>단순 변심에 의한 경우 (미개봉, 미사용 상태에 한함)</li>
        </ul>
      </section>

      <section>
        <h2>교환·반품이 불가능한 경우</h2>
        <ul>
          <li>
            <strong>주문 제작 상품</strong>은 교환·반품이 불가합니다
          </li>
          <li>고객의 사용 또는 일부 소비에 의해 상품의 가치가 감소한 경우</li>
          <li>시간이 경과되어 재판매가 곤란할 정도로 상품의 가치가 감소한 경우</li>
          <li>포장을 개봉하였거나 포장이 훼손되어 상품가치가 상실된 경우</li>
          <li>고객의 주문에 따라 개별적으로 생산되는 상품의 경우</li>
        </ul>
      </section>

      <section>
        <h2>교환·반품 절차</h2>
        <ol>
          <li>
            <CsContactInline />를 통해 주문번호와 함께 교환·반품 신청
          </li>
          <li>담당자 확인 후 교환·반품 승인 안내 (1~2 영업일 이내)</li>
          <li>안내받은 방법에 따라 상품 반송</li>
          <li>반품 상품 도착 후 검수, 교환 상품 발송 또는 환불 처리 (도착 후 1~3 영업일)</li>
        </ol>
      </section>

      <section>
        <h2>배송비 안내</h2>
        <div className="overflow-hidden rounded-xl border border-border">
          <table className="w-full text-left text-[15px]">
            <thead>
              <tr className="border-b border-border bg-muted">
                <th scope="col" className="px-4 py-3 text-sm font-semibold text-foreground">
                  구분
                </th>
                <th scope="col" className="px-4 py-3 text-sm font-semibold text-foreground">
                  배송비 부담
                </th>
              </tr>
            </thead>
            <tbody>
              <tr className="border-b border-border">
                <td className="px-4 py-3">상품 하자·오배송</td>
                <td className="px-4 py-3">왕복 배송비 회사 부담</td>
              </tr>
              <tr>
                <td className="px-4 py-3">단순 변심</td>
                <td className="px-4 py-3">왕복 배송비 고객 부담 (5,000원)</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <h2>환불 안내</h2>
        <ul>
          <li>
            결제 수단은 무통장 입금입니다. 반품 확인 후 고객님이 알려주신 계좌로 결제 금액을
            환불합니다.
          </li>
        </ul>
      </section>

      <section>
        <h2>원목 제품 특성 안내</h2>
        <div className="notice-box">
          <p>
            천연 원목 제품은 나무의 고유한 결, 색상, 질감 등이 제품마다 조금씩 다를 수
            있습니다. 이는 자연 소재의 특성이며 불량이 아닙니다. 다음과 같은 소재의 특성에
            따른 차이는 교환·반품 사유에 해당하지 않는 점 양해 부탁드립니다.
          </p>
          <ul className="mt-2">
            <li>나뭇결, 옹이, 색상의 자연스러운 차이</li>
            <li>계절 변화에 따른 미세한 수축·팽창</li>
            <li>천연 오일 마감으로 인한 색상 변화</li>
          </ul>
        </div>
      </section>

      <section>
        <h2>문의</h2>
        <p>
          교환·반품 관련 문의는 <CsContactInline />로 연락해주세요.
        </p>
      </section>
    </PolicyLayout>
  )
}
