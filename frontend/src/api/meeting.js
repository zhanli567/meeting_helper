import { http } from './http'
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
  async createVersion(planId, data) {
    return (await http.post(`/plans/${planId}/versions`, data)).data
  },
  async restoreVersion(planId, versionId) {
    return (await http.post(`/plans/${planId}/versions/${versionId}/restore`)).data
  },
  async versionSnapshot(planId, versionId) {
    return (await http.get(`/plans/${planId}/versions/${versionId}`)).data
  },
  async importTemplates() {
    return (await http.get('/import-templates')).data
  },
  async previewImport(meetingId, templateCode, file) {
    const form = new FormData()
    form.append('file', file)
    return (
      await http.post(`/meetings/${meetingId}/imports/preview`, form, {
        params: { templateCode },
        headers: { 'Content-Type': 'multipart/form-data' },
      })
    ).data
  },
  async commitImport(meetingId, token, selectedSourceRows) {
    return (
      await http.post(`/meetings/${meetingId}/imports/${token}/commit`, { selectedSourceRows })
    ).data
  },
  async exportFile(meetingId, type, versionId) {
    return (
      await http.get(`/meetings/${meetingId}/exports/${type}`, {
        responseType: 'arraybuffer',
        params: versionId ? { versionId } : undefined,
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
