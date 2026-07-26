import { http } from './http.js'

export const importContract = Object.freeze({
  templatePath: '/imports/template',
  previewPath: (meetingId) => `/meetings/${meetingId}/imports/preview`,
  commitPath: (meetingId, token) => `/meetings/${meetingId}/imports/${token}/commit`,
  canCommit: (preview) =>
    Boolean(preview) && Array.isArray(preview.errors) && preview.errors.length === 0,
})

export const meetingApi = {
  async meetings() {
    return (await http.get('/meetings')).data
  },
  async workspace(meetingId) {
    return (await http.get(`/meetings/${meetingId}/workspace`)).data
  },
  async assign(planId, participantId, targetElementId) {
    await http.post(`/plans/${planId}/assignments`, { participantId, targetElementId })
  },
  async unassign(planId, participantId) {
    await http.delete(`/plans/${planId}/participants/${participantId}/assignment`)
  },
  async saveAssignments(planId, assignments) {
    await http.put(`/plans/${planId}/assignments`, { assignments })
  },
  async setLock(planId, participantId, locked) {
    await http.put(`/plans/${planId}/participants/${participantId}/lock`, undefined, {
      params: { locked },
    })
  },
  async addParticipant(meetingId, data) {
    return (await http.post(`/meetings/${meetingId}/participants`, data)).data
  },
  async deleteParticipant(meetingId, participantId) {
    await http.delete(`/meetings/${meetingId}/participants/${participantId}`)
  },
  async updateAttendance(meetingId, participantId, attendanceStatus) {
    await http.put(`/meetings/${meetingId}/participants/${participantId}/attendance`, {
      attendanceStatus,
    })
  },
  async createVersion(planId, data) {
    return (await http.post(`/plans/${planId}/versions`, data)).data
  },
  async restoreVersion(planId, versionId) {
    return (await http.post(`/plans/${planId}/versions/${versionId}/restore`)).data
  },
  async versionSnapshot(planId, versionId) {
    return (await http.get(`/plans/${planId}/versions/${versionId}`)).data
  },
  async importTemplate() {
    return (await http.get(importContract.templatePath, { responseType: 'arraybuffer' })).data
  },
  async previewImport(meetingId, file) {
    const form = new FormData()
    form.append('file', file)
    return (
      await http.post(importContract.previewPath(meetingId), form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
    ).data
  },
  async commitImport(meetingId, token) {
    return (await http.post(importContract.commitPath(meetingId, token))).data
  },
  async exportFile(meetingId, type, versionId) {
    return (
      await http.get(`/meetings/${meetingId}/exports/${type}`, {
        responseType: 'arraybuffer',
        params: { versionId },
      })
    ).data
  },
  async venues() {
    return (await http.get('/venues')).data
  },
  async venue(id) {
    return (await http.get(`/venues/${id}`)).data
  },
  async createVenue(data) {
    return (await http.post('/venues', data)).data
  },
  async updateVenue(id, data) {
    return (await http.put(`/venues/${id}`, data)).data
  },
  async deleteVenue(id) {
    await http.delete(`/venues/${id}`)
  },
  async createMeeting(name, venueTemplateId) {
    return (await http.post('/meetings', { name, venueTemplateId })).data
  },
}
