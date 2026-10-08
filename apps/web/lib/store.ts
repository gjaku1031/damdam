'use client'
import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { CartItem } from './data'
import { loginWithPassword, logoutSession, fetchSession, type AuthUser } from './api'
interface CartStore {
  ownerId: string | null
  linkUser: (id: string) => void
  items: CartItem[]
  addItem: (item: CartItem) => void
  removeItem: (productId: string, variantId: string) => void
  updateQuantity: (productId: string, variantId: string, quantity: number) => void
  clearCart: () => void
  getItemCount: () => number
}
export const useCartStore = create<CartStore>()(
  persist(
    (set, get) => ({
      ownerId: null,
      // 비로그인 때 담은 상품은 로그인한 계정으로 옮기고, 다른 계정의 장바구니는 비움
      linkUser: (id) =>
        set((state) => ({
          ownerId: id,
          items: state.ownerId === id || state.ownerId === null ? state.items : [],
        })),
      items: [],
      addItem: (item) =>
        set((state) => ({
          items: state.items.some(
            (i) => i.productId === item.productId && i.variantId === item.variantId,
          )
            ? state.items.map((i) =>
                i.productId === item.productId && i.variantId === item.variantId
                  ? { ...i, quantity: i.quantity + item.quantity }
                  : i,
              )
            : [...state.items, item],
        })),
      removeItem: (productId, variantId) =>
        set((state) => ({
          items: state.items.filter((i) => i.productId !== productId || i.variantId !== variantId),
        })),
      updateQuantity: (productId, variantId, quantity) =>
        set((state) => ({
          items: state.items.map((i) =>
            i.productId === productId && i.variantId === variantId
              ? { ...i, quantity: Math.max(1, quantity) }
              : i,
          ),
        })),
      clearCart: () => set({ items: [] }),
      getItemCount: () => get().items.reduce((n, i) => n + i.quantity, 0),
    }),
    { name: 'damdam-cart-v2' },
  ),
)
interface AuthStore {
  user: AuthUser | null
  ready: boolean
  isLoggedIn: boolean
  userName: string | null
  editMode: boolean
  login: (username: string, password: string) => Promise<void>
  acceptUser: (user: AuthUser) => void
  logout: () => Promise<void>
  clearUser: () => void
  restoreSession: () => Promise<void>
  toggleEditMode: () => void
}
let restoring: Promise<void> | null = null
export const useAuthStore = create<AuthStore>((set, get) => ({
  user: null,
  ready: false,
  isLoggedIn: false,
  userName: null,
  editMode: false,
  acceptUser: (user) => {
    useCartStore.getState().linkUser(user.id)
    set({ user, ready: true, isLoggedIn: true, userName: user.username, editMode: false })
  },
  login: async (username, password) =>
    get().acceptUser(await loginWithPassword(username, password)),
  logout: async () => {
    await logoutSession()
    get().clearUser()
  },
  clearUser: () => {
    useCartStore.setState({ ownerId: null, items: [] })
    set({ user: null, ready: true, isLoggedIn: false, userName: null, editMode: false })
  },
  restoreSession: async () => {
    if (get().ready) return
    if (restoring) return restoring
    restoring = (async () => {
      try {
        const { user } = await fetchSession()
        if (user) get().acceptUser(user)
        else set({ user: null, ready: true, isLoggedIn: false, userName: null, editMode: false })
      } catch {
        set({ user: null, ready: true, isLoggedIn: false, userName: null, editMode: false })
      } finally {
        restoring = null
      }
    })()
    return restoring
  },
  toggleEditMode: () =>
    set((state) => ({ editMode: state.user?.role === 'ADMIN' ? !state.editMode : false })),
}))
