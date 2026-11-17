-- 仅用于开发环境；不创建用户，不生成或插入任何密钥。
use si1v3rApi;

-- userId = 0 是管理员创建人的占位值，部署时应替换成真实管理员 ID。
-- 按路径 + 方法判重（包括逻辑删除的记录），重复运行不新增记录。
insert into interface_info (name, description, url, method, status, userId)
select seed.name, seed.description, seed.url, seed.method, 1, 0
from (
    select 'virtualApi GET' as name, 'virtualApi 基础接口' as description,
           '/api/' as url, 'GET' as method
    union all
    select 'virtualApi name', 'virtualApi 名称接口', '/api/name', 'POST'
    union all
    select 'virtualApi user', 'virtualApi 用户接口', '/api/user', 'POST'
) as seed
where not exists (
    select 1 from interface_info existing
    where existing.url = seed.url and existing.method = seed.method
);
