import assert from 'node:assert/strict'
import { readdir, readFile } from 'node:fs/promises'
import test from 'node:test'

async function collectVueFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const files = await Promise.all(
    entries.map(async (entry) => {
      const child = new URL(`${entry.name}${entry.isDirectory() ? '/' : ''}`, directory)
      if (entry.isDirectory()) {
        return collectVueFiles(child)
      }
      return entry.name.endsWith('.vue') ? [child] : []
    }),
  )
  return files.flat()
}

test('all app input controls avoid placeholder copy so forms stay visually consistent', async () => {
  const sourceRoot = new URL('../src/', import.meta.url)
  const files = await collectVueFiles(sourceRoot)
  const offenders = []

  for (const file of files) {
    const source = await readFile(file, 'utf8')
    if (/placeholder=/.test(source)) {
      offenders.push(file)
    }
  }

  assert.deepEqual(offenders, [])
})
