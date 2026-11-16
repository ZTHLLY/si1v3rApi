import {
  getInterfaceInfoVoByIdUsingGet,
  invokeInterfaceInfoUsingPost,
} from '@/services/si1v3rApi-backend/interfaceController';
import { PageContainer } from '@ant-design/pro-components';
import { useParams } from '@umijs/max';
import { Alert, Button, Card, Descriptions, Form, Input, message, Space, Tag } from 'antd';
import React, { useEffect, useState } from 'react';

const InterfaceInfo: React.FC = () => {
  const { id } = useParams();
  const [loading, setLoading] = useState(false);
  const [invoking, setInvoking] = useState(false);
  const [data, setData] = useState<API.InterfaceInfoVO>();
  const [result, setResult] = useState<API.InvokeResultVO>();

  useEffect(() => {
    let active = true;
    setLoading(true);
    getInterfaceInfoVoByIdUsingGet({ id: Number(id) })
      .then((res) => {
        if (active) setData(res.data);
      })
      .catch((error: Error) => {
        if (active) message.error(error.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [id]);

  const invoke = async (values: { requestParams?: string }) => {
    setInvoking(true);
    setResult(undefined);
    try {
      const res = await invokeInterfaceInfoUsingPost({
        id: Number(id),
        requestParams: values.requestParams,
      });
      setResult(res.data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Invocation failed');
    } finally {
      setInvoking(false);
    }
  };

  return (
    <PageContainer title="API details">
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Card loading={loading}>
          {data ? (
            <Descriptions title={data.name} column={1}>
              <Descriptions.Item label="Status">
                {data.status === 1 ? 'Online' : 'Offline'}
              </Descriptions.Item>
              <Descriptions.Item label="Method">{data.method}</Descriptions.Item>
              <Descriptions.Item label="URL">{data.url}</Descriptions.Item>
              <Descriptions.Item label="Description">{data.description}</Descriptions.Item>
              <Descriptions.Item label="Request parameters">{data.requestParams}</Descriptions.Item>
              <Descriptions.Item label="Request headers">{data.requestHeader}</Descriptions.Item>
              <Descriptions.Item label="Response headers">{data.responseHeader}</Descriptions.Item>
              <Descriptions.Item label="Created">{data.createTime}</Descriptions.Item>
            </Descriptions>
          ) : (
            'Interface not found'
          )}
        </Card>
        <Card title="Online debugger">
          <Form layout="vertical" onFinish={invoke}>
            <Form.Item
              label="requestParams"
              name="requestParams"
              help="GET / DELETE: flat JSON query object. POST / PUT: JSON body."
            >
              <Input.TextArea rows={5} placeholder={'{"name":"S5"}'} />
            </Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              loading={invoking}
              disabled={data?.status !== 1}
            >
              Invoke
            </Button>
          </Form>
        </Card>
        {result && (
          <Alert
            type={result.status >= 200 && result.status < 300 ? 'success' : 'error'}
            message={
              <Tag color={result.status >= 200 && result.status < 300 ? 'green' : 'red'}>
                Status {result.status}
              </Tag>
            }
            description={
              <pre style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{result.body}</pre>
            }
          />
        )}
      </Space>
    </PageContainer>
  );
};
InterfaceInfo.displayName = 'InterfaceInfo';
export default InterfaceInfo;
