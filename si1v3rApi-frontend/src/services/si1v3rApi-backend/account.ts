import { request } from '@umijs/max';

export const getMyKeys = () => request<API.BaseResponse<API.UserKeyVO>>('/api/user/key');
export const regenerateMyKeys = () =>
  request<API.BaseResponse<API.UserKeyVO>>('/api/user/key/regenerate', { method: 'POST' });
export const getMyInvokeStats = () =>
  request<API.BaseResponse<API.MyInvokeStatVO[]>>('/api/userInterfaceInfo/my/list');
