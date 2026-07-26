import { http, raw, unwrap } from './http.js'

export const importContract = Object.freeze({
  templatePath: '/imports/template',
  previewPath: (meetingId) => `/meetings/${meetingId}/imports/preview`,
  commitPath: (meetingId, token) => `/meetings/${meetingId}/imports/${token}/commit`,
  canCommit: (preview) =>
    Boolean(preview) && Array.isArray(preview.errors) && preview.errors.length === 0,
})

export const meetingApi = {
  async meetings() {
    return unwrap(http.get('/meetings'))
  },
  async workspace(meetingId) {
    return unwrap(http.get(`/meetings/${meetingId}/workspace`))
  },
  async assign(planId, participantId, targetElementId) {
    return unwrap(http.post(`/plans/${planId}/assignments`, { participantId, targetElementId }))
  },
  async unassign(planId, participantId) {
    return unwrap(http.post(`/plans/${planId}/participants/${participantId}/assignment/remove`))
  },
  async saveAssignments(planId, assignments) {
    return unwrap(http.post(`/plans/${planId}/assignments/save`, { assignments }))
  },
  async setLock(planId, participantId, locked) {
    return unwrap(http.post(`/plans/${planId}/participants/${participantId}/lock`, undefined, {
      params: { locked },
    }))
  },
  async addParticipant(meetingId, data) {
    return unwrap(http.post(`/meetings/${meetingId}/participants`, data))
  },
  async deleteParticipant(meetingId, participantId) {
    return unwrap(http.post(`/meetings/${meetingId}/participants/${participantId}/delete`))
  },
  async updateAttendance(meetingId, participantId, attendanceStatus) {
    return unwrap(http.post(`/meetings/${meetingId}/participants/${participantId}/attendance`, {
      attendanceStatus,
    }))
  },
  async createVersion(planId, data) {
    return unwrap(http.post(`/plans/${planId}/versions`, data))
  },
  async restoreVersion(planId, versionId) {
    return unwrap(http.post(`/plans/${planId}/versions/${versionId}/restore`))
  },
  async versionSnapshot(planId, versionId) {
    return unwrap(http.get(`/plans/${planId}/versions/${versionId}`))
  },
  async importTemplate() {
    return raw(http.get(importContract.templatePath, { responseType: 'arraybuffer' }))
  },
  async previewImport(meetingId, file) {
    const form = new FormData()
    form.append('file', file)
    return unwrap(
      http.post(importContract.previewPath(meetingId), form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      }),
    )
  },
  async commitImport(meetingId, token) {
    return unwrap(http.post(importContract.commitPath(meetingId, token)))
  },
  async exportFile(meetingId, type, versionId) {
    return raw(
      http.get(`/meetings/${meetingId}/exports/${type}`, {
        responseType: 'arraybuffer',
        params: { versionId },
      }),
    )
  },
  async venues() {
    return unwrap(http.get('/venues'))
  },
  async venue(id) {
    return unwrap(http.get(`/venues/${id}`))
  },
  async createVenue(data) {
    return unwrap(http.post('/venues', data))
  },
  async updateVenue(id, data) {
    return unwrap(http.post(`/venues/${id}/update`, data))
  },
  async deleteVenue(id) {
    return unwrap(http.post(`/venues/${id}/delete`))
  },
  async createMeeting(name, venueTemplateId) {
    return unwrap(http.post('/meetings', { name, venueTemplateId }))
  },
}
