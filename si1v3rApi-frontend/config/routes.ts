export default [
  { path: '/', icon: 'smile', component: './Index/Welcome', name: 'Welcome' },
  { path: '/Interface_Info/:id', icon: 'smile', component: './InterfaceInfo', name: '查看接口',hideInMenu: true },
  {
    path: '/user',
    layout: false,
    routes: [
      { path: '/user/login', component: './User/Login' },
      { path: '/user/register', component: './User/Register' },
    ],
  },

  {
    path: '/admin',
    icon: 'crown',
    name: 'Management',
    access: 'canAdmin',
    routes: [
      { path: '/admin', redirect: '/admin/user' },
      { icon: 'table', path: '/admin/user', component: './Admin/User', name: 'User Management' },
      { icon: 'table', path: '/admin/api', component: './Admin/Api', name: 'API Management' },
    ],
  },

  { path: '/', redirect: '/welcome' },
  { path: '*', layout: false, component: './404' },
];
