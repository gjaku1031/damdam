'use client'
import { useCallback, useEffect, useState } from 'react'
import { fetchApi } from '@/lib/api'
export function useApi<T>(path: string | null) {
  const [data, setData] = useState<T | null>(null),
    [error, setError] = useState(''),
    [loading, setLoading] = useState(Boolean(path)),
    [loadedPath, setLoadedPath] = useState<string | null>(null),
    [version, setVersion] = useState(0)
  const reload = useCallback(() => setVersion((n) => n + 1), [])
  useEffect(() => {
    if (!path) {
      setData(null)
      setLoadedPath(null)
      setLoading(false)
      setError('')
      return
    }
    const abort = new AbortController()
    setLoading(true)
    setError('')
    fetchApi<T>(path, { signal: abort.signal })
      .then((result) => {
        if (!abort.signal.aborted) {
          setData(result)
          setLoadedPath(path)
        }
      })
      .catch((e) => {
        if (!abort.signal.aborted) {
          setData(null)
          setLoadedPath(path)
          setError(e.message)
        }
      })
      .finally(() => {
        if (!abort.signal.aborted) setLoading(false)
      })
    return () => abort.abort()
  }, [path, version])
  return {
    data: loadedPath === path ? data : null,
    error: loadedPath === path ? error : '',
    loading: Boolean(path) && (loading || loadedPath !== path),
    reload,
    setData,
  }
}
