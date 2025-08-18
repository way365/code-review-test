# 微信公众号通知配置指南

本文档详细说明如何配置微信公众号消息通知功能。

## 前置条件

1. 已注册微信公众号（服务号）
2. 公众号已通过微信认证
3. 已开通模板消息功能

## 配置步骤

### 1. 获取公众号配置信息

#### 获取AppID和AppSecret
1. 登录[微信公众平台](https://mp.weixin.qq.com)
2. 进入「开发」→「基本配置」
3. 记录以下信息：
   - **AppID(应用ID)**
   - **AppSecret(应用密钥)**

#### 获取用户OpenID
1. 进入「用户管理」
2. 找到要接收通知的用户
3. 通过接口获取用户的OpenID

### 2. 配置模板消息

#### 添加模板
1. 进入「功能」→「模板消息」
2. 点击「添加模板」
3. 选择合适的模板或申请新模板
4. 记录**模板ID**

#### 模板内容示例
```
{{title.DATA}}

任务名称：{{taskName.DATA}}
执行状态：{{status.DATA}}
执行时间：{{time.DATA}}

备注：{{remark.DATA}}
```

### 3. 修改配置文件

编辑 `src/main/resources/config.properties` 文件：

```properties
# 微信公众号配置
wechat.appid=你的AppID
wechat.appsecret=你的AppSecret
wechat.templateid=你的模板ID
wechat.openid=接收用户的OpenID
```

### 4. 获取用户OpenID

由于微信公众号的OpenID需要用户授权获取，这里提供几种方式：

#### 方式1：使用测试账号
1. 申请微信测试账号：[微信公众平台测试账号](https://mp.weixin.qq.com/debug/cgi-bin/sandbox?t=sandbox/login)
2. 获取测试账号的AppID和AppSecret
3. 在测试账号中添加模板消息

#### 方式2：通过网页授权
```java
// 示例代码：获取用户OpenID
String redirectUri = "http://yourdomain.com/callback";
String authUrl = "https://open.weixin.qq.com/connect/oauth2/authorize?" +
    "appid=" + appId +
    "&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8") +
    "&response_type=code&scope=snsapi_base&state=123#wechat_redirect";
```

#### 方式3：使用测试用户
如果只是为了测试，可以使用以下方式：
1. 关注你的公众号
2. 发送任意消息给你的公众号
3. 在公众号后台查看用户的OpenID

### 5. 测试配置

运行测试程序验证配置：

```bash
# 编译项目
mvn clean compile

# 运行测试
mvn exec:java -Dexec.mainClass="com.notification.Main"
```

## 常见问题

### 1. 获取AccessToken失败
- **原因**：AppID或AppSecret错误
- **解决**：检查配置信息是否正确

### 2. 模板消息发送失败
- **原因**：
  - 模板ID不存在
  - 用户未关注公众号
  - OpenID格式错误
  - 模板字段不匹配
- **解决**：
  - 确认模板ID正确
  - 确认用户已关注
  - 检查模板字段配置

### 3. 频率限制
- 每个用户每天最多接收**4条**模板消息
- 建议合理控制发送频率

### 4. 权限问题
- 确保公众号已通过微信认证
- 确保已开通模板消息功能

## 模板消息字段说明

### 标准字段
- `title`: 消息标题
- `taskName`: 任务名称
- `status`: 执行状态
- `time`: 执行时间
- `remark`: 备注信息

### 自定义字段
可以在模板中添加自定义字段：
```properties
wechat.template.fields=title,taskName,status,time,remark,customField
```

## 调试技巧

### 1. 查看日志
程序会输出详细的调试日志，包括：
- AccessToken获取过程
- 消息发送请求和响应
- 错误信息详情

### 2. 使用测试工具
- 微信官方提供的[接口调试工具](https://mp.weixin.qq.com/debug/)
- Postman测试模板消息接口

### 3. 错误码对照
- `40001`: invalid credential
- `40002`: invalid grant_type
- `40003`: invalid openid
- `40037`: invalid template_id
- `43101`: user refuse to accept the msg hint

## 安全建议

1. **保护密钥**：AppSecret不要提交到代码仓库
2. **使用环境变量**：生产环境建议使用环境变量配置敏感信息
3. **限制IP**：在公众号后台设置IP白名单
4. **监控日志**：定期检查发送日志和错误信息

## 扩展功能

### 1. 多用户通知
支持向多个用户发送通知：
```java
// 批量发送示例
List<String> openIds = Arrays.asList("openid1", "openid2", "openid3");
for (String openId : openIds) {
    new WeChatNotificationService(appId, appSecret, templateId, openId)
        .sendNotification(message, title);
}
```

### 2. 图文消息
支持发送图文消息：
```java
// 图文消息示例
Map<String, Object> newsMessage = new HashMap<>();
newsMessage.put("title", "任务完成通知");
newsMessage.put("description", "详细描述");
newsMessage.put("url", "http://yourdomain.com/detail");
newsMessage.put("picurl", "http://yourdomain.com/image.jpg");
```

### 3. 客服消息
支持48小时内的客服消息：
```java
// 客服消息示例
String url = "https://api.weixin.qq.com/cgi-bin/message/custom/send?access_token=" + accessToken;
```