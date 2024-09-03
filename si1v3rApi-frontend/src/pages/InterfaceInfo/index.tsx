import { getInterfaceInfoVoByIdUsingGet } from '@/services/si1v3rApi-backend/interfaceController';
import { PageContainer } from '@ant-design/pro-components';
import { useModel, useParams } from '@umijs/max';
import { message, theme } from 'antd';
import React, { useEffect, useState } from 'react';

const Welcome: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [data, setdata] = useState<API.InterfaceInfo>();
  const params = useParams();
  const { token } = theme.useToken();
  const { initialState } = useModel('@@initialState');

  const loadData = async () => {
    if (!params.id) {
      message.error('参数不存在');
      return;
    }

    setLoading(true);
    try {
      const res = await getInterfaceInfoVoByIdUsingGet({
        id: Number(params.id),
      });

      setdata(res.data);
    } catch (error: any) {
      message.error('请求失败,' + error.message);
      return false;
    }
    setLoading(false);
  };

  useEffect(() => {
    loadData();
  }, []);

  return (
    <PageContainer title="check the detail of the Interface">
      啊这是要隐藏的页面
      {JSON.stringify(data)}
    </PageContainer>
  );
};

export default Welcome;
