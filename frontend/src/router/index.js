import { createRouter, createWebHistory } from 'vue-router'
import Layout from '../views/Layout.vue'

const routes = [
  {
    path: '/',
    component: Layout,
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('../views/Home.vue'),
      },
      {
        path: 'projects',
        name: 'Projects',
        component: () => import('../views/Projects.vue'),
      },
      {
        path: 'help',
        name: 'Help',
        component: () => import('../views/Help.vue'),
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('../views/Settings.vue'),
      },
      {
        path: 'prompt-templates',
        name: 'PromptTemplates',
        component: () => import('../views/PromptTemplates.vue'),
      },
      {
        path: 'project/:id',
        name: 'ProjectDetail',
        component: () => import('../views/ProjectDetail.vue'),
      },
      {
        path: 'written-test/start/:projectId',
        name: 'WrittenTestStart',
        component: () => import('../views/written-test/WrittenTestStart.vue'),
      },
      {
        path: 'written-test/exam/:sessionId',
        name: 'WrittenTestExam',
        component: () => import('../views/written-test/WrittenTestExam.vue'),
      },
      {
        path: 'written-test/result/:sessionId',
        name: 'WrittenTestResult',
        component: () => import('../views/written-test/WrittenTestResult.vue'),
      },
      {
        path: 'mock-interview/:projectId?',
        name: 'MockInterviewChat',
        component: () => import('../views/mock-interview/MockInterviewChat.vue'),
      },
      {
        path: 'analysis/session/:sessionId',
        name: 'SessionReport',
        component: () => import('../views/analysis/SessionReport.vue'),
      },
      {
        path: 'analysis/history/:projectId',
        name: 'HistoryReport',
        component: () => import('../views/analysis/HistoryReport.vue'),
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
