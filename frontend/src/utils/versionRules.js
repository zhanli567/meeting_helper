export function normalizeVersionName(value) {
  return String(value ?? '').trim()
}

export function hasDuplicateVersionName(value, versions = []) {
  const normalized = normalizeVersionName(value).toLocaleLowerCase()
  if (!normalized) return false
  return versions.some(
    (version) => normalizeVersionName(version.versionName).toLocaleLowerCase() === normalized,
  )
}
