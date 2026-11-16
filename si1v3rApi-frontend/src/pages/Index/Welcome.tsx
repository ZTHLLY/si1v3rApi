import { listInterfaceInfoVoByPageUsingPost } from '@/services/si1v3rApi-backend/interfaceController';
import { PageContainer } from '@ant-design/pro-components';
import { Link } from '@umijs/max';
import { Form, Input, Button, List, Space, Tag, message } from 'antd';
import React, { useEffect, useState } from 'react';

const Welcome: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [list, setList] = useState<API.InterfaceInfoVO[]>([]);
  const [total, setTotal] = useState(0);
  const [query, setQuery] = useState<API.InterfaceInfoQueryRequest>({ current: 1, pageSize: 10 });

  useEffect(() => {
    let active = true;
    setLoading(true);
    listInterfaceInfoVoByPageUsingPost(query)
      .then((res) => {
        if (active) {
          setList(res.data?.records ?? []);
          setTotal(res.data?.total ?? 0);
        }
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
  }, [query]);

  return (
    <PageContainer title="API catalog">
      <Form layout="inline" onFinish={(values) => setQuery({ ...query, ...values, current: 1 })}>
        <Form.Item label="Name" name="name">
          <Input allowClear />
        </Form.Item>
        <Form.Item label="Description" name="description">
          <Input allowClear />
        </Form.Item>
        <Form.Item>
          <Button type="primary" htmlType="submit">
            Search
          </Button>
        </Form.Item>
      </Form>
      <List
        loading={loading}
        dataSource={list}
        locale={{ emptyText: 'No online APIs found' }}
        renderItem={(item) => (
          <List.Item
            key={item.id}
            actions={[
              <Link key="view" to={`/interface/${item.id}`}>
                View API
              </Link>,
            ]}
          >
            <List.Item.Meta
              title={<Link to={`/interface/${item.id}`}>{item.name}</Link>}
              description={
                <Space>
                  <Tag>{item.method}</Tag>
                  <code>{item.url}</code>
                  {item.description}
                </Space>
              }
            />
          </List.Item>
        )}
        pagination={{
          current: query.current,
          pageSize: query.pageSize,
          total,
          showSizeChanger: true,
          pageSizeOptions: [10, 20],
          onChange: (current, pageSize) => setQuery({ ...query, current, pageSize }),
        }}
      />
    </PageContainer>
  );
};
Welcome.displayName = 'Welcome';
export default Welcome;
