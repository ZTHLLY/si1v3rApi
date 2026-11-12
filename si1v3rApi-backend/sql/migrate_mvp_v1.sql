-- MVP v1：在已有 si1v3rApi 旧库上执行；不创建数据库，不修改已有业务数据。
-- MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，先查询元数据再执行 DDL。
use si1v3rApi;

set @mvp_ddl = (select if(count(*) = 0, 'alter table `user` add column assessKey varchar(256) null comment ''API access key'' after userRole', 'DO 0')
    from information_schema.columns
    where table_schema = database() and table_name = 'user' and column_name = 'assessKey');
prepare mvp_stmt from @mvp_ddl;
execute mvp_stmt;
deallocate prepare mvp_stmt;

set @mvp_ddl = (select if(count(*) = 0, 'alter table `user` add column secretKey varchar(512) null comment ''API secret key'' after assessKey', 'DO 0')
    from information_schema.columns
    where table_schema = database() and table_name = 'user' and column_name = 'secretKey');
prepare mvp_stmt from @mvp_ddl;
execute mvp_stmt;
deallocate prepare mvp_stmt;

set @mvp_ddl = (select if(count(*) = 0,
    'alter table `user` add unique index uk_assessKey (assessKey)', 'DO 0')
    from information_schema.statistics
    where table_schema = database() and table_name = 'user' and index_name = 'uk_assessKey');
prepare mvp_stmt from @mvp_ddl;
execute mvp_stmt;
deallocate prepare mvp_stmt;

-- 接口信息（url 保存网关路径，不包含协议和主机）
create table if not exists interface_info
(
    id             bigint auto_increment comment 'id' primary key,
    name           varchar(256)                       not null comment '名称',
    description    varchar(256)                       null comment '描述',
    url            varchar(512)                       not null comment '网关路径',
    requestParams  text                               null comment '请求参数',
    requestHeader  text                               null comment '请求头',
    responseHeader text                               null comment '响应头',
    status         int      default 0                 not null comment '0-关闭，1-开启',
    method         varchar(256)                       not null comment '请求方法（大写）',
    userId         bigint                             not null comment '创建人（管理员）',
    createTime     datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime     datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete       tinyint  default 0                 not null comment '是否删除',
    index idx_url_method (url, method)
) comment '接口信息' collate = utf8mb4_unicode_ci;

-- 用户调用接口关系
create table if not exists user_interface_info
(
    id              bigint auto_increment comment 'id' primary key,
    userId          bigint                             not null comment '调用用户 id',
    interfaceInfoId bigint                             not null comment '接口 id',
    totalNum        int      default 0                 not null comment '累计已调用次数',
    leftNum         int      default 0                 not null comment '剩余次数',
    status          int      default 0                 not null comment '0-正常，1-禁用',
    createTime      datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime      datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete        tinyint  default 0                 not null comment '是否删除',
    unique index uk_user_interface (userId, interfaceInfoId)
) comment '用户调用接口关系' collate = utf8mb4_unicode_ci;
