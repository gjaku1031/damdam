import { test, expect } from '@playwright/test'
import { operator, createPost, saved, unique } from './helpers'

test('public pages, legacy redirects, search and Pretendard work without administrator UI', async ({
  page,
}, info) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  for (const [path, title] of [
    ['/products', '상품 목록'],
    ['/posts', '게시글'],
    ['/notices', '공지사항'],
    ['/faq', '자주 묻는 질문'],
    ['/login', '로그인'],
    ['/signup', '회원가입'],
  ]) {
    await page.goto(path)
    await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible()
    await expect(page.locator('input[type="email"]')).toHaveCount(0)
    await expect(page.getByText('Google 계정으로 로그인')).toHaveCount(0)
    await expect(page.getByRole('button', { name: /편집 모드/ })).toHaveCount(0)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(
      true,
    )
  }
  await page.goto('/community')
  await expect(page).toHaveURL(/\/posts$/)
  await page.goto('/admin/products')
  await expect(page).toHaveURL(/\/products$/)
  await page.goto('/admin/login')
  await expect(page).toHaveURL(/\/login\?next=/)
  await page.goto('/notices')
  await page.getByRole('link', { name: '검색', exact: true }).click()
  await expect(page.locator('#product-search')).toBeFocused()
  await page.locator('#product-search').fill('연필')
  await page.getByRole('button', { name: '검색', exact: true }).click()
  await expect(page).toHaveURL(/q=/)
  await page.reload()
  await expect(page.locator('#product-search')).toHaveValue('연필')
  await page.goto('/')
  await expect(page.getByRole('heading', { level: 1 })).toContainText('일상에 스며드는')
  expect(await page.locator('h1').evaluate((el) => getComputedStyle(el).fontFamily)).toContain(
    'Pretendard',
  )
  await expect(page.getByText('신규 가입 시 10% 할인 쿠폰 즉시 지급')).toHaveCount(0)
  await expect(page.locator('footer nav')).toHaveCount(0)
  await page.screenshot({ path: `test-results/home-${info.project.name}.png`, fullPage: true })
  expect(errors).toEqual([])
})

test('product API failure is visible and retry loads real products', async ({ page }) => {
  await page.route('**/api/products?**', (route) =>
    route.fulfill({
      status: 503,
      contentType: 'application/json',
      body: JSON.stringify({ message: '상품 서버 확인 중' }),
    }),
  )
  await page.goto('/products')
  await expect(page.getByText('상품 서버 확인 중')).toBeVisible()
  await expect(page.locator('main h2')).toHaveCount(0)
  await page.unroute('**/api/products?**')
  await page.getByRole('button', { name: '다시 시도' }).click()
  await expect(page.locator('main h2').first()).toBeVisible()
})

test('mobile supports basic writing, publication and reading without horizontal overflow', async ({
  page,
  isMobile,
}) => {
  test.skip(
    !isMobile || process.env.E2E_ALLOW_MUTATIONS !== 'true',
    'Runs against the isolated server on mobile.',
  )
  await operator(page)
  const { id, editor } = await createPost(page, unique('모바일'))
  await editor.fill('모바일에서 입력하고 저장한 한글 본문입니다.')
  await saved(page)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(
    true,
  )
  await page.getByRole('button', { name: '발행', exact: true }).click()
  await expect(page).toHaveURL(new RegExp('/posts/' + id + '$'))
  await expect(page.locator('article')).toContainText('모바일에서 입력하고 저장한 한글 본문입니다.')
  await page.screenshot({ path: 'test-results/mobile-post.png', fullPage: true })
  expect((await page.request.delete('/api/admin/posts/' + id)).status()).toBe(200)
})
