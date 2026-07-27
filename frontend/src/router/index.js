import { createRouter, createWebHistory } from 'vue-router'
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      meta: { requiresSession: true },
    },
    {
      path: '/workbench/:meetingId?',
      name: 'workbench',
      component: () => import('@/views/WorkbenchView.vue'),
      meta: { requiresSession: true },
    },
    {
      path: '/venues',
      name: 'venue-manage',
      component: () => import('@/views/VenueLibraryView.vue'),
      meta: { requiresSession: true },
    },
    {
      path: '/venues/select',
      name: 'venue-select',
      component: () => import('@/views/VenueLibraryView.vue'),
      meta: { requiresSession: true },
    },
    {
      path: '/venues/new',
      name: 'venue-new',
      component: () => import('@/views/VenueCreateView.vue'),
      meta: { requiresSession: true },
    },
    {
      path: '/venues/:venueId/layout/edit',
      name: 'venue-layout-edit',
      component: () => import('@/views/VenueLayoutEditorView.vue'),
      meta: { requiresSession: true },
    },
  ],
})
export default router
