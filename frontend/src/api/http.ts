import axios from 'axios'

export const http = axios.create({
  baseURL: '/api',
  timeout: 20_000,
})

export function apiErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    return error.response?.data?.detail || error.response?.data?.title || error.message
  }
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

export function downloadBlob(data: BlobPart, filename: string, type: string) {
  const url = URL.createObjectURL(new Blob([data], { type }))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}
