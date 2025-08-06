import { extend } from 'umi-request';
import { notification } from 'antd';

const request = extend({
  // 默认配置
  prefix: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 请求拦截器
request.interceptors.request.use((url, options) => {
  // 可以在这里添加请求头或其他配置
  return { url, options };
});

// 响应拦截器
request.interceptors.response.use(
  async (response) => {
    const data = await response.clone().json();
    if (response.status >= 200 && response.status < 300) {
      return data;
    }
    // 错误处理
    notification.error({
      message: '请求错误',
      description: data.message || '请求失败，请重试',
    });
    throw new Error(data.message || '请求失败');
  },
  (error) => {
    notification.error({
      message: '网络错误',
      description: '请检查您的网络连接',
    });
    throw error;
  }
);

export const requestConfig = request;