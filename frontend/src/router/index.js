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
    { path: '/venues', name: 'venues', component: () => import('@/views/VenueLibraryView.vue') },
    {
      path: '/venues/new',
      name: 'venue-new',
      component: () => import('@/views/VenueDesignerView.vue'),
    },
    {
      path: '/venues/:venueId/edit',
      name: 'venue-edit',
      component: () => import('@/views/VenueDesignerView.vue'),
    },
  ],
})
export default router
