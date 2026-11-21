## 基于Okhttp实现的微信支付v3接口的Spring Boot Starter

> 本项目基于 [rwocj/wxpay-v3-spring-boot-starter](https://github.com/rwocj/wxpay-v3-spring-boot-starter) 二次改造，适配 **Spring Boot 3.x**（jakarta）+ **JeecgBoot 3.9.1** 的包结构。

#### 改造说明

| 项目         | 原版（0.7）                          | 改造后（0.6）                                   |
| ------------ | ------------------------------------ | ----------------------------------------------- |
| groupId      | `top.rwocj`                          | `com.coderyang`                                 |
| 包名         | `top.rwocj.wx.pay.*`                 | `com.coderyang.wx.*`                            |
| core 包      | `top.rwocj.wx.pay.core`              | `com.coderyang.wx.base`                         |
| WxProperties | 平铺结构，prefix=`wx.pay`            | 嵌套结构（`appId` + `Pay` 子对象），prefix=`wx` |
| Servlet API  | `jakarta.servlet`（Spring Boot 3.x） | 同左                                            |
| 自动配置     | `AutoConfiguration.imports`          | 同左                                            |

#### 目前只完成直连商户(普通商户)基础支付，包含下单、查询、关单、支付通知、调用等

#### 使用方法

- 1、pom引入组件

```xml
<dependency>
  <groupId>com.coderyang</groupId>
  <artifactId>wxpay-v3-spring-boot-starter</artifactId>
  <version>0.6</version>
</dependency>
```

> 需先在本地构建安装：`mvn clean install -Dmaven.test.skip=true`

- 2、配置必要属性（application.yml）

```yaml
wx:
  app-id: 微信appId
  pay:
    mch-id: 商户id
    api-v3-key: v3密钥
    certificate-serial-no: API证书序列号
    private-key-path: classpath:/cert/apiclient_key.pem
    notify-url: https://xxx/支付结果通知地址
    refund-notify-url: https://xxx/退款通知地址(可选)
    # 可选：微信支付公钥模式（配置后优先用公钥验签，未配置则自动下载平台证书）
    # public-key-id: PUB_KEY_ID_xxx
    # public-key-path: classpath:/cert/pub_key.pem
```

- 3、在业务Service/Controller中注入WxPayV3Service即可
  - jsapi支付: JSAPICreateOrderRes res = WxPayV3Service.createJSAPIOrder(createOrderRequest)
  - 其他支付：String res = WxPayV3Service.createOrder(createOrderRequest)，返回值根据类型不同而不同,参见OrderType
  - 支付回调验签与解密:WxPayResult payResult = WxPayV3Service.validateAndDecryptPayNotification(request)
  - 其他未封装的接口可调用: WxPayV3Service.post(String url, Object requestBody)和WxPayV3Service.get(String url)
