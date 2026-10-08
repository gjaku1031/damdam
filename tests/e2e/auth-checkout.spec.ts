import { test, expect } from '@playwright/test'
import { password, unique, register, operator, chooseOption } from './helpers'

test.skip(
  process.env.E2E_ALLOW_MUTATIONS !== 'true',
  'Account and order checks require an isolated, disposable server.',
)

test('signup validates fields, auto signs in, restores session and restricts operations', async ({
  page,
}) => {
  const username = unique('user')
  await page.goto('/signup?next=%2Fposts')
  await page.getByLabel(/^아이디/).fill('bad')
  await page.getByLabel(/^비밀번호$/).fill('short')
  await page.getByLabel('비밀번호 확인').fill('different')
  await page.getByRole('button', { name: '가입하기' }).click()
  await expect(page.getByText('영문·숫자·밑줄로 4~20자 아이디를 입력해주세요.')).toBeVisible()
  await expect(page.getByText('비밀번호가 일치하지 않습니다.')).toBeVisible()
  await page.getByLabel(/^아이디/).fill(username.toUpperCase())
  await page.locator('input[autocomplete="new-password"]').first().fill(password)
  await page.locator('input[autocomplete="new-password"]').nth(1).fill(password)
  await page.getByRole('checkbox').nth(0).check()
  await page.getByRole('checkbox').nth(1).check()
  await page.getByRole('button', { name: '가입하기' }).click()
  await expect(page).toHaveURL(/\/posts$/)
  await page.reload()
  expect((await (await page.request.get('/api/auth/me')).json()).username).toBe(username)
  await expect(page.getByRole('button', { name: /편집 모드/ })).toHaveCount(0)
  expect((await page.request.get('/api/admin/products')).status()).toBe(403)
  await page.getByRole('button', { name: 'LOGOUT' }).click()
  await expect.poll(async () => (await page.request.get('/api/auth/me')).status()).toBe(401)
  await page.goto('/signup')
  await page.getByLabel(/^아이디/).fill(username)
  await page.getByLabel(/^비밀번호$/).fill(password)
  await page.getByLabel('비밀번호 확인').fill(password)
  await page.getByRole('checkbox').nth(0).check()
  await page.getByRole('checkbox').nth(1).check()
  await page.getByRole('button', { name: '가입하기' }).click()
  await expect(page.getByText('이미 사용 중인 아이디입니다.')).toBeVisible()
  await expect(page.locator('input[autocomplete="username"]')).toHaveAttribute(
    'aria-invalid',
    'true',
  )
  await page.goto('/login?next=https%3A%2F%2Fevil.example')
  await page.getByLabel('아이디').fill(username.toUpperCase())
  await page.getByLabel('비밀번호').fill(password)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page).toHaveURL(/\/$/)
})

test('real variant prices survive cart reload and email-free checkout creates an owned order', async ({
  page,
  browser,
}) => {
  await register(page.request)
  const products = await (await page.request.get('/api/products?limit=100')).json()
  const product = products.products.find((p: any) =>
    p.variants.some((v: any) => v.active && v.stock > 2),
  )
  expect(product).toBeTruthy()
  const variant = product.variants.find((v: any) => v.active && v.stock > 2)
  await page.goto('/products/' + product.id)
  await chooseOption(page, '옵션', new RegExp('^' + variant.name + ' ·'))
  await page.getByLabel('수량').fill('2')
  await page.getByRole('button', { name: '장바구니 담기' }).click()
  await page.goto('/cart')
  await page.reload()
  await expect(page.getByText(product.name, { exact: true })).toBeVisible()
  await page.goto('/checkout')
  await expect(page.getByRole('heading', { name: '주문 / 결제' })).toBeVisible()
  await expect(page.locator('input[type="email"]')).toHaveCount(0)
  for (const [label, value] of [
    ['주문자 이름', '브라우저 고객'],
    ['주문자 전화번호', '01012345678'],
    ['받는 분 이름', '받는 고객'],
    ['받는 분 전화번호', '01087654321'],
    ['우편번호', '12345'],
    ['주소', '서울시 테스트로 10'],
    ['상세주소', '2층'],
  ])
    await page.getByLabel(new RegExp('^' + label + '( \\*)?$')).fill(value)
  await page.getByRole('button', { name: /주문 접수/ }).click()
  await expect(page).toHaveURL(/\/order-result\?orderId=/)
  const orderId = new URL(page.url()).searchParams.get('orderId')!
  const order = await (await page.request.get('/api/orders/' + orderId)).json()
  expect(order.totalAmount).toBe(variant.priceAmount * 2)
  expect(order.status).toBe('PENDING_PAYMENT')
  expect(order.buyerName).toBe('브라우저 고객')
  expect(order).not.toHaveProperty('buyerEmail')
  const stranger = await browser.newContext({ baseURL: process.env.E2E_BASE_URL })
  expect((await stranger.request.get('/api/orders/' + orderId)).status()).toBe(401)
  await stranger.close()
  await page.goto('/mypage/orders')
  await expect(page.getByText(order.orderNo)).toBeVisible()
  await page.getByText(order.orderNo).click()
  page.on('dialog', (d) => d.accept())
  await page
    .getByRole('button', { name: /주문 취소/ })
    .first()
    .click()
  await expect
    .poll(
      async () => (await (await page.request.get('/api/orders/' + orderId)).json()).status,
    )
    .toBe('CANCELED')
  const refreshed = await (await page.request.get('/api/products/' + product.id)).json()
  expect(refreshed.variants.find((v: any) => v.id === variant.id).stock).toBe(variant.stock)
})

test('operator login uses the shared screen and edit mode resets after reload', async ({
  page,
}) => {
  await operator(page)
  await page.getByRole('button', { name: '편집 모드', exact: true }).click()
  await expect(page.getByRole('button', { name: '상품 추가' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('button', { name: '편집 모드', exact: true })).toHaveAttribute(
    'aria-pressed',
    'false',
  )
  await expect(page.getByRole('button', { name: '상품 추가' })).toHaveCount(0)
  await page.goto('/mypage')
  for (const label of ['주문 관리', '회원 관리', '결제·운영 설정', '운영 기록'])
    await expect(page.getByRole('link', { name: label })).toBeVisible()
})
