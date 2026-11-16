-- MVP v2: remove obsolete quota columns without changing accumulated counts.
use si1v3rApi;

set @mvp_ddl = (select if(count(*) > 0,
    'alter table user_interface_info drop column leftNum', 'DO 0')
    from information_schema.columns
    where table_schema = database() and table_name = 'user_interface_info' and column_name = 'leftNum');
prepare mvp_stmt from @mvp_ddl;
execute mvp_stmt;
deallocate prepare mvp_stmt;

set @mvp_ddl = (select if(count(*) > 0,
    'alter table user_interface_info drop column status', 'DO 0')
    from information_schema.columns
    where table_schema = database() and table_name = 'user_interface_info' and column_name = 'status');
prepare mvp_stmt from @mvp_ddl;
execute mvp_stmt;
deallocate prepare mvp_stmt;
