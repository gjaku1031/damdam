import { expect, type Page, type APIRequestContext } from '@playwright/test'
import { randomBytes } from 'node:crypto'
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { join } from 'node:path'

export const password = 'Test-pass-1234!'
export const unique = (prefix = 'check') => prefix + '_' + randomBytes(5).toString('hex')
export async function chooseOption(
  page: Page,
  label: string,
  option: string | RegExp,
  index = 0,
) {
  await page.getByRole('combobox', { name: label, exact: true }).nth(index).click()
  await page.getByRole('option', { name: option, exact: typeof option === 'string' }).click()
}
export const photo = {
  name: 'wood.png',
  mimeType: 'image/png',
  buffer: Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jhRkAAAAASUVORK5CYII=',
    'base64',
  ),
}
export async function operator(page: Page) {
  if (!process.env.E2E_ADMIN_PASSWORD)
    throw new Error('Set E2E_ADMIN_PASSWORD for the isolated test server.')
  const state = join(
    '.playwright',
    'operator-' +
      new URL(process.env.E2E_BASE_URL || 'http://localhost:3000').host.replace(
        /[^\w-]/g,
        '_',
      ) +
      '.json',
  )
  try {
    await page.context().addCookies(JSON.parse(readFileSync(state, 'utf8')))
  } catch {}
  const profile = await page.request.get('/api/auth/me')
  if (profile.status() !== 200 || (await profile.json()).role !== 'ADMIN') {
    const res = await page.request.post('/api/auth/login', {
      data: {
        username: process.env.E2E_ADMIN_USERNAME || 'admin',
        password: process.env.E2E_ADMIN_PASSWORD,
      },
    })
    expect(res.status()).toBe(200)
    mkdirSync('.playwright', { recursive: true })
    writeFileSync(state, JSON.stringify(await page.context().cookies()), { mode: 0o600 })
  }
  await page.goto('/products')
  await expect(page.getByRole('button', { name: '편집 모드', exact: true })).toBeVisible()
}
export async function editMode(page: Page) {
  await page.getByRole('button', { name: '편집 모드', exact: true }).click()
  await expect(
    page.getByRole('button', { name: '편집 모드 켜짐', exact: true }),
  ).toHaveAttribute('aria-pressed', 'true')
}
export async function register(request: APIRequestContext, username = unique('member')) {
  const res = await request.post('/api/auth/register', {
    data: {
      username,
      password,
      serviceTermsAccepted: true,
      privacyTermsAccepted: true,
    },
  })
  expect(res.status()).toBe(201)
  return res.json()
}
export async function createPost(page: Page, title = unique('글')) {
  await page.goto('/posts/new')
  await page.getByRole('button', { name: '글쓰기 시작' }).click()
  await expect(page).toHaveURL(/\/posts\/[^/]+\/edit$/)
  await page.getByRole('textbox', { name: '제목', exact: true }).fill(title)
  await expect(page.locator('.tiptap')).toBeVisible()
  const id = new URL(page.url()).pathname.split('/')[2]
  return { id, title, editor: page.locator('.tiptap') }
}
export async function saved(page: Page) {
  await expect(page.getByRole('status').filter({ hasText: /^저장됨$/ })).toBeVisible({
    timeout: 20_000,
  })
}
