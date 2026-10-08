'use client'

import { useRef, useState, type ReactNode } from 'react'
import { useEditorState, type Editor } from '@tiptap/react'
import {
  AlignLeft,
  AlignCenter,
  AlignRight,
  Bold,
  Italic,
  Underline,
  Strikethrough,
  List,
  ListOrdered,
  Quote,
  Minus,
  ImagePlus,
  Link2,
  Undo2,
  Redo2,
  Unlink,
} from 'lucide-react'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@damdam/ui'
import { AppSelect } from './app-select'

function Tool({
  label,
  shortcut,
  active,
  disabled,
  onClick,
  children,
  text = false,
}: {
  label: string
  shortcut?: string
  active?: boolean
  disabled?: boolean
  onClick: () => void
  children: ReactNode
  text?: boolean
}) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          type="button"
          aria-label={label}
          aria-pressed={active}
          disabled={disabled}
          className={`editor-tool ${text ? 'editor-tool-text' : ''}`}
          onMouseDown={(event) => event.preventDefault()}
          onClick={onClick}
        >
          {children}
          {text && <span>{label}</span>}
        </button>
      </TooltipTrigger>
      <TooltipContent side="bottom" sideOffset={8}>
        {label}
        {shortcut && <span className="ml-2 opacity-60">{shortcut}</span>}
      </TooltipContent>
    </Tooltip>
  )
}

