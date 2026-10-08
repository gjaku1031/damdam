import { test, expect } from '@playwright/test'
import { operator, editMode, unique, photo, register } from './helpers'

test.skip(
  process.env.E2E_ALLOW_MUTATIONS !== 'true',
  'Operations checks require an isolated, disposable server.',
)

test('product options, price, photos, visibility and stock history save through customer pages', async ({
  page,
}) => {
  await operator(page)
  await editMode(page)
  await page.getByRole('button', { name: '상품 추가' }).click()
  const dialog = page.getByRole('dialog'),
    name = unique('상품')
  await dialog.getByLabel('상품명').fill(name)
  await dialog.getByLabel('분류', { exact: true }).fill('주방용품')
  await dialog.getByLabel('설명').fill('실제 저장과 옵션 가격을 확인하는 상품')
  await dialog.getByLabel('상품 공개').check()
  await dialog.getByLabel('가격 (원)').fill('21100')
  await dialog.getByLabel('초기 재고').fill('3')
  await dialog.getByRole('button', { name: '옵션 추가' }).click()
  await dialog.getByLabel('옵션명').nth(1).fill('큰 사이즈')
  await dialog.getByLabel('가격 (원)').nth(1).fill('25200')
  await dialog.getByLabel('초기 재고').nth(1).fill('5')
  await dialog.locator('input[type="file"]').setInputFiles(photo)
  await expect(dialog.getByAltText('상품 사진 1')).toBeVisible()
  await dialog.getByRole('button', { name: '저장', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  const rows = await (
    await page.request.get('/api/admin/products?search=' + encodeURIComponent(name))
  ).json()
  const product = rows.products[0]
  expect(product.name).toBe(name)
  expect(product.variants.map((v: any) => v.priceAmount).sort()).toEqual([21100, 25200])
  expect(product.images).toHaveLength(1)
  await page.goto('/products/' + product.id)
  await page.getByRole('button', { name: '편집 모드', exact: true }).click()
  await page.getByRole('button', { name: '상품·재고 수정' }).click()
  await dialog.getByRole('button', { name: '재고 조정·기록 보기' }).first().click()
  await dialog.getByLabel('증감 수량').fill('2')
  await dialog.getByLabel('조정 사유').fill('브라우저 재고 추가')
  await dialog.getByRole('button', { name: '조정 적용' }).click()
  await expect(dialog.getByText('현재 재고 5개')).toBeVisible()
  await expect(dialog.getByText(/브라우저 재고 추가/)).toBeVisible()
  await dialog.getByLabel('가격 (원)').first().fill('22000')
  await dialog.getByLabel('상품 공개').uncheck()
  await dialog.getByRole('button', { name: '저장', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  expect((await page.request.get('/api/products/' + product.id)).status()).toBe(404)
  const updated = await (await page.request.get('/api/admin/products/' + product.id)).json()
  expect(updated.variants[0].stock).toBe(5)
  expect(updated.variants[0].priceAmount).toBe(22000)
  expect((await page.request.delete('/api/admin/products/' + product.id)).status()).toBe(200)
})

test('notice and FAQ editor explicitly saves, preserves failed input, and supports hide and delete', async ({
  page,
}) => {
  await operator(page)
  await editMode(page)
  for (const [path, button, type] of [
    ['/notices', '공지 작성', 'notices'],
    ['/faq', '질문 추가', 'faqs'],
  ]) {
    await page
      .getByRole('link', { name: path === '/notices' ? '공지사항' : 'FAQ', exact: true })
      .first()
      .click()
    await expect(page).toHaveURL(new RegExp(path + '$'))
    await page.getByRole('button', { name: button }).click()
    const dialog = page.getByRole('dialog'),
      title = unique('안내')
    await dialog.getByLabel('제목', { exact: true }).fill(title)
    await dialog.locator('.tiptap').fill('한글 공지 내용과 링크 안내를 저장합니다.')
    await page.route('**/api/admin/cms/' + type, (route) =>
      route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ message: '저장 통신 실패' }),
      }),
    )
    await dialog.getByRole('button', { name: '저장', exact: true }).click()
    await expect(dialog.getByText('저장 통신 실패')).toBeVisible()
    await expect(dialog.getByLabel('제목', { exact: true })).toHaveValue(title)
    await page.unroute('**/api/admin/cms/' + type)
    await dialog.getByRole('button', { name: '저장', exact: true }).click()
    await expect(dialog).not.toBeVisible()
    await expect(page.getByText(title, { exact: true })).toBeVisible()
    const response = await (await page.request.get('/api/admin/cms/' + type)).json()
    const content = response.items.find((i: any) => i.title === title)
    expect(content.bodyHtml).toContain('한글 공지 내용')
    const card = page.locator('main div.rounded-lg').filter({ hasText: title }).last()
    await card.getByRole('button', { name: '숨김', exact: true }).click()
    await expect(card.getByRole('button', { name: '공개', exact: true })).toBeVisible()
    const publicRows = await (await page.request.get('/api/' + type)).json()
    expect(publicRows.items.some((i: any) => i.id === content.id)).toBe(false)
    page.once('dialog', (d) => d.accept())
    await card.getByRole('button', { name: '삭제', exact: true }).click()
    await expect(page.getByText(title, { exact: true })).toHaveCount(0)
  }
})

test('member restrictions, real bank settings and operation history work in My account', async ({
  page,
  browser,
}) => {
  const customer = await browser.newContext({ baseURL: process.env.E2E_BASE_URL })
  const registered = await register(customer.request)
  await operator(page)
  await page.goto('/mypage/members')
  await page.getByLabel('회원 아이디 검색').fill(registered.user.username)
  await page.getByRole('button', { name: '댓글 제한', exact: true }).click()
  await expect(page.getByRole('button', { name: '제한 해제' })).toBeVisible()
  const posts = await (await customer.request.get('/api/posts')).json()
  const post = posts.items[0]
  expect(
    (
      await customer.request.post('/api/posts/' + post.id + '/comments', {
        data: { content: '제한 확인' },
      })
    ).status(),
  ).toBe(403)
  await page.getByRole('button', { name: '제한 해제' }).click()
  const comment = await customer.request.post('/api/posts/' + post.id + '/comments', {
    data: { content: '작성 가능 확인' },
  })
  expect(comment.status()).toBe(201)
  expect(
    (
      await customer.request.delete(
        '/api/posts/' + post.id + '/comments/' + (await comment.json()).id,
      )
    ).status(),
  ).toBe(200)
  await page.goto('/mypage/settings')
  const bank = page.getByLabel('무통장 입금 계좌 안내')
  await bank.fill('검증은행 12345678 담담공방')
  await bank.locator('xpath=ancestor::form').getByRole('button', { name: '저장' }).click()
  await expect
    .poll(
      async () =>
        (await (await customer.request.get('/api/payments/bank-instructions')).json()).instructions,
    )
    .toBe('검증은행 12345678 담담공방')
  await page.goto('/mypage/history')
  await expect(page.getByRole('heading', { name: '운영 기록' })).toBeVisible()
  await expect(page.locator('main ul li').first()).toBeVisible()
  await customer.close()
})
