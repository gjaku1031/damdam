import type { JSONContent } from '@tiptap/react'
const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || '/api'
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public fields?: Record<string, string>,
  ) {
    super(message)
  }
}
export async function fetchApi<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(API_BASE + path, {
    cache: 'no-store',
    ...options,
    credentials: 'include',
    headers: {
      ...(options?.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
      ...options?.headers,
    },
  })
  if (!res.ok) {
    const error = await res.json().catch(() => null)
    throw new ApiError(
      res.status,
      Array.isArray(error?.message)
        ? error.message.join(' ')
        : error?.message || '요청을 처리하지 못했습니다. 다시 시도해주세요.',
      error?.fields,
    )
  }
  return res.json()
}
export const mutate = <T>(path: string, method: string, data?: unknown) =>
  fetchApi<T>(path, { method, ...(data === undefined ? {} : { body: JSON.stringify(data) }) })
export interface AuthUser {
  id: string
  username: string
  role: 'ADMIN' | 'CUSTOMER'
}
export async function loginWithPassword(username: string, password: string) {
  return (await mutate<{ user: AuthUser }>('/auth/login', 'POST', { username, password })).user
}
export async function registerAccount(data: {
  username: string
  password: string
  serviceTermsAccepted: boolean
  privacyTermsAccepted: boolean
}) {
  return (await mutate<{ user: AuthUser }>('/auth/register', 'POST', data)).user
}
export const fetchCurrentUser = () => fetchApi<AuthUser>('/auth/me')
/** 로그인하지 않았으면 user가 null (401 대신 200) */
export const fetchSession = () => fetchApi<{ user: AuthUser | null }>('/auth/session')
export const logoutSession = () => mutate('/auth/logout', 'POST')
export const changePassword = (currentPassword: string, newPassword: string) =>
  mutate<{ success: boolean }>('/auth/password', 'POST', { currentPassword, newPassword })
export const withdrawAccount = (password: string) =>
  mutate<{ success: boolean }>('/auth/withdraw', 'POST', { password })

/** 푸터·주문 안내에 쓰는 사업자·고객센터·입금 정보. 없는 값은 빈 문자열 */
export interface SiteInfo {
  businessName: string
  ownerName: string
  businessNumber: string
  mailOrderNumber: string
  address: string
  csPhone: string
  csHours: string
  csEmail: string
  bankInstructions: string
  /** 주문일(한국 시간)에서 이 일수 뒤의 23:59까지 입금을 기다림 */
  paymentDueDays: number
}
export const fetchSiteInfo = () => fetchApi<SiteInfo>('/site')
/** 서버가 올릴 때 만들어 둔 사진 크기. 가로 최대 s 400px, m 800px, l 1600px */
export type ImageSize = 's' | 'm' | 'l'
/**
 * 사진 키의 주소. 크기를 생략하면 서버가 대(l)를 보냄.
 * `next/image`에는 크기 없이 넘김. 이미지 로더(`lib/image-loader.ts`)가 세 벌을 후보로 넣음.
 * 크기가 고정된 작은 `<img>` 썸네일은 `'s'`를 직접 지정함.
 */
