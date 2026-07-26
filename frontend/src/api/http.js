import axios from 'axios'
import Aurora from './aurora.js'
import { apiBaseUrl } from '../utils/apiPath.js'

function apiPath(path) {
  return `${apiBaseUrl(Boolean(import.meta.env?.DEV))}${path}`
}

export const http = {
  get(path, config) {
    return Aurora.service.network.get(apiPath(path), config)
  },
  post(path, data, config) {
    return Aurora.service.network.post(apiPath(path), data, config)
  },
}

export async function unwrap(request) {
  const response = await request
  const envelope = response?.data
  if (envelope?.code === 0) {
    return envelope.data
  }
  throw new Error(envelope?.msg || '请求失败')
}

export async function raw(request) {
  return (await request).data
}

export function apiErrorMessage(error) {
  if (axios.isAxiosError(error)) {
    return error.response?.data?.msg || error.message
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
