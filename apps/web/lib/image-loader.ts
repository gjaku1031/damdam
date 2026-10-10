'use client'
import type { ImageLoaderProps } from 'next/image'

/**
 * `next/image`가 `srcset` 후보를 만들 때 부르는 로더.
 * 요청 폭에 맞는 서버 변환본(s 400px, m 800px, l 1600px)의 주소를 돌려줌.
 * 브라우저가 화면 크기와 배율에 따라 셋 중 하나를 고름.
 * 저장소 사진(`storageUrl`의 결과)에만 씀. 그 밖의 정적 이미지는 `unoptimized`로 로더를 거치지 않게 함.
 */
export default function storageImageLoader({ src, width }: ImageLoaderProps) {
  const size = width <= 400 ? 's' : width <= 800 ? 'm' : 'l'
  return `${src}${src.includes('?') ? '&' : '?'}size=${size}`
}
