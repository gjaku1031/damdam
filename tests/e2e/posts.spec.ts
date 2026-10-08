import { test, expect } from '@playwright/test'
import {
  operator,
  editMode,
  createPost,
  saved,
  unique,
  photo,
  register,
  chooseOption,
} from './helpers'

test.skip(
  process.env.E2E_ALLOW_MUTATIONS !== 'true',
  'Draft and publish checks require an isolated, disposable server.',
)

test('editor keeps text selection for formatting and links, supports keyboard menus and fits mobile', async ({
  page,
}) => {
  await operator(page)
  const { id, editor } = await createPost(page, unique('서식'))
  await editor.fill('나무의 결을 담은 이야기')
  await editor.press('ControlOrMeta+a')
  await page.getByRole('button', { name: '굵게', exact: true }).click()
  await expect(editor.locator('strong')).toHaveText('나무의 결을 담은 이야기')
  await expect(page.getByRole('button', { name: '굵게', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  )
  await chooseOption(page, '문단 스타일', '제목 2')
  await expect(editor.locator('h2')).toHaveText('나무의 결을 담은 이야기')
  await editor.press('ControlOrMeta+a')
  await page.getByRole('button', { name: '링크', exact: true }).click()
  await page.getByLabel('링크 주소').fill('javascript:alert(1)')
  await page.getByRole('button', { name: '적용', exact: true }).click()
  await expect(page.getByRole('alert').filter({ hasText: '올바른 주소' })).toBeVisible()
  await page.getByLabel('링크 주소').fill('https://damdamwood.co.kr/products')
  await page.getByRole('button', { name: '적용', exact: true }).click()
  await expect(editor.locator('a')).toHaveAttribute(
    'href',
    'https://damdamwood.co.kr/products',
  )
  await expect(editor.locator('a')).toHaveText('나무의 결을 담은 이야기')
  await saved(page)
  await page.screenshot({ path: 'test-results/editor-desktop.png', fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  const menu = page.getByRole('combobox', { name: '문단 스타일', exact: true })
  await menu.focus()
  await menu.press('Space')
  await expect(page.getByRole('listbox')).toBeVisible()
  await page.keyboard.press('Home')
  await page.keyboard.press('Enter')
  await expect(menu).toContainText('본문')
  await expect(editor.locator('p strong a, p a strong')).toHaveText('나무의 결을 담은 이야기')
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
  ).toBe(true)
  await page.screenshot({ path: 'test-results/editor-mobile.png', fullPage: true })
  await saved(page)
  expect((await page.request.delete('/api/admin/posts/' + id)).status()).toBe(200)
})

test('Korean input, undo, paste, images, categories, preview, draft isolation and real comments', async ({
  page,
  browser,
}) => {
  await operator(page)
  await editMode(page)
  const { id, title, editor } = await createPost(page)
  await editor.fill('처음 한글 본문')
  await page.waitForTimeout(600) // Tiptap groups consecutive typing into a single undo event.
  await editor.press('End')
  await page.keyboard.insertText(' 한글 추가')
  await page.getByRole('button', { name: '실행 취소', exact: true }).click()
  await expect(editor).toHaveText('처음 한글 본문')
  await page.getByRole('button', { name: '다시 실행', exact: true }).click()
  await expect(editor).toContainText('한글 추가')
  await editor.dispatchEvent('compositionstart', { data: '' })
  await page.keyboard.insertText(' 조합 입력')
  await editor.dispatchEvent('compositionend', { data: '조합 입력' })
  await editor.evaluate((element) => {
    const data = new DataTransfer()
    data.setData('text/html', '<p><strong>붙여넣은 한글</strong></p>')
    element.dispatchEvent(
      new ClipboardEvent('paste', { clipboardData: data, bubbles: true, cancelable: true }),
    )
  })
  await expect(editor.locator('strong')).toContainText('붙여넣은 한글')
  await page.locator('input[type="file"]').setInputFiles(photo)
  await expect(page.getByLabel('사진 설명')).toBeVisible()
  await page.getByLabel('사진 설명').fill('원목 작업 사진')
  await chooseOption(page, '사진 크기', '50%')
  await chooseOption(page, '사진 정렬', '오른쪽')
  await page.getByRole('button', { name: '사진 위로' }).click()
  page.once('dialog', (d) => d.accept('목공 작업'))
  await page.getByRole('button', { name: '분류 추가' }).click()
  await page.getByLabel('태그 (쉼표로 구분)').fill('원목, 제작')
  await chooseOption(page, '대표 이미지', /사진 1/)
  await saved(page)
  const draft = await (await page.request.get('/api/admin/posts/' + id + '/draft')).json()
  const image = draft.draft.documentJson.content.find((n: any) => n.type === 'image')
  expect(image.attrs).toMatchObject({ caption: '원목 작업 사진', width: 50, align: 'right' })
  expect((await page.request.get('/api/posts/' + id)).status()).toBe(404)
  const reader = await browser.newContext({ baseURL: process.env.E2E_BASE_URL })
  expect(
    (await reader.request.get('/api/storage/files/' + image.attrs.objectKey)).status(),
  ).toBe(404)
  await page.getByRole('button', { name: '미리보기', exact: true }).click()
  await expect(page.locator('article figcaption')).toHaveText('원목 작업 사진')
  await page.screenshot({ path: 'test-results/post-preview.png', fullPage: true })
  await page.getByRole('button', { name: '발행', exact: true }).click()
  await expect(page).toHaveURL(new RegExp('/posts/' + id + '$'))
  const first = await (await reader.request.get('/api/posts/' + id)).json()
  expect(first.title).toBe(title)
  expect(first.bodyHtml).toContain('붙여넣은 한글')
  expect(
    (await reader.request.get('/api/storage/files/' + image.attrs.objectKey)).status(),
  ).toBe(200)
  await page.goto('/posts/' + id + '/edit')
  await page
    .locator('.tiptap figure')
    .getByRole('button', { name: '삭제', exact: true })
    .click()
  await editor.fill('발행 전 새 초안')
  await saved(page)
  const unchanged = await (await reader.request.get('/api/posts/' + id)).json()
  expect(unchanged.bodyHtml).toBe(first.bodyHtml)
  expect(unchanged.updatedAt).toBe(first.updatedAt)
  expect(
    (await reader.request.get('/api/storage/files/' + image.attrs.objectKey)).status(),
  ).toBe(200)
  await page.getByRole('button', { name: '발행본 업데이트' }).click()
  await expect(page).toHaveURL(new RegExp('/posts/' + id + '$'))
  const published = await (await reader.request.get('/api/posts/' + id)).json()
  expect(published.bodyHtml).toContain('발행 전 새 초안')
  expect(published.publishedAt).toBe(first.publishedAt)
  const customer = await register(reader.request)
  const readerPage = await reader.newPage()
  await readerPage.goto('/posts/' + id)
  await readerPage.getByLabel('댓글 내용').fill('아이디로 작성한 실제 댓글')
  await readerPage.getByRole('button', { name: '댓글 등록' }).click()
  await expect(readerPage.getByText('아이디로 작성한 실제 댓글')).toBeVisible()
  await expect(
    readerPage.locator('section li').getByText(customer.user.username, { exact: true }),
  ).toBeVisible()
  readerPage.once('dialog', (d) => d.accept())
  await readerPage.getByRole('button', { name: '삭제', exact: true }).click()
  await expect(readerPage.getByText('아이디로 작성한 실제 댓글')).toHaveCount(0)
  expect((await page.request.delete('/api/admin/posts/' + id)).status()).toBe(200)
  await reader.close()
})

test('failed upload can retry, failed autosave recovers locally, and a real revision conflict preserves writing', async ({
  page,
  context,
}) => {
  await operator(page)
  const { id, editor } = await createPost(page, unique('복구'))
  await page.route('**/api/storage/upload/**', (route) =>
    route.fulfill({
      status: 503,
      contentType: 'application/json',
      body: JSON.stringify({ message: '사진 업로드 통신 실패' }),
    }),
  )
  await page.locator('input[type="file"]').setInputFiles(photo)
  await expect(
    page.getByRole('alert').filter({ hasText: '사진 업로드 통신 실패' }),
  ).toBeVisible()
  await expect(page.getByRole('button', { name: '발행', exact: true })).toBeDisabled()
  await page.unroute('**/api/storage/upload/**')
  await page.getByRole('button', { name: '재시도', exact: true }).click()
  await expect(page.getByLabel('사진 설명')).toBeVisible()
  await saved(page)
  await page.route('**/api/admin/posts/' + id + '/draft', (route) =>
    route.request().method() === 'PATCH'
      ? route.fulfill({
          status: 503,
          contentType: 'application/json',
          body: JSON.stringify({ message: '초안 저장 통신 실패' }),
        })
      : route.continue(),
  )
  await editor.fill('통신이 끊겨도 남아 있어야 할 작성 내용')
  await expect(page.getByText('초안 저장 통신 실패')).toBeVisible()
  await expect(page.getByRole('button', { name: '발행', exact: true })).toBeDisabled()
  await expect(page).toHaveURL(/\/edit$/)
  expect((await page.request.get('/api/posts/' + id)).status()).toBe(404)
  page.on('dialog', (d) => d.accept())
  await page.reload()
  await page.getByRole('button', { name: '복구하기' }).click()
  await expect(editor).toContainText('통신이 끊겨도 남아 있어야 할 작성 내용')
  await page.unroute('**/api/admin/posts/' + id + '/draft')
  await page.getByRole('button', { name: '임시저장', exact: true }).click()
  await saved(page)
  const second = await context.newPage()
  await second.goto('/posts/' + id + '/edit')
  await expect(second.locator('.tiptap')).toBeVisible()
  await second.locator('.tiptap').fill('다른 탭에서 저장한 내용')
  await saved(second)
  await editor.fill('충돌한 탭에 남아 있는 내용')
  await expect(page.getByRole('button', { name: '서버 내용 다시 불러오기' })).toBeVisible()
  await expect(page.getByRole('button', { name: '발행', exact: true })).toBeDisabled()
  await expect(editor).toContainText('충돌한 탭에 남아 있는 내용')
  const recoveries = await page.evaluate(() =>
    Object.keys(localStorage)
      .filter((k) => k.startsWith('damdam:draft:'))
      .map((k) => localStorage.getItem(k)),
  )
  expect(recoveries.some((v) => v?.includes('충돌한 탭에 남아 있는 내용'))).toBe(true)
  expect((await page.request.get('/api/posts/' + id)).status()).toBe(404)
  await second.close()
  expect((await page.request.delete('/api/admin/posts/' + id)).status()).toBe(200)
})

test('photo paste and file drop upload at the cursor and preserve their arrangement after reopening', async ({
  page,
}) => {
  await operator(page)
  const { id, editor } = await createPost(page, unique('사진'))
  await editor.fill('사진을 넣는 위치')
  await editor.press('End')
  await editor.evaluate((element, base64) => {
    const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0)),
      data = new DataTransfer()
    data.items.add(new File([bytes], 'pasted.png', { type: 'image/png' }))
    element.dispatchEvent(
      new ClipboardEvent('paste', { clipboardData: data, bubbles: true, cancelable: true }),
    )
  }, photo.buffer.toString('base64'))
  await expect(page.getByLabel('사진 설명')).toHaveCount(1)
  await editor.evaluate((element, base64) => {
    const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0)),
      data = new DataTransfer()
    data.items.add(new File([bytes], 'dropped.png', { type: 'image/png' }))
    const box = element.getBoundingClientRect()
    element.dispatchEvent(
      new DragEvent('drop', {
        dataTransfer: data,
        clientX: box.x + 20,
        clientY: box.y + 20,
        bubbles: true,
        cancelable: true,
      }),
    )
  }, photo.buffer.toString('base64'))
  await expect(page.getByLabel('사진 설명')).toHaveCount(2)
  await page.getByLabel('사진 설명').nth(0).fill('첫 사진')
  await page.getByLabel('사진 설명').nth(1).fill('두 번째 사진')
  await chooseOption(page, '사진 크기', '75%', 0)
  await page.getByRole('button', { name: '사진 아래로' }).nth(0).click()
  await saved(page)
  const snapshot = await (await page.request.get('/api/admin/posts/' + id + '/draft')).json()
  await page.reload()
  await expect(page.getByLabel('사진 설명')).toHaveCount(2)
  const reopened = await (await page.request.get('/api/admin/posts/' + id + '/draft')).json()
  expect(reopened.draft.documentJson).toEqual(snapshot.draft.documentJson)
  expect((await page.request.delete('/api/admin/posts/' + id)).status()).toBe(200)
})
