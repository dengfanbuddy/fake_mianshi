import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/written-test/start/:projectId',
    name: 'WrittenTestStart',
    component: () => import('../views/written-test/WrittenTestStart.vue'),
  },
  {
    path: '/written-test/exam/:sessionId',
    name: 'WrittenTestExam',
    component: () => import('../views/written-test/WrittenTestExam.vue'),
  },
  {
    path: '/written-test/result/:sessionId',
    name: 'WrittenTestResult',
    component: () => import('../views/written-test/WrittenTestResult.vue'),
  },
  {
    path: '/mock-interview/:projectId?',
    name: 'MockInterviewChat',
    component: () => import('../views/mock-interview/MockInterviewChat.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
