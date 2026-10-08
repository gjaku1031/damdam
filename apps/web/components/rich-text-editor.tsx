'use client'
import { useEditor, EditorContent, type JSONContent } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import Placeholder from '@tiptap/extension-placeholder'
import TextAlign from '@tiptap/extension-text-align'
import { useEffect, useRef, useState } from 'react'
import { BlogImage } from './blog-image'
import { EditorToolbar } from './editor-toolbar'
import { uploadImage, storageUrl } from '@/lib/api'
export function RichTextEditor({
  content,
  onChange,
  onUploadState,
  onReadyHtml,
  disabled = false,
}: {
  content: JSONContent | string
  onChange: (doc: JSONContent, html: string) => void
  onUploadState?: (blocked: boolean) => void
  onReadyHtml?: (html: string) => void
  disabled?: boolean
}) {
  const changeRef = useRef(onChange),
    statusRef = useRef(onUploadState),
    fileInput = useRef<HTMLInputElement>(null),
    uploadRef = useRef<(files: File[]) => void>(() => {})
  const uploadingRef = useRef(false)
  const [pending, setPending] = useState(0),
    [failed, setFailed] = useState<{ file: File; message: string }[]>([])
  useEffect(() => {
    changeRef.current = onChange
    statusRef.current = onUploadState
  }, [onChange, onUploadState])
  const editor = useEditor({
    immediatelyRender: false,
    extensions: [
      StarterKit.configure({ heading: { levels: [1, 2, 3] }, link: { openOnClick: false } }),
      BlogImage,
      TextAlign.configure({ types: ['heading', 'paragraph'] }),
      Placeholder.configure({
        placeholder: '이곳에 글을 써보세요. 사진을 붙여넣거나 끌어넣을 수 있습니다.',
      }),
    ],
    content,
    onUpdate: ({ editor }) => changeRef.current(editor.getJSON(), editor.getHTML()),
    editorProps: {
      attributes: {
        class: 'article-body editor-writing-area focus:outline-none',
        'aria-label': '본문',
      },
      handlePaste: (_view, event) => {
        const files = Array.from(event.clipboardData?.files ?? [])
        if (!files.length) return false
        event.preventDefault()
        uploadRef.current(files)
        return true
      },
      handleDrop: (view, event, _slice, moved) => {
        const files = Array.from(event.dataTransfer?.files ?? [])
        if (moved || !files.length) return false
        event.preventDefault()
        const pos = view.posAtCoords({ left: event.clientX, top: event.clientY })
        if (pos) editor?.commands.setTextSelection(pos.pos)
        uploadRef.current(files)
        return true
      },
    },
  })
  useEffect(() => {
    if (
      editor &&
      (typeof content === 'string'
        ? editor.getHTML() !== content
        : JSON.stringify(editor.getJSON()) !== JSON.stringify(content))
    )
      editor.commands.setContent(content, { emitUpdate: false })
  }, [editor, content])
  useEffect(() => {
    if (editor) onReadyHtml?.(editor.getHTML())
  }, [editor, onReadyHtml])
  useEffect(() => {
    editor?.setEditable(!disabled)
  }, [editor, disabled])
  useEffect(() => {
    statusRef.current?.(pending > 0 || failed.length > 0)
  }, [pending, failed])
  async function upload(files: File[]) {
    if (!editor || disabled) return
    if (uploadingRef.current) {
      setFailed((old) => [
        ...old,
        ...files.map((file) => ({
          file,
          message: '현재 사진 업로드가 끝난 뒤 재시도해주세요.',
        })),
      ])
      return
    }
    const count: number[] = []
    editor.state.doc.descendants((node) => {
      if (node.type.name === 'image') count.push(1)
    })
    if (count.length + files.length > 20) {
      setFailed((old) => [
        ...old,
        ...files.map((file) => ({ file, message: '사진은 최대 20장입니다.' })),
      ])
      return
    }
    uploadingRef.current = true
    setPending(files.length)
    statusRef.current?.(true)
    for (const file of files) {
      try {
        const key = await uploadImage(file, 'POST')
        if (editor.isDestroyed) continue
        editor
          .chain()
          .focus()
          .insertContent({
            type: 'image',
            attrs: {
              src: storageUrl(key),
              objectKey: key,
              width: 100,
              align: 'center',
              caption: '',
            },
          })
          .run()
      } catch (e) {
        setFailed((old) => [...old, { file, message: (e as Error).message }])
      } finally {
        setPending((n) => Math.max(0, n - 1))
      }
    }
    uploadingRef.current = false
  }
  useEffect(() => {
    uploadRef.current = (files) => void upload(files)
  })
  if (!editor) return <p className="p-8">에디터를 준비하는 중...</p>
  return (
    <div className="rich-text-editor">
      <EditorToolbar
        editor={editor}
        disabled={disabled || pending > 0}
        onAddPhotos={() => fileInput.current?.click()}
      />
      <input
        ref={fileInput}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        multiple
        className="hidden"
        onChange={(e) => {
          void upload(Array.from(e.target.files ?? []))
          e.target.value = ''
        }}
      />
      {pending > 0 && (
        <p role="status" className="px-6 py-3 text-sm">
          사진 업로드 중 · {pending}장 남음
        </p>
      )}
      {failed.map((item, index) => (
        <div
          key={index}
          role="alert"
          className="flex flex-wrap items-center gap-2 px-6 py-2 text-sm"
        >
          <span>
            {item.file.name}: {item.message}
          </span>
          <button
            type="button"
            className="underline"
            disabled={pending > 0 || disabled}
            onClick={() => {
              setFailed((old) => old.filter((_, i) => i !== index))
              void upload([item.file])
            }}
          >
            재시도
          </button>
          <button
            type="button"
            className="underline"
            onClick={() => setFailed((old) => old.filter((_, i) => i !== index))}
          >
            업로드 취소
          </button>
        </div>
      ))}
      <div inert={disabled || undefined}>
        <EditorContent editor={editor} />
      </div>
    </div>
  )
}
