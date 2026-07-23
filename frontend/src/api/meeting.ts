import { http } from './http'
import type {
  ImportPreview,
  ImportTemplate,
  MeetingSummary,
  VenueSummary,
  Workspace,
} from '@/types/workspace'

export const meetingApi = {
  async meetings() {
    return (await http.get<MeetingSummary[]>('/meetings')).data
  },
  async workspace(meetingId: string) {
    return (await http.get<Workspace>(`/meetings/${meetingId}/workspace`)).data
  },
  async assign(planId: string, participantId: string, targetElementId: string) {
    await http.post(`/plans/${planId}/assignments`, { participantId, targetElementId })
  },
  async unassign(planId: string, participantId: string) {
    await http.delete(`/plans/${planId}/participants/${participantId}/assignment`)
  },
  async setLock(planId: string, participantId: string, locked: boolean) {
    await http.put(`/plans/${planId}/participants/${participantId}/lock`, undefined, {
      params: { locked },
    })
  },
  async addParticipant(meetingId: string, data: Record<string, unknown>) {
    return (await http.post(`/meetings/${meetingId}/participants`, data)).data
  },
  async deleteParticipant(meetingId: string, participantId: string) {
    await http.delete(`/meetings/${meetingId}/participants/${participantId}`)
  },
  async createVersion(
    planId: string,
    data: { versionName: string; changeNote?: string; automatic: boolean },
  ) {
    return (await http.post(`/plans/${planId}/versions`, data)).data
  },
  async restoreVersion(planId: string, versionId: string) {
    return (await http.post(`/plans/${planId}/versions/${versionId}/restore`)).data
  },
  async importTemplates() {
    return (await http.get<ImportTemplate[]>('/import-templates')).data
  },
  async previewImport(meetingId: string, templateCode: string, file: File) {
    const form = new FormData()
    form.append('file', file)
    return (
      await http.post<ImportPreview>(`/meetings/${meetingId}/imports/preview`, form, {
        params: { templateCode },
        headers: { 'Content-Type': 'multipart/form-data' },
      })
    ).data
  },
  async commitImport(meetingId: string, token: string, selectedSourceRows: Record<string, number>) {
    return (
      await http.post(`/meetings/${meetingId}/imports/${token}/commit`, { selectedSourceRows })
    ).data
  },
  async exportFile(meetingId: string, type: 'excel' | 'pdf') {
    return (
      await http.get<ArrayBuffer>(`/meetings/${meetingId}/exports/${type}`, {
        responseType: 'arraybuffer',
      })
    ).data
  },
  async venues() {
    return (await http.get<VenueSummary[]>('/venues')).data
  },
  async createVenue(data: Record<string, unknown>) {
    return (await http.post('/venues', data)).data
  },
  async createMeeting(name: string, venueTemplateId: string) {
    return (await http.post<MeetingSummary>('/meetings', { name, venueTemplateId })).data
  },
}
