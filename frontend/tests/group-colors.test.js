import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

import {
  buildFieldColorEntries,
  buildParticipantColorMap,
  participantFieldValue,
  participantFieldValues,
} from '../src/utils/groupColors.js'

test('participant field values prefer primary attributes and ignore blanks', () => {
  const person = {
    id: 'p1',
    name: 'Alice',
    employeeNo: '001',
    primaryAttributes: { department: 'R&D' },
    attributeValues: { batch: ['', 'Batch 1'] },
  }

  assert.equal(participantFieldValue(person, 'name'), 'Alice')
  assert.equal(participantFieldValue(person, 'department'), 'R&D')
  assert.equal(participantFieldValue(person, 'batch'), 'Batch 1')
  assert.deepEqual(participantFieldValues(person, 'batch'), ['Batch 1'])
})

test('same field value shares the same soft color and empty value has no color', () => {
  const colors = buildParticipantColorMap([
    { id: 'p1', primaryAttributes: { department: 'A' } },
    { id: 'p2', primaryAttributes: { department: 'B' } },
    { id: 'p3', primaryAttributes: { department: 'A' } },
    { id: 'p4', primaryAttributes: { department: '' } },
  ], 'department')

  assert.equal(colors.get('p1').backgroundColor, colors.get('p3').backgroundColor)
  assert.notEqual(colors.get('p1').backgroundColor, colors.get('p2').backgroundColor)
  assert.equal(colors.has('p4'), false)
})

test('participants with multiple values use a segmented gradient', () => {
  const colors = buildParticipantColorMap([
    {
      id: 'p1',
      primaryAttributes: { batch: 'Batch 1' },
      attributeValues: { batch: ['Batch 1', 'Batch 3'] },
    },
    {
      id: 'p2',
      primaryAttributes: { batch: 'Batch 2' },
      attributeValues: { batch: ['Batch 2'] },
    },
  ], 'batch')

  assert.deepEqual(participantFieldValues({
    primaryAttributes: { batch: 'Batch 1' },
    attributeValues: { batch: ['Batch 1', 'Batch 3', ' '] },
  }, 'batch'), ['Batch 1', 'Batch 3'])
  assert.equal(colors.get('p1').multiValue, true)
  assert.match(colors.get('p1').backgroundImage, /linear-gradient/)
  assert.equal(colors.get('p1').value, 'Batch 1、Batch 3')
  assert.equal(colors.get('p2').multiValue, false)
})

test('field color entries support user overrides for legend and participants', () => {
  const participants = [
    { id: 'p1', primaryAttributes: { batch: 'Batch 1' } },
    { id: 'p2', primaryAttributes: { batch: 'Batch 2' } },
  ]
  const overrides = { 'Batch 2': '#12abc0' }
  const colors = buildParticipantColorMap(participants, 'batch', undefined, overrides)
  const entries = buildFieldColorEntries(participants, 'batch', undefined, overrides)

  assert.equal(colors.get('p2').backgroundColor, '#12abc0')
  assert.equal(colors.get('p2').custom, true)
  assert.deepEqual(
    entries.map((entry) => ({
      value: entry.value,
      backgroundColor: entry.backgroundColor,
      custom: Boolean(entry.custom),
    })),
    [
      { value: 'Batch 1', backgroundColor: '#FEF3C7', custom: false },
      { value: 'Batch 2', backgroundColor: '#12abc0', custom: true },
    ],
  )
})

