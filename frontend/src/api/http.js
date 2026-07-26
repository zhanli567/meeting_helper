import axios from 'axios'
import { currentUser } from '../auth/session.js'
import { apiBaseUrl } from '../utils/apiPath.js'

export const http = axios.create({
  baseURL: apiBaseUrl(Boolean(import.meta.env?.DEV)),
  timeout: 20_000,
})

http.interceptors.request.use((config) => {
  config.headers['X-User-Id'] = currentUser.id
  return config
})
export function apiErrorMessage(error) {
  if (axios.isAxiosError(error)) {
    return error.response?.data?.detail || error.response?.data?.title || error.message
  }
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}
export function downloadBlob(data, filename, type) {
  const url = URL.createObjectURL(new Blob([data], { type }))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}
