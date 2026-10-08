import ShopLayout from './(shop)/layout'
import NotFound from './(shop)/not-found'

/** 어느 경로에도 맞지 않는 주소도 머리글·바닥글이 있는 안내 화면으로 */
export default function GlobalNotFound() {
  return (
    <ShopLayout>
      <NotFound />
    </ShopLayout>
  )
}
