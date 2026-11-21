# wxpay-v3-spring-boot-starter

基于 OkHttp 实现的微信支付 V3 **直连商户（普通商户）** Spring Boot Starter，适配 **Spring Boot 3.x**（jakarta）与 **JDK 17+**。

> 本项目基于 [rwocj/wxpay-v3-spring-boot-starter](https://github.com/rwocj/wxpay-v3-spring-boot-starter) 二次改造，调整了包名与配置结构，并补充了直连商户退款、微信支付公钥验签等能力，差异见[与上游的差异](#与上游的差异)。

## 特性

- 自动配置：引入依赖并配置 `wx.*` 即可使用
- 下单：JSAPI / APP / Native / H5，自动填充 appId、mchId、notifyUrl
- 查询订单、关闭订单
- 直连商户退款（`/v3/refund/domestic/refunds`）及退款结果异步通知验签解密
- 支付/退款回调验签 + AES-256-GCM 自动解密
- 应答验签支持「微信支付公钥」与「平台证书」两种模式，可组合回退
- 请求自动签名（`WECHATPAY2-SHA256-RSA2048`），由 OkHttp 拦截器完成
- 未封装的接口可直接调用 `get` / `post`

## 环境要求

| 依赖         | 版本                         |
| ------------ | ---------------------------- |
| JDK          | 17+                          |
| Spring Boot  | 3.x（jakarta.servlet）       |
| Maven        | 3.6+                         |

## 快速开始

### 1. 引入依赖

```xml
<dependency>
  <groupId>com.coderyang</groupId>
  <artifactId>wxpay-v3-spring-boot-starter</artifactId>
  <version>0.6</version>
</dependency>
```

> 当前版本未发布到中央仓库，需先在本地构建安装：
>
> ```bash
> mvn clean install -Dmaven.test.skip=true
> ```

### 2. 配置

```yaml
wx:
  app-id: 微信AppId
  pay:
    mch-id: 商户号
    api-v3-key: 32字节APIv3密钥
    certificate-serial-no: 商户API证书序列号
    private-key-path: classpath:/cert/apiclient_key.pem
    notify-url: https://your-domain/wxpay/notify
    # 退款回调地址（预留，当前版本需在退款请求中显式传 notifyUrl）
    refund-notify-url: https://your-domain/wxpay/refund-notify
    # 可选：微信支付公钥模式（配置后优先使用公钥验签）
    # public-key-id: PUB_KEY_ID_xxxxxxxx
    # public-key-path: classpath:/cert/pub_key.pem
```

配置项说明：

| 配置项                            | 必填 | 说明                                                                                                                       |
| --------------------------------- | ---- | -------------------------------------------------------------------------------------------------------------------------- |
| `wx.app-id`                       | 是   | 微信 AppId，下单请求未指定时自动填充                                                                                       |
| `wx.pay.mch-id`                   | 是   | 直连商户号，下单请求未指定时自动填充                                                                                       |
| `wx.pay.api-v3-key`               | 是   | 32 字节 APIv3 密钥，用于回调解密与平台证书下载                                                                             |
| `wx.pay.certificate-serial-no`    | 是   | 商户 API 证书序列号，写入请求 `Authorization` 的 `serial_no`                                                               |
| `wx.pay.private-key-path`         | 是   | 商户 API 证书私钥 `apiclient_key.pem`，支持 `classpath:` / `file:` / 绝对路径 / PEM 原文。**未配置时 Starter 不会自动装配** |
| `wx.pay.notify-url`               | 否   | 支付结果通知地址，下单请求未指定时自动填充                                                                                 |
| `wx.pay.refund-notify-url`        | 否   | 预留配置，当前版本需通过 `WxOrdinaryRefundRequest.notifyUrl` 指定退款回调地址                                              |
| `wx.pay.public-key-id`            | 否   | 微信支付公钥 ID（`PUB_KEY_ID_xxx`），与 `public-key-path` 同时配置才会启用公钥验签                                         |
| `wx.pay.public-key-path`          | 否   | 微信支付公钥（PEM），支持路径或原文                                                                                        |

> 应答验签至少需要配置一种：`public-key-id` + `public-key-path`，或 `api-v3-key`。

### 3. 使用

注入 `WxPayV3Service`：

```java
@Autowired
private WxPayV3Service wxPayV3Service;
```

#### JSAPI 下单

```java
WxCreateOrderRequest request = WxCreateOrderRequest.jsapiOrder(
        "测试商品",
        "ORDER_20260101_000001",
        new WxCreateOrderRequest.Amount(1), // 单位：分
        new Payer("用户openid"));

JSAPICreateOrderRes res = wxPayV3Service.createJSAPIOrder(request);
// 前端 wx.requestPayment(res) 调起支付
```

#### APP / Native / H5 下单

```java
WxCreateOrderRequest request = new WxCreateOrderRequest(
        "测试商品",
        "ORDER_20260101_000002",
        OrderType.NATIVE,                     // APP / JSAPI / NATIVE / H5
        new WxCreateOrderRequest.Amount(1),
        null);                                // payer，JSAPI 支付时必填

// 可选：request.setNotifyUrl("https://your-domain/wxpay/notify");

String result = wxPayV3Service.createOrder(request);
// app、jsapi -> prepay_id；nativeS -> code_url；h5 -> h5_url
```

> APP 调起支付所需的 `paySign` 可通过 `wxPayV3Service.appPaySign(timestamp, nonceStr, prepayId)` 生成。

#### 查询与关单

```java
WxPayResult byOutTradeNo = wxPayV3Service.queryOrderByOutTradeId("ORDER_20260101_000001");
WxPayResult byTransactionId = wxPayV3Service.queryOrderByTransactionsId("4200000000202601010000000000");

wxPayV3Service.closeOrder("ORDER_20260101_000001");
```

#### 直连商户退款

```java
WxOrdinaryRefundRequest refund = new WxOrdinaryRefundRequest();
refund.setOutTradeNo("ORDER_20260101_000001");     // 与 transactionId 二选一
refund.setOutRefundNo("REFUND_20260101_000001");
refund.setReason("用户申请退款");
refund.setNotifyUrl("https://your-domain/wxpay/refund-notify");

WxOrdinaryRefundRequest.Amount amount = new WxOrdinaryRefundRequest.Amount();
amount.setRefund(1);                               // 退款金额，单位：分
amount.setTotal(1);                                // 原订单金额，单位：分
refund.setAmount(amount);

WxOrdinaryRefundRes res = wxPayV3Service.ordinaryRefund(refund);
```

#### 支付 / 退款回调

```java
@PostMapping("/wxpay/notify")
public ResponseEntity<Void> payNotify(HttpServletRequest request) {
    // 验签失败会抛出 WxPayException
    WxPayResult result = wxPayV3Service.validateAndDecryptPayNotification(request);
    // TODO 幂等处理业务
    return ResponseEntity.noContent().build();
}

@PostMapping("/wxpay/refund-notify")
public ResponseEntity<Void> refundNotify(HttpServletRequest request) {
    WxOrdinaryRefundRes result = wxPayV3Service.validateAndDecryptRefundNotification(request);
    // TODO 处理退款结果
    return ResponseEntity.noContent().build();
}
```

> 回调接口需返回 HTTP 200/204（无响应体），返回其他状态码微信会重试。
>
> 若请求体已被网关/过滤器提前读取，或不在 Servlet 环境，可改用重载
> `validateAndDecryptPayNotification(WxHeaders headers, String body)` /
> `validateAndDecryptRefundNotification(WxHeaders headers, String body)`。

#### 调用未封装的接口

```java
String res = wxPayV3Service.get("https://api.mch.weixin.qq.com/v3/...");
String res2 = wxPayV3Service.post("https://api.mch.weixin.qq.com/v3/...", requestBody);
```

## 验签机制

- **请求签名**：`WxPayV3OkHttpInterceptor` 使用 `private-key-path` 加载的私钥，按
  `method\nurl\ntimestamp\nnonce\nbody\n` 生成 SHA256withRSA 签名，写入 `Authorization`。
- **应答与回调验签**：`DefaultV3Validator` 校验 `Wechatpay-Timestamp` 处于 5 分钟窗口内，
  再按 `timestamp\nnonce\nbody\n` 验签。
- **验签器选择**（`WxPayV3AutoConfiguration`）：
  1. 配置了 `public-key-id` + `public-key-path` → `PublicKeySignVerifier`（公钥模式，优先）
  2. 配置了 `api-v3-key` → `DefaultCertificatesSignVerifier`（平台证书模式，自动下载证书，默认 12 小时刷新）
  3. 两者都配置 → `CompositeSignVerifier`，任一通过即视为有效
- 回调解密使用 `api-v3-key` 进行 AES-256-GCM 解密，因此 `api-v3-key` 必须配置。

## 与上游的差异

| 项目         | 上游 0.7                             | 本项目 0.6                                      |
| ------------ | ------------------------------------ | ----------------------------------------------- |
| groupId      | `top.rwocj`                          | `com.coderyang`                                 |
| 包名         | `top.rwocj.wx.pay.*`                 | `com.coderyang.wx.*`                            |
| core 包      | `top.rwocj.wx.pay.core`              | `com.coderyang.wx.base`                         |
| 配置结构     | 平铺 `wx.pay.*`                      | `wx.app-id` + `wx.pay.*` 嵌套                   |
| 证书加载     | `p12-path`                           | `private-key-path`（PEM，路径或原文）           |
| 应答验签     | 平台证书                             | 微信支付公钥 + 平台证书（可回退）               |
| 退款         | 电商平台退款                         | 直连商户退款 + 退款通知解密                     |
| Servlet API  | `jakarta.servlet`                    | 同左                                            |
| 自动配置     | `AutoConfiguration.imports`          | 同左                                            |

## 项目结构

```text
com.coderyang.wx
├── base            # 签名、验签、请求头、异常
├── configuration   # 自动配置与 OkHttpClient 定制
├── dto             # 请求/响应对象
├── enums           # 订单类型、交易状态/类型
├── properties      # wx.* 配置属性
├── service         # WxPayV3Service
└── util            # PEM/AES/签名等工具
```

## License

[Apache License 2.0](LICENSE)
