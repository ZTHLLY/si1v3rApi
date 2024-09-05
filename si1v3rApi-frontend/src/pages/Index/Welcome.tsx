import { PageContainer } from '@ant-design/pro-components';
import { useModel } from '@umijs/max';
import { Card, theme } from 'antd';
import React, { useEffect, useState } from 'react';
import { Avatar, Button, List, Skeleton,message } from 'antd';
import { listInterfaceInfoByPageUsingPost } from '@/services/si1v3rApi-backend/interfaceController';
import { Item } from 'rc-menu';



const Welcome: React.FC = () => {

  const [loading, setLoading] = useState(false);
  const [list, setList] = useState<API.InterfaceInfo[]>([]);
  const [total,setTotal]=useState<number>();

  const { token } = theme.useToken();
  const { initialState } = useModel('@@initialState');


  const loadData = async (current=1 , pageSize =10 ) =>{
    setLoading(true);
    try {
     const res = await listInterfaceInfoByPageUsingPost({
       current,pageSize
      });
      setList(res?.data?.records ?? []);
      setTotal(res?.data?.total ?? 0);

    } catch (error: any) {

      message.error('请求失败,'+error.message);
      return false;
    }
    setLoading(false);
  }

  useEffect(()=>{
    loadData();
  },[])


  
  return (
    <PageContainer>
      <List
      className="my-list"
      loading={loading}
      itemLayout="horizontal"
      dataSource={list}
      renderItem={item => {
      const apilink=`/Interface_Info/${item.id}`;

      return(
        <List.Item
          actions={[<a href={apilink} >check</a>]}
        >
          <Skeleton avatar title={false} loading={loading} active>
            <List.Item.Meta
              title={<a href={apilink}>{item.name}</a>}
              description={item.description}
            />
          </Skeleton>
        </List.Item>
        )
      }
    }

      pagination={
        {
          pageSize: 10,
          total,
          onChange(page,pageSize){
            loadData(page,pageSize);
          }
        }
      }
    />
    </PageContainer>
  );
};

export default Welcome;
