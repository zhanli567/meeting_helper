export interface MeetingSummary {
  id: string
  name: string
  status: string
  layoutName: string
  updatedAt: string
  updatedByName: string
}

export interface Workspace {
  meeting: {
    id: string
    name: string
    status: string
    layoutName: string
    layoutVersion: number
    updatedAt: string
    updatedByName: string
  }
  plan: {
    id: string
    name: string
    status: string
    currentVersionNo: number
  }
  layout: {
    gridRows: number
    gridColumns: number
    cellSize: number
    elements: LayoutElement[]
  }
  participants: Participant[]
  items: PlanItem[]
  versions: PlanVersion[]
  fieldDefinitions: FieldDefinition[]
  styleRules: StyleRule[]
}

export interface LayoutElement {
  id: string
  type: ElementType
  code?: string
  label?: string
  row: number
  column: number
  rowSpan: number
  columnSpan: number
  rotation: number
  capacity: number
  assignable: boolean
  walkable: boolean
  groupCode?: string
  groupLabel?: string
  sequenceNo?: number
  backgroundColor?: string
  borderColor?: string
}

export type ElementType =
  | 'SEAT'
  | 'AISLE'
  | 'WALL'
  | 'DOOR'
  | 'STAIR'
  | 'STAGE'
  | 'TABLE'
  | 'SCREEN'
  | 'PODIUM'
  | 'LABEL'
  | 'EMPTY'

export interface AwardRecord {
  id: string
  batchOrder: number
  batchName: string
  awardName: string
  awardLevel?: string
  projectName?: string
  teamSize?: number
}

export interface Participant {
  id: string
  employeeNo: string
  name: string
  level?: number
  department?: string
  participantType?: string
  tags: string[]
  attributes: Record<string, string>
  locked: boolean
  assignedElementId?: string
  primaryBatchOrder?: number
  primaryBatchName?: string
  displayColor?: string
  repeatedBatches: string[]
  awards: AwardRecord[]
}

export interface PlanItem {
  id: string
  type: 'PERSON' | 'EQUIPMENT' | 'RESERVED' | 'DISABLED'
  participantId?: string
  label?: string
  locked: boolean
  backgroundColor?: string
  textColor?: string
  bold: boolean
  targetElementIds: string[]
}

export interface PlanVersion {
  id: string
  versionNo: number
  versionName: string
  changeNote?: string
  automatic: boolean
  assignedCount: number
  unassignedCount: number
  createdAt: string
  createdByName: string
}

export interface FieldDefinition {
  code: string
  label: string
  type: 'TEXT' | 'NUMBER' | 'ENUM' | 'MULTI_ENUM' | 'BOOLEAN' | 'DATE'
  searchable: boolean
  filterable: boolean
  sortable: boolean
  cardVisible: boolean
}

export interface StyleRule {
  fieldCode: string
  value: string
  backgroundColor: string
  textColor: string
}

export interface ImportTemplate {
  code: string
  name: string
  description: string
  version: number
  sheets: Array<{ name: string; required: boolean; rowMeaning: string }>
}

export interface ImportParticipantRow {
  sourceRow: number
  employeeNo: string
  name: string
  level?: number
  department?: string
  participantType?: string
  tags?: string
  attributes: Record<string, string>
}

export interface ImportPreview {
  token: string
  templateCode: string
  participantRowCount: number
  awardRowCount: number
  uniqueParticipants: ImportParticipantRow[]
  duplicateGroups: Array<{ employeeNo: string; candidates: ImportParticipantRow[] }>
  errors: string[]
}

export interface VenueSummary {
  id: string
  name: string
  description?: string
  gridRows: number
  gridColumns: number
  versionNo: number
  preset: boolean
  seatCount: number
}

export interface VenueDetail {
  id: string
  name: string
  description?: string
  gridRows: number
  gridColumns: number
  cellSize: number
  versionNo: number
  preset: boolean
  frontDirection: string
  elements: Array<Omit<LayoutElement, 'id'>>
}
