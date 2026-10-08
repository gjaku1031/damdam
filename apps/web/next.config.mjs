/** @type {import('next').NextConfig} */
const nextConfig = {
  transpilePackages: ['@damdam/ui', '@damdam/shared'],
  output: 'standalone',
  images: {
    unoptimized: true,
  },
  async redirects() {
    return [
      { source: '/community', destination: '/posts', permanent: true },
      { source: '/return-policy', destination: '/exchange-policy', permanent: true },
      { source: '/admin/login', destination: '/login?next=/mypage', permanent: false },
      { source: '/admin/products/:path*', destination: '/products/:path*', permanent: false },
      { source: '/admin/inventory', destination: '/products', permanent: false },
      { source: '/admin/orders', destination: '/mypage/orders', permanent: false },
      { source: '/admin/users', destination: '/mypage/members', permanent: false },
      { source: '/admin/payments', destination: '/mypage/settings', permanent: false },
      { source: '/admin/audit', destination: '/mypage/history', permanent: false },
      { source: '/admin/community', destination: '/posts', permanent: false },
      { source: '/admin/cms', destination: '/notices', permanent: false },
      { source: '/admin/:path*', destination: '/mypage', permanent: false },
    ]
  },
  async rewrites() {
    return {
      beforeFiles: [
        {
          source: '/images/:path*',
          destination: `${process.env.API_INTERNAL_URL ?? 'http://127.0.0.1:4000'}/api/storage/legacy-files/:path*`,
        },
      ],
      afterFiles: [
        {
          source: '/api/:path*',
          destination: `${process.env.API_INTERNAL_URL ?? 'http://127.0.0.1:4000'}/api/:path*`,
        },
      ],
      fallback: [],
    }
  },
}

export default nextConfig
