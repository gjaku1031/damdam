#!/usr/bin/env node
// Read-only catalog/content checks and a private administrator session. No test accounts/orders.
const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const origin = process.argv[2]
const sourceFile = process.argv[3]
if (!origin || !process.env.SEED_ADMIN_PASSWORD) {
  console.error(
    'Usage: SEED_ADMIN_PASSWORD=<secret> node verify-release.cjs <origin> [export.json]',
  )
  process.exit(1)
}
let cookie = ''
async function call(path, status = 200, options = {}, authenticated = true) {
  let response
  try {
    response = await fetch(new URL(path, origin), {
      ...options,
      redirect: 'manual',
      signal: AbortSignal.timeout(15000),
      headers: {
        'Content-Type': 'application/json',
        ...(authenticated && cookie ? { Cookie: cookie } : {}),
      },
    })
    // Consume the body under the same timeout and include the failing request in diagnostics.
    response = new Response(await response.arrayBuffer(), {
      status: response.status,
      statusText: response.statusText,
      headers: response.headers,
    })
  } catch (error) {
    throw new Error(`${options.method || 'GET'} ${path}: ${error.message}`)
  }
  if (process.env.RELEASE_VERIFY_PROGRESS === 'true')
    console.log(`${options.method || 'GET'} ${path}: ${response.status}`)
  assert.equal(response.status, status, path + ' returned an unexpected status')
  return response
}
async function json(path, authenticated = true) {
  return (await call(path, 200, {}, authenticated)).json()
}
async function all(path, property, sizeKey = 'limit') {
  const rows = []
  for (let page = 1; page <= 1000; page++) {
    const result = await json(path + '?' + sizeKey + '=100&page=' + page)
    rows.push(...result[property])
    if (rows.length >= result.total) return rows
  }
  throw new Error('Unexpected pagination size: ' + path)
}
function timestamp(value) {
  const text = String(value).replace(' ', 'T')
  return new Date(/(Z|[+-]\d\d:\d\d)$/.test(text) ? text : text + 'Z').getTime()
}
function sameIdSet(actual, expected, label) {
  assert.deepEqual(
    actual.map((r) => r.id).sort(),
    expected.map((r) => r.id).sort(),
    label + ' IDs differ from the backup',
  )
}
async function main() {
  await call('/api/health')
  await call('/api/auth/me', 401, {}, false)
  for (const path of ['/api/admin/products', '/api/ADMIN/posts', '/api/admin/users'])
    await call(path, 401, {}, false)
  for (const path of ['/api/auth/google', '/api/auth/naver']) await call(path, 404, {}, false)
  const login = await call(
    '/api/auth/login',
    200,
    {
      method: 'POST',
      body: JSON.stringify({
        username: process.env.SEED_ADMIN_USERNAME || 'admin',
        password: process.env.SEED_ADMIN_PASSWORD,
      }),
    },
    false,
  )
  const setCookie = login.headers.getSetCookie().find((c) => c.startsWith('damdam.sid='))
  assert.ok(
    setCookie && /HttpOnly/i.test(setCookie) && /SameSite=Lax/i.test(setCookie),
    'Session cookie attributes missing',
  )
  if (new URL(origin).protocol === 'https:') assert.match(setCookie, /; Secure/i)
  cookie = setCookie.split(';')[0]
  const { user } = await login.json()
  assert.equal(user.role, 'ADMIN')
  assert.deepEqual(Object.keys(user).sort(), ['id', 'role', 'username'])
  assert.deepEqual(await json('/api/auth/me'), user)
  const products = await all('/api/admin/products', 'products')
  const posts = await all('/api/admin/posts', 'items', 'size')
  assert.ok(products.length, 'The preserved product catalog is empty')
  const sources = sourceFile ? JSON.parse(readFileSync(sourceFile, 'utf8')).tables : null
  if (sources) {
    const retained = (name) => (sources[name] || []).filter((row) => !row.deletedAt)
    const admins = retained('users').filter((row) => row.role === 'ADMIN')
    assert.equal(admins.length, 1)
    assert.equal(user.id, admins[0].id, 'Administrator ID changed')
    sameIdSet(products, retained('products'), 'Products')
    sameIdSet(posts, retained('admin_updates'), 'Posts')
    for (const p of products) {
      const original = retained('products').find((row) => row.id === p.id)
      assert.equal(p.name, original.name)
      assert.equal(timestamp(p.createdAt), timestamp(original.createdAt))
      const variants = (sources.product_variants || []).filter((row) => row.productId === p.id)
      sameIdSet(p.variants, variants, 'Variants')
      for (const v of p.variants) {
        const old = variants.find((row) => row.id === v.id)
        assert.equal(v.stock, Number(old.stock), 'Preserved stock differs')
        assert.equal(v.priceAmount, Number(old.priceAmount), 'Preserved variant price differs')
      }
      const images = (sources.product_images || []).filter((row) => row.productId === p.id)
      sameIdSet(p.images, images, 'Product photos')
      for (const image of p.images)
        assert.equal(image.objectKey, images.find((row) => row.id === image.id).objectKey)
    }
    for (const post of posts) {
      const original = retained('admin_updates').find((row) => row.id === post.id)
      assert.equal(post.title, original.title)
      assert.equal(timestamp(post.createdAt), timestamp(original.createdAt))
      assert.equal(timestamp(post.publishedAt), timestamp(original.createdAt))
      assert.equal(post.commentCount, 0)
      assert.equal((await json('/api/admin/posts/' + post.id + '/comments')).length, 0)
      assert.equal(
        (await json('/api/admin/posts/' + post.id + '/draft')).draft.documentJson.type,
        'doc',
      )
    }
    for (const [path, type] of [
      ['notices', 'NOTICE'],
      ['faqs', 'FAQ'],
    ]) {
      const contents = await all('/api/admin/cms/' + path, 'items', 'size')
      sameIdSet(
        contents,
        retained('cms_contents').filter((row) => row.type === type),
        'CMS ' + type,
      )
    }
    const members = await all('/api/admin/users', 'users')
    assert.equal(members.length, 1, 'Mock customers remain')
    assert.equal(members[0].id, user.id)
    assert.equal((await json('/api/admin/orders')).total, 0, 'Mock orders remain')
    const configs = await json('/api/admin/system/configs')
    for (const config of sources.system_configs || []) {
      const actual = configs.find((row) => row.configKey === config.configKey)
      assert.ok(actual, 'A preserved setting is missing')
      assert.equal(actual.configValue, config.configValue)
    }
  }
  const imageKeys = new Set(products.flatMap((p) => p.images.map((i) => i.objectKey)))
  if (sources) for (const image of sources.cms_post_images || []) imageKeys.add(image.objectKey)
  for (const key of imageKeys)
    await call(key.startsWith('/images/') ? key : '/api/storage/files/' + key)
  for (const post of posts) {
    const response = await call(
      '/api/posts/' + post.id,
      post.state === 'PUBLISHED' ? 200 : 404,
      {},
      false,
    )
    if (response.ok) {
      const item = await response.json()
      assert.ok(
        !('draft' in item) && !('revision' in item),
        'A draft leaked through the public API',
      )
    }
  }
  for (const path of ['/login', '/signup', '/products', '/posts', '/notices', '/faq'])
    await call(path)
  await call('/api/auth/logout', 200, { method: 'POST' })
  await call('/api/auth/me', 401)
  console.log(
    'Verified: administrator/session/permissions, ' +
      products.length +
      ' products, ' +
      posts.length +
      ' posts, ' +
      imageKeys.size +
      ' accessible photos' +
      (sources ? ', preserved IDs/stock/content and reset mock activity.' : '.'),
  )
}
main().catch(async (error) => {
  if (cookie)
    try {
      await call('/api/auth/logout', 200, { method: 'POST' })
    } catch {}
  console.error('Release verification failed:', error.message)
  process.exitCode = 1
})
