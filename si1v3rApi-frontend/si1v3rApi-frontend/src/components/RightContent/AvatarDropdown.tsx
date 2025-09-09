import React from 'react';
import { Menu, Dropdown } from 'antd';
import { UserOutlined, LogoutOutlined } from '@ant-design/icons';

const AvatarDropdown: React.FC = () => {
  const handleMenuClick = (e: any) => {
    if (e.key === 'logout') {
      // Handle logout logic here
    }
  };

  const menu = (
    <Menu onClick={handleMenuClick}>
      <Menu.Item key="profile" icon={<UserOutlined />}>
        Profile
      </Menu.Item>
      <Menu.Item key="logout" icon={<LogoutOutlined />}>
        Logout
      </Menu.Item>
    </Menu>
  );

  return (
    <Dropdown overlay={menu} trigger={['click']}>
      <a onClick={e => e.preventDefault()}>
        <img
          src="https://gw.alipayobjects.com/zos/rmsportal/BiazfanxmamNRoxxVxka.png"
          alt="avatar"
          style={{ width: 32, height: 32, borderRadius: '50%' }}
        />
      </a>
    </Dropdown>
  );
};

export default AvatarDropdown;