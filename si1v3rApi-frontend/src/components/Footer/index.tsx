import { GithubOutlined } from '@ant-design/icons';
import { DefaultFooter } from '@ant-design/pro-components';
import '@umijs/max';
import React from 'react';

const Footer: React.FC = () => {
  const defaultMessage = 'si1v3r';
  const currentYear = new Date().getFullYear();
  return (
    <DefaultFooter
      style={{
        background: 'none',
      }}
      copyright={`${currentYear} ${defaultMessage}`}
      links={[
        {
          key: 'codeNav',
          title: 'si1v3r\'s blog',
          href: 'https://zthlly.github.io',
          blankTarget: true,
        },
        {
          key: 'github',
          title: (
            <>
              <GithubOutlined /> si1v3r Code
            </>
          ),
          href: 'https://github.com/zthlly',
          blankTarget: true,
        },
      ]}
    />
  );
};
export default Footer;
