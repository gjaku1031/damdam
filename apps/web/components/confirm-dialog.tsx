'use client'
import { create } from 'zustand'
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@damdam/ui'

type ConfirmOptions = {
  title: string
  description?: string
  confirmLabel?: string
  cancelLabel?: string
  tone?: 'default' | 'danger'
}

type ConfirmState = {
  request: (ConfirmOptions & { resolve: (ok: boolean) => void }) | null
}

const useConfirmStore = create<ConfirmState>(() => ({ request: null }))

/** 브라우저 기본 confirm() 대신 쓰는 확인 창. 확인하면 true. */
export function confirmAction(options: ConfirmOptions) {
  return new Promise<boolean>((resolve) => {
    useConfirmStore.getState().request?.resolve(false)
    useConfirmStore.setState({ request: { ...options, resolve } })
  })
}

function settle(ok: boolean) {
  const { request } = useConfirmStore.getState()
  useConfirmStore.setState({ request: null })
  request?.resolve(ok)
}

/** 레이아웃에 한 번 두는 확인 창 자리 */
export function ConfirmHost() {
  const request = useConfirmStore((s) => s.request)
  return (
    <AlertDialog open={Boolean(request)} onOpenChange={(open) => !open && settle(false)}>
      <AlertDialogContent className="rounded-2xl sm:max-w-md">
        <AlertDialogHeader>
          <AlertDialogTitle className="text-lg">{request?.title}</AlertDialogTitle>
          {request?.description && (
            <AlertDialogDescription className="leading-relaxed text-muted-foreground">
              {request.description}
            </AlertDialogDescription>
          )}
        </AlertDialogHeader>
        <AlertDialogFooter className="gap-2">
          <AlertDialogCancel className="button secondary m-0">
            {request?.cancelLabel ?? '취소'}
          </AlertDialogCancel>
          <button
            type="button"
            autoFocus
            className={request?.tone === 'danger' ? 'button danger' : 'button'}
            onClick={() => settle(true)}
          >
            {request?.confirmLabel ?? '확인'}
          </button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
