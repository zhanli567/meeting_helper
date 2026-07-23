export interface CurrentUser {
  id: string
  name: string
  tenantId: string
  tenantName: string
  roles: string[]
}

// 第一版使用演示会话。接入公司认证 SDK 后，只需替换这一层的数据来源。
export const currentUser: CurrentUser = Object.freeze({
  id: 'demo-secretary',
  name: '演示秘书',
  tenantId: 'demo-company',
  tenantName: '秘书团队工作区',
  roles: ['MEETING_SECRETARY'],
})
