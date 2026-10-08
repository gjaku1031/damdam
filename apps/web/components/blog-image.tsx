'use client'
import { AppSelect } from './app-select'
import { ArrowDown, ArrowUp, Trash2 } from 'lucide-react'
import { NodeViewWrapper, ReactNodeViewRenderer, type NodeViewProps } from '@tiptap/react'
import Image from '@tiptap/extension-image'
import { Fragment } from '@tiptap/pm/model'
import { NodeSelection } from '@tiptap/pm/state'
function ImageView({ node, editor, getPos, updateAttributes, deleteNode }: NodeViewProps) {
  const attrs = node.attrs
  function move(direction: number) {
    const pos = getPos()
    if (pos === undefined) return
    const blocks: { node: typeof node; pos: number }[] = []
    editor.state.doc.forEach((item, offset) => blocks.push({ node: item, pos: offset }))
    const index = blocks.findIndex((block) => block.pos === pos),
      target = index + direction
    if (index < 0 || target < 0 || target >= blocks.length) return
    ;[blocks[index], blocks[target]] = [blocks[target], blocks[index]]
    let position = 0
    for (let i = 0; i < target; i++) position += blocks[i].node.nodeSize
    const tr = editor.state.tr.replaceWith(
      0,
      editor.state.doc.content.size,
      Fragment.fromArray(blocks.map((b) => b.node)),
    )
    tr.setSelection(NodeSelection.create(tr.doc, position))
    editor.view.dispatch(tr)
  }
  return (
    <NodeViewWrapper
      as="figure"
      data-drag-handle
      draggable
      className="group relative"
      style={{
        width: attrs.width + '%',
        marginLeft: attrs.align === 'left' ? 0 : 'auto',
        marginRight: attrs.align === 'right' ? 0 : 'auto',
      }}
      contentEditable={false}
    >
      <img
        src={attrs.src}
        alt={attrs.caption || ''}
        className="w-full rounded"
        draggable={false}
      />
      <div className="my-3 flex flex-wrap items-center gap-2 rounded-lg border border-border bg-background p-2 text-xs">
        <button
          type="button"
          aria-label="사진 위로"
          className="inline-flex size-8 items-center justify-center rounded-md border border-border bg-card hover:bg-muted"
          onClick={() => move(-1)}
        >
          <ArrowUp className="size-4" aria-hidden="true" />
        </button>
        <button
          type="button"
          aria-label="사진 아래로"
          className="inline-flex size-8 items-center justify-center rounded-md border border-border bg-card hover:bg-muted"
          onClick={() => move(1)}
        >
          <ArrowDown className="size-4" aria-hidden="true" />
        </button>
        <label className="flex items-center gap-1.5 whitespace-nowrap">
          <span className="text-muted-foreground">크기</span>
          <AppSelect
            label="사진 크기"
            compact
            className="min-w-20"
            value={String(attrs.width)}
            onValueChange={(value) => updateAttributes({ width: Number(value) })}
            options={[25, 50, 75, 100].map((width) => ({
              value: String(width),
              label: `${width}%`,
            }))}
          />
        </label>
        <label className="flex items-center gap-1.5 whitespace-nowrap">
          <span className="text-muted-foreground">정렬</span>
          <AppSelect
            label="사진 정렬"
            compact
            className="min-w-20"
            value={attrs.align}
            onValueChange={(value) => updateAttributes({ align: value })}
            options={[
              { value: 'left', label: '왼쪽' },
              { value: 'center', label: '가운데' },
              { value: 'right', label: '오른쪽' },
            ]}
          />
        </label>
        <button
          type="button"
          onClick={deleteNode}
          className="inline-flex h-8 items-center gap-1 rounded-md px-2 text-destructive hover:bg-muted"
        >
          <Trash2 className="size-3.5" aria-hidden="true" />
          삭제
        </button>
        <span className="ml-auto hidden whitespace-nowrap text-subtle-foreground sm:inline">
          끌어서 이동
        </span>
      </div>
      <input
        className="control text-center text-sm"
        value={attrs.caption ?? ''}
        aria-label="사진 설명"
        placeholder="사진 설명"
        onChange={(e) => updateAttributes({ caption: e.target.value, alt: e.target.value })}
      />
    </NodeViewWrapper>
  )
}
export const BlogImage = Image.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      src: {
        default: null,
        parseHTML: (el) => (el.querySelector('img') ?? el).getAttribute('src'),
      },
      alt: {
        default: null,
        parseHTML: (el) => (el.querySelector('img') ?? el).getAttribute('alt'),
      },
      caption: {
        default: '',
        parseHTML: (el) => el.querySelector('figcaption')?.textContent ?? '',
      },
      width: { default: 100, parseHTML: (el) => parseInt(el.style.width) || 100 },
      align: {
        default: 'center',
        parseHTML: (el) =>
          /^0(px)?$/.test(el.style.marginLeft)
            ? 'left'
            : /^0(px)?$/.test(el.style.marginRight)
              ? 'right'
              : 'center',
      },
      objectKey: {
        default: '',
        parseHTML: (el) =>
          ((el.querySelector('img') ?? el).getAttribute('src') ?? '').replace(
            '/api/storage/files/',
            '',
          ),
      },
    }
  },
  parseHTML() {
    return [
      { tag: 'figure', getAttrs: (el) => (el.querySelector('img') ? null : false) },
      { tag: 'img[src]' },
    ]
  },
  renderHTML({ node }) {
    const a = node.attrs
    return [
      'figure',
      {
        style:
          'width:' +
          a.width +
          '%;margin-left:' +
          (a.align === 'left' ? '0' : 'auto') +
          ';margin-right:' +
          (a.align === 'right' ? '0' : 'auto'),
      },
      ['img', { src: a.src, alt: a.alt ?? a.caption ?? '', style: 'width:100%;height:auto' }],
      ['figcaption', {}, a.caption ?? ''],
    ]
  },
  addNodeView() {
    return ReactNodeViewRenderer(ImageView)
  },
})
