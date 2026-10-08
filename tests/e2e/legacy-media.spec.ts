import { test, expect } from '@playwright/test'
import { operator } from './helpers'

test('legacy photo URLs check visibility and keep the original photo bytes', async ({
  page,
  browser,
}) => {
  test.skip(
    process.env.E2E_ALLOW_MUTATIONS !== 'true',
    'Requires the isolated legacy import preview.',
  )
  await operator(page)
  const posts = await (await page.request.get('/api/admin/posts?size=100')).json()
  const post = posts.items.find((p: any) => p.coverObjectKey === '/images/community-1.jpg')
  test.skip(!post, 'The test server has no legacy community photo.')
  if (post.state !== 'PUBLISHED') {
    const resumed = await page.request.post('/api/admin/posts/' + post.id + '/publish', {
      data: { revision: post.revision },
    })
    expect(resumed.status()).toBe(201)
    post.revision = (await resumed.json()).revision
  }
  const reader = await browser.newContext({ baseURL: process.env.E2E_BASE_URL })
  const first = await reader.request.get('/images/community-1.jpg')
  expect(first.status()).toBe(200)
  const bytes = await first.body()
  const hidden = await page.request.post('/api/admin/posts/' + post.id + '/hide', {
    data: { revision: post.revision },
  })
  expect(hidden.status()).toBe(201)
  const revision = (await hidden.json()).revision
  expect((await reader.request.get('/images/community-1.jpg')).status()).toBe(404)
  expect((await reader.request.get('/api/posts/' + post.id)).status()).toBe(404)
  expect((await page.request.get('/images/community-1.jpg')).status()).toBe(200)
  const restored = await page.request.post('/api/admin/posts/' + post.id + '/publish', {
    data: { revision },
  })
  expect(restored.status()).toBe(201)
  const after = await reader.request.get('/images/community-1.jpg')
  expect(after.status()).toBe(200)
  expect(await after.body()).toEqual(bytes)
  await reader.close()
})
