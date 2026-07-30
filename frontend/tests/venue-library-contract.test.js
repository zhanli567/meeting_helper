import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

async function readSource(path) {
  return readFile(new URL(`../${path}`, import.meta.url), 'utf8')
}

test('场馆列表使用表格、分页和双模式', async () => {
  const source = await readSource('src/views/VenueLibraryView.vue')

  assert.match(source, /<el-table/)
  assert.match(source, /<el-pagination/)
  assert.match(source, /route\.name === 'venue-select'/)
  assert.match(source, /按园区分组/)
  assert.doesNotMatch(source, /系统预置|versionNo|preset/)
})

test('场馆列表使用独立 API，并提供搜索、筛选、当前页分组和布局未完成状态', async () => {
  const source = await readSource('src/views/VenueLibraryView.vue')

  assert.match(source, /venueApi\.list/)
  assert.match(source, /venueApi\.detail/)
  assert.match(source, /venueApi\.remove/)
  assert.match(source, /300/)
  assert.match(source, /\[10,\s*20,\s*50\]/)
  assert.match(source, /未填写园区/)
  assert.match(source, /布局未完成/)
  assert.match(source, /seatCount === 0/)
})

test('首页顶部保留场馆模板入口，会议标题右侧提供添加会议', async () => {
  const source = await readSource('src/views/HomeView.vue')

  assert.match(source, /场馆模板/)
  assert.match(source, /router\.push\('\/venues'\)/)
  assert.doesNotMatch(source, /开始排座/)
  assert.match(source, /添加会议/)
  assert.doesNotMatch(source, /router\.push\('\/venues\/select'\)/)
  assert.match(source, /EditPen/)
  assert.match(source, /Delete/)
  assert.match(source, /editMeeting\(meeting\)/)
  assert.match(source, /removeMeeting\(meeting\)/)
  assert.match(source, /meetingApi\.updateMeetingName\(editingMeeting\.id,\s*trimmedName\)/)
  assert.match(source, /meetingApi\.deleteMeeting\(meeting\.id\)/)
  assert.match(source, /确认删除会议/)
  assert.match(source, /@click\.stop/)
  assert.doesNotMatch(source, /创建新会议/)
})

test('路由区分场馆管理、选择、新建和布局编辑', async () => {
  const source = await readSource('src/router/index.js')

  assert.match(source, /path:\s*'\/venues',\s*name:\s*'venue-manage'/)
  assert.match(source, /path:\s*'\/venues\/select',\s*name:\s*'venue-select'/)
  assert.match(
    source,
    /path:\s*'\/venues\/new',\s*name:\s*'venue-new',\s*component:\s*\(\)\s*=>\s*import\('@\/views\/VenueCreateView\.vue'\)/,
  )
  assert.match(source, /path:\s*'\/venues\/:venueId\/layout\/edit'/)
  assert.match(source, /name:\s*'venue-layout-edit'/)
})

