import {
  getMyKeys,
  getMyInvokeStats,
  regenerateMyKeys,
} from '@/services/si1v3rApi-backend/account';
import { PageContainer } from '@ant-design/pro-components';
import { Button, Card, Descriptions, message, Modal, Space, Table, Typography } from 'antd';
import React, { useEffect, useState } from 'react';

const AccountCenter: React.FC = () => {
  const [keys, setKeys] = useState<API.UserKeyVO>();
  const [visible, setVisible] = useState(false);
  const [stats, setStats] = useState<API.MyInvokeStatVO[]>([]);
  const [keyLoading, setKeyLoading] = useState(false);
  const [statsLoading, setStatsLoading] = useState(false);

  const loadKeys = async () => {
    setKeyLoading(true);
    try {
      setKeys((await getMyKeys()).data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Failed to load keys');
    } finally {
      setKeyLoading(false);
    }
  };
  const loadStats = async () => {
    setStatsLoading(true);
    try {
      setStats((await getMyInvokeStats()).data ?? []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Failed to load statistics');
    } finally {
      setStatsLoading(false);
    }
  };
  useEffect(() => {
    loadKeys();
    loadStats();
  }, []);

  const regenerate = () =>
    Modal.confirm({
      title: 'Regenerate API keys?',
      content: 'Your old keys will stop working immediately.',
      okText: 'Regenerate',
      cancelText: 'Cancel',
      okButtonProps: { danger: true },
      onOk: async () => {
        setKeyLoading(true);
        try {
          setKeys((await regenerateMyKeys()).data);
          setVisible(false);
          message.success('Keys regenerated');
        } catch (error) {
          message.error(error instanceof Error ? error.message : 'Failed to regenerate keys');
          throw error;
        } finally {
          setKeyLoading(false);
        }
      },
    });

  return (
    <PageContainer title="Account center">
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Card
          title="My API keys"
          loading={keyLoading}
          extra={<Button onClick={loadKeys}>Refresh keys</Button>}
        >
          {keys && (
            <Descriptions column={1}>
              <Descriptions.Item label="assessKey">
                <Typography.Text copyable>{keys.assessKey}</Typography.Text>
              </Descriptions.Item>
              <Descriptions.Item label="secretKey">
                <Space wrap>
                  <Typography.Text code copyable={{ text: keys.secretKey }}>
                    {visible ? keys.secretKey : '••••••••••••••••••••••••••••••••'}
                  </Typography.Text>
                  <Button onClick={() => setVisible(!visible)}>
                    {visible ? 'Hide secret' : 'Show secret'}
                  </Button>
                </Space>
              </Descriptions.Item>
            </Descriptions>
          )}
          <Button danger onClick={regenerate} loading={keyLoading}>
            Regenerate keys
          </Button>
        </Card>
        <Card
          title="My invocation statistics"
          extra={<Button onClick={loadStats}>Refresh statistics</Button>}
        >
          <Table<API.MyInvokeStatVO>
            loading={statsLoading}
            rowKey="interfaceInfoId"
            dataSource={stats}
            scroll={{ x: true }}
            columns={[
              {
                title: 'API',
                dataIndex: 'interfaceName',
                render: (name: string | null) => name ?? 'Deleted interface',
              },
              { title: 'URL', dataIndex: 'url' },
              { title: 'Method', dataIndex: 'method' },
              { title: 'Total calls', dataIndex: 'totalNum' },
              { title: 'Last invocation', dataIndex: 'updateTime' },
            ]}
          />
        </Card>
      </Space>
    </PageContainer>
  );
};
AccountCenter.displayName = 'AccountCenter';
export default AccountCenter;