export function EditorToolbar({
  editor,
  disabled,
  onAddPhotos,
}: {
  editor: Editor
  disabled: boolean
  onAddPhotos: () => void
}) {
  const state = useEditorState({
    editor,
    selector: ({ editor: e }) => ({
      bold: e.isActive('bold'),
      italic: e.isActive('italic'),
      underline: e.isActive('underline'),
      strike: e.isActive('strike'),
      bullet: e.isActive('bulletList'),
      ordered: e.isActive('orderedList'),
      quote: e.isActive('blockquote'),
      link: e.isActive('link'),
      heading: e.isActive('heading')
        ? String(e.getAttributes('heading').level)
        : e.isActive('paragraph')
          ? 'paragraph'
          : 'mixed',
      align: e.isActive({ textAlign: 'right' })
        ? 'right'
        : e.isActive({ textAlign: 'center' })
          ? 'center'
          : 'left',
      undo: e.can().undo(),
      redo: e.can().redo(),
    }),
  })
  const [linkOpen, setLinkOpen] = useState(false)
  const [url, setUrl] = useState('')
  const [linkError, setLinkError] = useState('')
  const selection = useRef({ from: 0, to: 0 })
  function applyLink(remove = false) {
    const href = url.trim()
    if (!remove) {
      try {
        if (!['http:', 'https:'].includes(new URL(href).protocol)) throw new Error()
      } catch {
        setLinkError('https://로 시작하는 올바른 주소를 입력해주세요.')
        return
      }
    }
    const chain = editor
      .chain()
      .focus()
      .setTextSelection(selection.current)
      .extendMarkRange('link')
    if (remove) chain.unsetLink().run()
    else if (selection.current.from === selection.current.to && !state.link)
      chain
        .insertContent({
          type: 'text',
          text: href,
          marks: [{ type: 'link', attrs: { href } }],
        })
        .run()
    else chain.setLink({ href }).run()
    setLinkOpen(false)
  }
  return (
    <div
      className="editor-toolbar"
      role="toolbar"
      aria-label="본문 편집 도구"
      onKeyDown={(event) => {
        if (linkOpen || !['ArrowLeft', 'ArrowRight'].includes(event.key)) return
        const target = event.target as HTMLElement
        if (target.tagName !== 'BUTTON' || target.getAttribute('aria-expanded') === 'true')
          return
        const tools = Array.from(
          event.currentTarget.querySelectorAll<HTMLButtonElement>('button:not(:disabled)'),
        )
        const index = tools.indexOf(target as HTMLButtonElement)
        if (index < 0) return
        event.preventDefault()
        tools[
          (index + (event.key === 'ArrowRight' ? 1 : -1) + tools.length) % tools.length
        ]?.focus()
      }}
    >
      <div className="editor-insert-row">
        <div className="editor-tool-group" role="group" aria-label="콘텐츠 삽입">
          <Tool label="사진" text disabled={disabled} onClick={onAddPhotos}>
            <ImagePlus />
          </Tool>
          <Tool
            label="인용"
            text
            active={state.quote}
            disabled={disabled}
            onClick={() => editor.chain().focus().toggleBlockquote().run()}
          >
            <Quote />
          </Tool>
          <Tool
            label="구분선"
            text
            disabled={disabled}
            onClick={() => editor.chain().focus().setHorizontalRule().run()}
          >
            <Minus />
          </Tool>
          <Popover
            open={linkOpen}
            onOpenChange={(open) => {
              if (open) {
                selection.current = {
                  from: editor.state.selection.from,
                  to: editor.state.selection.to,
                }
                setUrl(editor.getAttributes('link').href || '')
                setLinkError('')
              }
              setLinkOpen(open)
            }}
          >
            <PopoverTrigger asChild>
              <button
                type="button"
                className="editor-tool editor-tool-text"
                aria-label="링크"
                aria-pressed={state.link}
                onMouseDown={(event) => event.preventDefault()}
                disabled={disabled}
              >
                <Link2 />
                <span>링크</span>
              </button>
            </PopoverTrigger>
            <PopoverContent
              align="start"
              className="w-[min(340px,calc(100vw-32px))] rounded-xl border-border p-4 shadow-lg"
              onCloseAutoFocus={(event) => event.preventDefault()}
            >
              <label className="block text-sm font-medium">
                링크 주소
                <input
                  className="control mt-2 text-sm"
                  value={url}
                  placeholder="https://example.com"
                  type="url"
                  onChange={(event) => {
                    setUrl(event.target.value)
                    setLinkError('')
                  }}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') {
                      event.preventDefault()
                      applyLink()
                    }
                  }}
                />
              </label>
              {linkError && (
                <p role="alert" className="field-error">
                  {linkError}
                </p>
              )}
              <div className="mt-3 flex justify-between gap-2">
                <button
                  type="button"
                  className="button secondary"
                  disabled={!state.link}
                  onClick={() => applyLink(true)}
                >
                  <Unlink size={15} />
                  링크 해제
                </button>
                <button type="button" className="button" onClick={() => applyLink()}>
                  적용
                </button>
              </div>
            </PopoverContent>
          </Popover>
        </div>
        <span className="hidden text-xs text-muted-foreground lg:block">
          사진을 끌어놓거나 붙여넣어 보세요
        </span>
      </div>
      <div className="editor-format-row">
        <AppSelect
          label="문단 스타일"
          placeholder="여러 서식"
          value={state.heading}
          compact
          disabled={disabled}
          className="editor-heading-select"
          options={[
            { value: 'paragraph', label: '본문' },
            { value: '1', label: '제목 1' },
            { value: '2', label: '제목 2' },
            { value: '3', label: '제목 3' },
          ]}
          onValueChange={(value) =>
            value === 'paragraph'
              ? editor.chain().focus().setParagraph().run()
              : editor
                  .chain()
                  .focus()
                  .setHeading({ level: Number(value) as 1 | 2 | 3 })
                  .run()
          }
        />
        <div
          className="editor-tool-group editor-tool-divider"
          role="group"
          aria-label="글자 서식"
        >
          <Tool
            label="굵게"
            shortcut="⌘/Ctrl B"
            active={state.bold}
            disabled={disabled}
            onClick={() => editor.chain().focus().toggleBold().run()}
          >
            <Bold />
          </Tool>
          <Tool
            label="기울임"
            shortcut="⌘/Ctrl I"
            active={state.italic}
            disabled={disabled}
            onClick={() => editor.chain().focus().toggleItalic().run()}
          >
            <Italic />
          </Tool>
          <Tool
            label="밑줄"
            shortcut="⌘/Ctrl U"
            active={state.underline}
            disabled={disabled}
            onClick={() => editor.chain().focus().toggleUnderline().run()}
          >
            <Underline />
          </Tool>
          <Tool
            label="취소선"
            active={state.strike}
            disabled={disabled}
            onClick={() => editor.chain().focus().toggleStrike().run()}
          >
            <Strikethrough />
          </Tool>
        </div>
        <div
          className="editor-tool-group editor-tool-divider"
          role="group"
          aria-label="문단 정렬"
        >
          <Tool
            label="왼쪽 정렬"
            active={state.align === 'left'}
            disabled={disabled}
            onClick={() => editor.chain().focus().setTextAlign('left').run()}
          >
            <AlignLeft />
          </Tool>
          <Tool
            label="가운데 정렬"
            active={state.align === 'center'}
            disabled={disabled}
            onClick={() => editor.chain().focus().setTextAlign('center').run()}
          >
            <AlignCenter />
          </Tool>
          <Tool
            label="오른쪽 정렬"
            active={state.align === 'right'}
            disabled={disabled}
            onClick={() => editor.chain().focus().setTextAlign('right').run()}
          >
            <AlignRight />
          </Tool>
        </div>
        <div className="editor-tool-group editor-tool-divider" role="group" aria-label="목록">
          <Tool
            label="목록"
            active={state.bullet}
            disabled={disabled}
            onClick={() => editor.chain().focus().toggleBulletList().run()}
          >
            <List />
          </Tool>
          <Tool
            label="번호 목록"
            active={state.ordered}
            disabled={disabled}
            onClick={() => editor.chain().focus().toggleOrderedList().run()}
          >
            <ListOrdered />
          </Tool>
        </div>
        <div className="editor-tool-group ml-auto" role="group" aria-label="편집 기록">
          <Tool
            label="실행 취소"
            shortcut="⌘/Ctrl Z"
            disabled={disabled || !state.undo}
            onClick={() => editor.chain().focus().undo().run()}
          >
            <Undo2 />
          </Tool>
          <Tool
            label="다시 실행"
            shortcut="⌘/Ctrl ⇧ Z"
            disabled={disabled || !state.redo}
            onClick={() => editor.chain().focus().redo().run()}
          >
            <Redo2 />
          </Tool>
        </div>
      </div>
    </div>
  )
}
