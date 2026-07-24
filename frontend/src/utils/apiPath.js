/**
 * 返回当前运行环境的接口基础路径。
 *
 * 开发环境保留 `/api` 作为 Vite 代理标识，代理转发时会移除该前缀；
 * 生产环境直接使用后端控制层的根路径。
 */
export function apiBaseUrl(isDevelopment = import.meta.env.DEV) {
  return isDevelopment ? '/api' : ''
}

/**
 * 构造浏览器直接下载文件时使用的接口地址。
 */
export function apiDownloadUrl(path, isDevelopment = import.meta.env.DEV) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${apiBaseUrl(isDevelopment)}${normalizedPath}`
}
