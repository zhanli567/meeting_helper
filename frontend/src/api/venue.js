import { http, unwrap } from './http.js'

export const venueApi = {
  list: (params) => unwrap(http.get('/venues', { params })),
  locationAvailability: (location, excludeId) =>
    unwrap(
      http.get('/venues/location-availability', {
        params: { location, excludeId },
      }),
    ),
  detail: (id) => unwrap(http.get(`/venues/${id}`)),
  layout: (id) => unwrap(http.get(`/venues/${id}/layout`)),
  create: (data) => unwrap(http.post('/venues/create', data)),
  updateInfo: (id, data) => unwrap(http.post(`/venues/${id}/info/update`, data)),
  updateLayout: (id, data) => unwrap(http.post(`/venues/${id}/layout/update`, data)),
  remove: (id) => unwrap(http.post(`/venues/${id}/delete`)),
}
