import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'workbench', component: () => import('@/views/WorkbenchView.vue') },
    { path: '/venues', name: 'venues', component: () => import('@/views/VenueLibraryView.vue') },
    {
      path: '/venues/new',
      name: 'venue-new',
      component: () => import('@/views/VenueDesignerView.vue'),
    },
  ],
})

export default router
