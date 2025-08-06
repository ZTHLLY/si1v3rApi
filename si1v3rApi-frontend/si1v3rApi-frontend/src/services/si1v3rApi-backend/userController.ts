import { request } from '@/utils/request';
import type { API } from '@/types/index';

export async function getLoginUserUsingGet(): Promise<API.LoginUserVO> {
  return request('/api/user/current', {
    method: 'GET',
  });
}