test('group color legend collapses into a compact tab without leaving a blank card', async () => {
  const source = await readFile(new URL('../src/components/GroupColorLegend.vue', import.meta.url), 'utf8')
  const picker = await readFile(new URL('../src/components/ColorPickerPopover.vue', import.meta.url), 'utf8')
  const legendSwatchBlock = source.match(/\.legend-swatch\s*\{[^}]*\}/)?.[0] || ''

  assert.match(source, /ref="legendRef"/)
  assert.match(source, /legendTop/)
  assert.match(source, /startLegendDrag/)
  assert.match(source, /@pointerdown\.left="startLegendDrag"/)
  assert.match(source, /class="legend-card"/)
  assert.match(source, /\.group-color-legend\s*\{[\s\S]*overflow:\s*visible;/)
  assert.match(source, /\.legend-toggle\s*\{[\s\S]*position:\s*absolute;[\s\S]*width:\s*28px;[\s\S]*height:\s*52px;/)
  assert.match(source, /\.legend-toggle\s*\{[\s\S]*right:\s*-28px;/)
  assert.match(source, /\.legend-card\s*\{[\s\S]*overflow:\s*visible;/)
  assert.match(source, /\.group-color-legend\.collapsed \.legend-card\s*\{[\s\S]*opacity:\s*0;[\s\S]*visibility:\s*hidden;/)
  assert.match(source, /closeColorPopovers/)
  assert.match(source, /meeting-helper:close-color-popovers/)
  assert.match(source, /requestLegendCollapse/)
  assert.match(legendSwatchBlock, /border:\s*0;/)
  assert.doesNotMatch(legendSwatchBlock, /border:\s*1px/)
  assert.doesNotMatch(source, /min-height:\s*100%;/)
  assert.doesNotMatch(source, /translateX\(calc\(-100%/)
  assert.match(picker, /<el-popover/)
  assert.match(picker, /:teleported="true"/)
})

test('shared color picker previews swatches and only applies on confirm', async () => {
  const picker = await readFile(new URL('../src/components/ColorPickerPopover.vue', import.meta.url), 'utf8')
  const chooseColorBody = picker.match(/function chooseColor\(value\) \{[\s\S]*?\n\}/)?.[0] || ''
  const currentButtonBlock = picker.match(/\.current-color-button\s*\{[^}]*\}/)?.[0] || ''
  const currentDotBlock = picker.match(/\.current-color-dot\s*\{[^}]*\}/)?.[0] || ''
  const swatchBlock = picker.match(
    /\.color-swatch-button,\s*\n\.custom-color-preview,\s*\n\.custom-preview-dot\s*\{[^}]*\}/,
  )?.[0] || ''

  assert.match(picker, /const pendingColor = ref/)
  assert.match(picker, /unavailableColors/)
  assert.match(picker, /unavailableColorSet/)
  assert.match(picker, /isColorUnavailable/)
  assert.match(picker, /rgbLabel/)
  assert.match(picker, /meeting-helper:close-color-popovers/)
  assert.match(chooseColorBody, /pendingColor\.value = color/)
  assert.match(chooseColorBody, /customPreview\.value = color/)
  assert.doesNotMatch(chooseColorBody, /emit\(/)
  assert.doesNotMatch(chooseColorBody, /open\.value = false/)
  assert.doesNotMatch(chooseColorBody, /saveCustomColor/)
  assert.match(picker, /function confirmSelectedColor/)
  assert.match(picker, /saveCustomColor\(pendingColor\.value\)/)
  assert.match(picker, /@click="confirmSelectedColor"/)
  assert.match(picker, /active: pendingColor === swatch\.value/)
  assert.match(picker, /:disabled="isColorUnavailable\(swatch\.value\)"/)
  assert.match(picker, /:title="rgbLabel\(swatch\.value\)"/)
  assert.match(picker, /:title="rgbLabel\(currentColor\)"/)
  assert.match(picker, /\.swatch-grid\s*\{[\s\S]*padding:\s*5px 4px 4px;/)
  assert.match(currentButtonBlock, /border:\s*0;/)
  assert.match(currentDotBlock, /border:\s*0;/)
  assert.match(swatchBlock, /border:\s*0;/)
  assert.doesNotMatch(currentButtonBlock, /border:\s*1px/)
  assert.doesNotMatch(currentDotBlock, /border:\s*1px/)
  assert.doesNotMatch(swatchBlock, /border:\s*1px/)
})
