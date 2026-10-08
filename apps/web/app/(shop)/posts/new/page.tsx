'use client'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { newPost } from '@/lib/api'
import { LoadError, OperatorOnly } from '@/components/operator-only'
import { PageLoading } from '@/components/ui-kit'
export default function Page() {
  return (
    <OperatorOnly>
      <New />
    </OperatorOnly>
  )
}
function New() {
  const router = useRouter(),
    started = useRef(false),
    [error, setError] = useState('')
  const create = useCallback(async () => {
    setError('')
    try {
      const post = await newPost()
      router.replace('/posts/' + post.id + '/edit')
    } catch (e) {
      setError((e as Error).message)
    }
  }, [router])
  useEffect(() => {
    // StrictMode의 두 번 실행에도 임시글은 하나만 생성
    if (started.current) return
    started.current = true
    void create()
  }, [create])
  if (error)
    return (
      <div className="page page-narrow">
        <LoadError message={error} onRetry={() => void create()} />
      </div>
    )
  return <PageLoading label="새 글을 준비하는 중" />
}