test('场馆信息表单只显示高频字段，且只有地点必填', async () => {
  const source = await readSource('src/components/VenueInfoForm.vue')

  for (const field of [
    'location',
    'campus',
    'manualCapacity',
    'contactInfo',
    'remarks',
  ]) {
    assert.match(source, new RegExp(`form\\.${field}`))
  }
  for (const field of [
    'bookingUrl',
    'mainScreenResolution',
    'stageDimensions',
    'meetingRoomFunctions',
    'servicesProvided',
    'description',
  ]) {
    assert.doesNotMatch(source, new RegExp(`v-model="form\\.${field}"`))
  }
  assert.match(source, /基本信息/)
  assert.doesNotMatch(source, /设备信息/)
  assert.doesNotMatch(source, /补充信息/)
  assert.match(source, /prop="location"/)
  assert.match(source, /required:\s*true/)
  assert.equal((source.match(/\{\s*required:\s*true,\s*message:/g) || []).length, 1)
  assert.equal((source.match(/type="textarea"/g) || []).length, 1)
  assert.match(source, /normalizeVenueInfo/)
})

test('详情抽屉只读，信息抽屉携带行版本更新并通知列表刷新', async () => {
  const detailSource = await readSource('src/components/VenueDetailDrawer.vue')
  const editSource = await readSource('src/components/VenueInfoDrawer.vue')

  assert.match(detailSource, /<el-drawer/)
  assert.match(detailSource, /<el-descriptions/)
  assert.doesNotMatch(detailSource, /venueApi\.updateInfo/)
  for (const label of ['地点', '园区', '容纳人数', '接口人', '备注']) {
    assert.match(detailSource, new RegExp(`label="${label}"`))
  }
  for (const hidden of [
    '设备信息',
    '补充信息',
    '主屏分辨率',
    '舞台尺寸',
    '会议室功能',
    '服务提供',
    '预定链接',
    '说明',
    '最近更新',
  ]) {
    assert.doesNotMatch(detailSource, new RegExp(hidden))
  }
  assert.match(editSource, /<VenueInfoForm/)
  assert.match(editSource, /venueApi\.updateInfo\(props\.venue\.id/)
  assert.match(editSource, /\.\.\.normalizeVenueInfo\(form\)/)
  assert.match(editSource, /rowVersion:\s*props\.venue\.rowVersion/)
  assert.match(editSource, /emit\('saved'/)
})

test('场馆模板表格列宽、溢出提示和操作按钮保持统一展开', async () => {
  const source = await readSource('src/views/VenueLibraryView.vue')

  assert.match(source, /:fit="true"/)
  assert.match(source, /prop="location" label="地点" min-width="240"/)
  assert.match(source, /prop="campus" label="园区" min-width="150"/)
  assert.match(source, /prop="manualCapacity" label="容纳人数" width="110"/)
  assert.match(source, /prop="seatCount" label="布局座位数" width="130"/)
  assert.match(source, /prop="updatedByName" label="更新人" min-width="120"/)
  assert.match(source, /prop="updatedAt" label="更新时间" min-width="170"/)
  assert.match(source, /label="操作" width="420"/)
  assert.match(source, /show-overflow-tooltip/)
  assert.match(source, /class="cell-ellipsis"/)
  assert.match(source, /function displayText/)
  assert.match(source, /return '-'/)
  assert.match(source, /getFullYear\(\)/)
  assert.match(source, /String\(date\.getHours\(\)\)\.padStart\(2, '0'\)/)
  assert.doesNotMatch(source, /MoreFilled|<el-dropdown|el-dropdown-item/)
  for (const action of ['使用模板', '详情', '预览', '编辑信息', '编辑布局', '删除']) {
    assert.match(source, new RegExp(`>\\s*${action}\\s*<`))
  }
})

test('场馆新建保留信息和默认布局草稿并在第二步统一保存', async () => {
  const source = await readSource('src/views/VenueCreateView.vue')

  assert.match(source, /const step = ref\('info'\)/)
  assert.match(source, /const info = reactive\(emptyVenueInfo\(\)\)/)
  assert.match(
    source,
    /const layout = reactive\(\{\s*gridRows:\s*20,\s*gridColumns:\s*30,\s*elements:\s*\[\]\s*\}\)/,
  )
  assert.match(source, /<VenueInfoForm/)
  assert.match(source, /<VenueLayoutEditor/)
  assert.match(source, /onBeforeRouteLeave/)
  assert.match(source, /beforeunload/)
  assert.match(source, /venueApi\.create\(toCreateVenuePayload\(info,\s*layout\)\)/)
  assert.doesNotMatch(source, /画布行数|画布列数/)
})

test('场馆预览和布局编辑页面不再调用会议 API 的场馆方法', async () => {
  const previewSource = await readSource('src/components/VenuePreviewDialog.vue')
  const designerSource = await readSource('src/views/VenueLayoutEditorView.vue')

  assert.match(previewSource, /venueApi\.layout/)
  assert.doesNotMatch(previewSource, /meetingApi/)
  assert.match(designerSource, /venueApi\.(list|detail|layout|updateLayout)/)
  assert.doesNotMatch(
    designerSource,
    /meetingApi\.(venues|venue|createVenue|updateVenue|deleteVenue)/,
  )
})

test('场馆预览只读并支持初始适配、滚轮缩放和右键平移', async () => {
  const source = await readSource('src/components/VenuePreviewDialog.vue')

  assert.match(source, /venueApi\.layout\(venueId\)/)
  assert.match(source, /`预览场馆：\$\{venue\.location\}`/)
  assert.match(source, /@opened="fitCanvas"/)
  assert.match(source, /nextTick\(centerCanvas\)/)
  assert.match(source, /@wheel="onWheel"/)
  assert.match(source, /event\.button !== 2/)
  assert.match(source, /@contextmenu\.prevent/)
  assert.doesNotMatch(source, /VenueElement(Picker|Panel)|VenueLayoutEditor/)
})

test('会议创建防止重复提交且 Enter 只保留单一触发路径', async () => {
  const source = await readSource('src/views/VenueLibraryView.vue')

  assert.match(source, /if \(submitting\.value\) return/)
  assert.match(source, /@submit\.prevent="createMeeting"/)
  assert.doesNotMatch(source, /@keyup\.enter="createMeeting"/)
})

test('选择有座位模板后填写会议名称并进入独立会议工作台', async () => {
  const source = await readSource('src/views/VenueLibraryView.vue')

  assert.match(source, /if \(venue\.seatCount === 0\) return/)
  assert.match(source, /meetingForm\.venueTemplateId = venue\.id/)
  assert.match(source, /meetingVisible\.value = true/)
  assert.match(source, /meetingApi\.createMeeting\(name, meetingForm\.venueTemplateId\)/)
  assert.match(source, /store\.rememberMeeting\(meetingId\)/)
  assert.match(source, /router\.push\(`\/workbench\/\$\{meetingId\}`\)/)
  assert.match(source, /:disabled="row\.seatCount === 0"/)
})

test('场馆列表只接收最后一次查询响应', async () => {
  const source = await readSource('src/views/VenueLibraryView.vue')

  assert.match(source, /let latestLoadId/)
  assert.match(source, /const loadId = \+\+latestLoadId/)
  assert.match(source, /if \(loadId !== latestLoadId\) return/)
  assert.match(source, /if \(loadId === latestLoadId\) loading\.value = false/)
})

test('场馆模板页首页入口样式和会议编辑页保持一致', async () => {
  const source = await readSource('src/views/VenueLibraryView.vue')

  assert.match(source, /import \{[\s\S]*House[\s\S]*\} from '@element-plus\/icons-vue'/)
  assert.match(
    source,
    /<el-button\s+text\s+class="header-home header-home-left"[\s\S]*:icon="House"[\s\S]*>\s*首页\s*<\/el-button>/,
  )
  assert.match(source, /\.header-home\s*\{[\s\S]*font-weight:\s*650;/)
  assert.doesNotMatch(source, /class="back-button" :icon="ArrowLeft"/)
  assert.doesNotMatch(source, />\s*返回首页\s*</)
})

test('布局编辑页不提供无法随布局保存的场馆信息输入', async () => {
  const source = await readSource('src/views/VenueLayoutEditorView.vue')

  assert.doesNotMatch(source, /v-model="venue\.(location|description)"/)
  assert.match(source, /venue\?\.location/)
  assert.match(source, /venue\?\.description/)
})
