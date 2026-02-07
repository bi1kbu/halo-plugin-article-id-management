import { definePlugin } from '@halo-dev/ui-shared'
import HomeView from './views/HomeView.vue'
import MenuLayoutView from './views/MenuLayoutView.vue'
import { IconPlug } from '@halo-dev/components'
import { markRaw } from 'vue'

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: 'Root',
      route: {
        path: '/article-id-management',
        name: 'ArticleIdManagement',
        redirect: '/article-id-management/query',
        component: MenuLayoutView,
        meta: {
          title: '文件编号',
          searchable: true,
          menu: {
            name: '文件编号',
            group: '内容治理',
            icon: markRaw(IconPlug),
            priority: 10,
          },
          permissions: ['article-id-management:view'],
        },
        children: [
          {
            path: 'query',
            name: 'ArticleIdManagementQuery',
            component: HomeView,
            props: { mode: 'query' },
            meta: {
              title: '编号查询',
              searchable: true,
              permissions: ['article-id-management:view'],
            },
          },
          {
            path: 'create',
            name: 'ArticleIdManagementCreate',
            component: HomeView,
            props: { mode: 'create' },
            meta: {
              title: '编号创建',
              searchable: true,
              menu: {
                name: '编号创建',
                priority: 20,
              },
              permissions: ['article-id-management:create'],
            },
          },
          {
            path: 'update',
            name: 'ArticleIdManagementUpdate',
            component: HomeView,
            props: { mode: 'update' },
            meta: {
              title: '编号修改',
              searchable: true,
              menu: {
                name: '编号修改',
                priority: 30,
              },
              permissions: ['article-id-management:modify'],
            },
          },
          {
            path: 'manage',
            name: 'ArticleIdManagementManage',
            component: HomeView,
            props: { mode: 'manage' },
            meta: {
              title: '规则管理',
              searchable: true,
              menu: {
                name: '规则管理',
                priority: 40,
              },
              permissions: ['article-id-management:manage'],
            },
          },
          {
            path: 'logs',
            name: 'ArticleIdManagementLogs',
            component: HomeView,
            props: { mode: 'logs' },
            meta: {
              title: '操作日志',
              searchable: true,
              menu: {
                name: '操作日志',
                priority: 50,
              },
              permissions: ['article-id-management:view'],
            },
          },
        ],
      },
    },
  ],
  extensionPoints: {},
})
