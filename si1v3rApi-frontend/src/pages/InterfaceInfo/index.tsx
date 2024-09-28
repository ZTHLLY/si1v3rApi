import {
  getInterfaceInfoVoByIdUsingGet,
  invokeInterfaceInfoUsingPost,
} from '@/services/si1v3rApi-backend/interfaceController';
import { PageContainer } from '@ant-design/pro-components';
import { useModel, useParams } from '@umijs/max';
import { Button, Card, Descriptions, Divider, Form, Input, message, theme } from 'antd';
import React, { useEffect, useState } from 'react';

const Welcome: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [Invokeloading, setInvokeLoading] = useState(false);

  const [data, setdata] = useState<API.InterfaceInfo>();
  const [Invokers, setInvokers] = useState();
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

  const onFinish = async (data: any) => {
    setInvokeLoading(true);
    if (!params.id) {
      message.error('参数不存在');
      return;
    }
    try {
      const res = await invokeInterfaceInfoUsingPost({ id: params.id, ...data });

      //console.log(res);
      setInvokeLoading(false);
      setInvokers(res.data);
      message.success('invoke success');
      return true;
    } catch (error: any) {
      message.error('option error:', error);
    }
  };

  return (
    <PageContainer title="detail of the Interface">
      <Card>
        {data ? (
          <Descriptions title={data?.name} column={1}>
            <Descriptions.Item label="status">{data.status ? 'able' : 'closed'}</Descriptions.Item>
            <Descriptions.Item label="method">{data.method}</Descriptions.Item>
            <Descriptions.Item label="url">{data.url}</Descriptions.Item>
            <Descriptions.Item label="requestParams">{data.requestParams}</Descriptions.Item>
            <Descriptions.Item label="requestHeader">{data.requestHeader}</Descriptions.Item>
            <Descriptions.Item label="responseHeader">{data.responseHeader}</Descriptions.Item>
            <Descriptions.Item label="createTime">{data.createTime}</Descriptions.Item>
            <Descriptions.Item label="updateTime">{data.updateTime}</Descriptions.Item>
            <Descriptions.Item label="description">{data.description}</Descriptions.Item>
          </Descriptions>
        ) : (
          <>接口不存在</>
        )}
      </Card>
      <Card>
        <Form name="invoke" layout="vertical" onFinish={onFinish}>
          <Form.Item label="requestParams" name="requestParams">
            <Input.TextArea />
          </Form.Item>

          <Form.Item
            wrapperCol={{
              span: 16,
            }}
          >
            <Button type="primary" htmlType="submit">
              调用
            </Button>
          </Form.Item>
        </Form>
      </Card>
      <Divider />
      <Card>{Invokers}</Card>
    </PageContainer>
  );
};

export default Welcome;