export function storageUrl(key: string, size?: ImageSize) {
  const url = key.startsWith('/images/')
    ? key
    : API_BASE + '/storage/files/' + key.replace(/^\//, '')
  return size ? `${url}?size=${size}` : url
}
export interface ApiProductVariant {
  id: string
  name: string
  priceAmount: number
  priceCurrency: string
  stock: number
  isDefault: boolean
  active: boolean
}
export interface ApiProductImage {
  id: string
  objectKey: string
  sortOrder: number
}
export interface ApiProduct {
  id: string
  name: string
  description: string | null
  category: string | null
  visible: boolean
  createdAt: string
  updatedAt: string
  images: ApiProductImage[]
  variants: ApiProductVariant[]
}
export interface ProductVariant {
  id: string
  name: string
  stock: number
  priceAmount: number
}
export interface Product {
  id: string
  name: string
  price: number
  description: string
  images: string[]
  category: string
  variants: ProductVariant[]
  active: boolean
  createdAt: string
}
export function mapApiProduct(p: ApiProduct): Product {
  const variant = p.variants.find((v) => v.isDefault) || p.variants[0]
  return {
    id: p.id,
    name: p.name,
    price: variant?.priceAmount ?? 0,
    description: p.description ?? '',
    images: [...p.images].sort((a, b) => a.sortOrder - b.sortOrder).map((i) => i.objectKey),
    category: p.category ?? '',
    variants: p.variants
      .filter((v) => v.active !== false)
      .map((v) => ({ id: v.id, name: v.name, stock: v.stock, priceAmount: v.priceAmount })),
    active: p.visible,
    createdAt: p.createdAt,
  }
}
export type ProductSort = 'newest' | 'price_asc' | 'price_desc' | 'name'
export const fetchProductCategories = () => fetchApi<string[]>('/products/categories')
export type ProductCategory = { id: string; name: string; productCount: number }
export async function fetchProducts(options?: {
  page?: number
  limit?: number
  search?: string
  category?: string
  sort?: ProductSort
}) {
  const params = new URLSearchParams(
    Object.entries(options ?? {}).map(([key, value]) => [key, String(value)]),
  )
  const data = await fetchApi<{
    products: ApiProduct[]
    total: number
    page: number
    limit: number
  }>('/products?' + params)
  return { ...data, products: data.products.map(mapApiProduct) }
}
export async function fetchProductById(id: string) {
  return mapApiProduct(await fetchApi<ApiProduct>('/products/' + id))
}
export interface ApiCmsContent {
  id: string
  type: string
  title: string
  bodyHtml: string
  visible: boolean
  authorUserId: string
  createdAt: string
  updatedAt: string
}
export const fetchNotices = (page = 1, size = 20) =>
  fetchApi<{ items: ApiCmsContent[]; total: number }>('/notices?page=' + page + '&size=' + size)
export const fetchNoticeById = (id: string) => fetchApi<ApiCmsContent>('/notices/' + id)
export const fetchFaqs = (page = 1, size = 20) =>
  fetchApi<{ items: ApiCmsContent[]; total: number }>('/faqs?page=' + page + '&size=' + size)
export async function uploadImage(file: File, domain: 'PRODUCT' | 'POST', _userId?: string) {
  if (
    !['image/jpeg', 'image/png', 'image/webp'].includes(file.type) ||
    file.size > 5 * 1024 * 1024 ||
    file.size === 0
  )
    throw new Error('JPG·PNG·WebP 사진을 5MB 이하로 선택해주세요.')
  const ticket = await mutate<{ id: string }>('/storage/tickets', 'POST', {
    domain,
    contentType: file.type,
    sizeBytes: file.size,
  })
  const form = new FormData()
  form.append('file', file)
  return (
    await fetchApi<{ objectKey: string }>('/storage/upload/' + ticket.id, {
      method: 'POST',
      body: form,
    })
  ).objectKey
}
export interface Category {
  id: string
  name: string
}
export interface PostSummary {
  id: string
  title: string
  excerpt: string
  bodyHtml?: string
  coverObjectKey: string | null
  categoryId: string | null
  category: Category | null
  tags: string[]
  commentCount: number
  createdAt: string
  updatedAt: string
  publishedAt: string | null
  state?: 'DRAFT' | 'PUBLISHED' | 'PRIVATE'
  revision?: number
}
export interface PostDraft {
  title: string
  documentJson: JSONContent
  tags: string[]
  categoryId: string | null
  coverObjectKey: string | null
}
export interface DraftResponse {
  id: string
  state: 'DRAFT' | 'PUBLISHED' | 'PRIVATE'
  revision: number
  publishedAt: string | null
  draft: PostDraft
}
export interface PostComment {
  id: string
  userId: string
  content: string
  createdAt: string
  hiddenAt: string | null
  user: { id: string; username: string }
}
export const newPost = () => mutate<DraftResponse>('/admin/posts', 'POST')
export const getDraft = (id: string) => fetchApi<DraftResponse>('/admin/posts/' + id + '/draft')
export const saveDraft = (id: string, draft: PostDraft, revision: number) =>
  mutate<DraftResponse>('/admin/posts/' + id + '/draft', 'PATCH', { ...draft, revision })
export interface PostNeighbours {
  previous: { id: string; title: string } | null
  next: { id: string; title: string } | null
}
export const fetchPostNeighbours = (id: string) =>
  fetchApi<PostNeighbours>('/posts/' + id + '/adjacent')
export const publishPost = (id: string, revision: number) =>
  mutate<DraftResponse>('/admin/posts/' + id + '/publish', 'POST', { revision })
export interface ApiOrderItem {
  id: string
  productNameSnapshot: string
  variantNameSnapshot: string
  thumbnailImageUrlSnapshot: string | null
  unitPriceAmount: number
  quantity: number
  status: string
}
export interface ApiOrder {
  id: string
  orderNo: string
  status: string
  paymentMethod: string
  totalAmount: number
  shippingCost: number
  finalPaymentAmount: number
  refundedAmount: number
  currency: string
  buyerName: string
  buyerPhone: string
  shipName: string
  shipPhone: string
  shipPostalCode: string
  shipAddress1: string
  shipAddress2: string
  shipMemo: string
  /** 입금 기한. 지나면 결제 대기 주문이 자동 취소됨. 기한 도입 전 주문은 null */
  paymentDueAt: string | null
  paidAt: string | null
  canceledAt: string | null
  refundedAt: string | null
  items: ApiOrderItem[]
  user?: AuthUser
  createdAt: string
  updatedAt: string
  statusHistories?: { id: string; toStatus: string; reason: string | null; createdAt: string }[]
}
export interface ApiUser extends AuthUser {
  communityBlockedAt: string | null
  createdAt: string
}
export interface ApiAuditLog {
  id: string
  actorUserId: string
  actorUsername: string | null
  action: string
  targetType: string
  targetId: string | null
  description: string | null
  createdAt: string
}
export interface ApiInventoryAdjustment {
  id: string
  variantId: string
  actorUserId: string | null
  delta: number
  reason: string
  createdAt: string
}
export const fetchMyOrders = () =>
  fetchApi<{ orders: ApiOrder[]; total: number }>('/orders/me?limit=100')
export const fetchOrder = (id: string) => fetchApi<ApiOrder>('/orders/' + id)
export interface CreateOrderInput {
  items: { variantId: string; quantity: number }[]
  buyerName: string
  buyerPhone: string
  shipName: string
  shipPhone: string
  shipPostalCode: string
  shipAddress1: string
  shipAddress2?: string
  shipMemo?: string
  paymentMethod: 'BANK_TRANSFER'
}
export const createOrder = (data: CreateOrderInput) => mutate<ApiOrder>('/orders', 'POST', data)
export const fetchAdminOrders = (params?: {
  page?: number
  limit?: number
  status?: string
  search?: string
}) => {
  const query = new URLSearchParams()
  query.set('limit', String(params?.limit ?? 20))
  query.set('page', String(params?.page ?? 1))
  if (params?.status) query.set('status', params.status)
  if (params?.search) query.set('search', params.search)
  return fetchApi<{ orders: ApiOrder[]; total: number }>('/admin/orders?' + query)
}
export interface AdminDashboard {
  orderCounts: Record<string, number>
  todayOrderCount: number
  todaySalesAmount: number
  monthSalesAmount: number
  lowStock: {
    productId: string
    productName: string
    variantId: string
    variantName: string
    stock: number
  }[]
  /** 입금 기한이 오늘 안에 끝나는 결제 대기 주문 수 */
  paymentDueTodayCount: number
}
export const fetchAdminDashboard = () => fetchApi<AdminDashboard>('/admin/dashboard')
export const fetchAdminOrder = (id: string) => fetchApi<ApiOrder>('/admin/orders/' + id)
export const updateAdminOrderStatus = (
  id: string,
  data: { status: string; reason?: string; restock?: boolean },
) => mutate<ApiOrder>('/admin/orders/' + id + '/status', 'PATCH', data)
export const changeAdminOrderPaymentDue = (id: string, dueAt: string) =>
  mutate<ApiOrder>('/admin/orders/' + id + '/payment-due', 'PATCH', { dueAt })
export const fetchAuditLogs = (params?: { page?: number; size?: number; target?: string }) =>
  fetchApi<{ items: ApiAuditLog[]; total: number }>(
    '/admin/system/audit-logs?page=' +
      (params?.page ?? 1) +
      '&size=' +
      (params?.size ?? 50) +
      (params?.target ? '&target=' + encodeURIComponent(params.target) : ''),
  )
export const fetchInventoryHistory = (id: string) =>
  fetchApi<{ items: ApiInventoryAdjustment[]; total: number }>(
    '/admin/inventory/variants/' + id + '/history',
  )
export const adjustInventory = (id: string, data: { delta: number; reason: string }) =>
  mutate<{ stock: number }>('/admin/inventory/variants/' + id + '/adjust', 'POST', data)
