/** 카카오(다음) 우편번호 서비스. 필요할 때만 스크립트를 불러옴 */
type PostcodeResult = { zonecode: string; roadAddress: string; jibunAddress: string; buildingName: string }
declare global {
  interface Window {
    daum?: { Postcode: new (options: {
      oncomplete: (data: PostcodeResult) => void
      onclose?: (state: string) => void
    }) => { open: () => void } }
  }
}
const SRC = 'https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js'
let loading: Promise<void> | null = null
function load() {
  if (window.daum?.Postcode) return Promise.resolve()
  if (loading) return loading
  loading = new Promise<void>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = SRC
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => {
      script.remove()
      loading = null
      reject(new Error('주소 검색을 불러오지 못했습니다. 주소를 직접 입력해주세요.'))
    }
    document.head.append(script)
  })
  return loading
}
/** 주소 검색 창을 열고 선택한 우편번호·도로명 주소를 돌려줌 */
export async function searchAddress(): Promise<{ postalCode: string; address: string } | null> {
  await load()
  if (!window.daum?.Postcode) throw new Error('주소 검색을 불러오지 못했습니다. 주소를 직접 입력해주세요.')
  return new Promise((resolve) => {
    let picked = false
    new window.daum!.Postcode({
      onclose: () => {
        if (!picked) resolve(null)
      },
      oncomplete: (data) => {
        picked = true
        const base = data.roadAddress || data.jibunAddress
        resolve({
          postalCode: data.zonecode,
          address: data.buildingName ? `${base} (${data.buildingName})` : base,
        })
      },
    }).open()
  })
}
