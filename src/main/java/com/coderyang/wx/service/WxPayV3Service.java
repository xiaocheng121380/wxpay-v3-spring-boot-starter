package com.coderyang.wx.service;

import com.coderyang.wx.base.HttpServletRequestWxHeaders;
import com.coderyang.wx.base.OkHttpWxHeaders;
import com.coderyang.wx.base.Sign;
import com.coderyang.wx.base.Validator;
import com.coderyang.wx.base.WxHeaders;
import com.coderyang.wx.base.WxPayException;
import com.coderyang.wx.dto.JSAPICreateOrderRes;
import com.coderyang.wx.dto.WxCreateOrderRequest;
import com.coderyang.wx.dto.WxOrdinaryRefundRequest;
import com.coderyang.wx.dto.WxOrdinaryRefundRes;
import com.coderyang.wx.dto.WxPayResult;
import com.coderyang.wx.enums.OrderType;
import com.coderyang.wx.properties.WxProperties;
import com.coderyang.wx.util.AesUtil;
import com.coderyang.wx.util.SignUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;

/**
 * 微信支付 V3 直连商户能力封装。
 *
 * <p>包含下单（APP / JSAPI / Native / H5）、查单、关单、直连商户退款、
 * 支付与退款通知的验签解密，以及未封装接口的原始 {@code get} / {@code post} 调用。
 *
 * <p>通常由 {@code WxPayV3AutoConfiguration} 自动装配，也可以手动构造。
 */
public class WxPayV3Service {

    /**
     * 普通商户下单 url
     */
    final static String ORDER_URL = "https://api.mch.weixin.qq.com/v3/pay/transactions/";

    /**
     * 普通商户（直连）退款 url
     */
    final static String ORDINARY_REFUND_URL = "https://api.mch.weixin.qq.com/v3/refund/domestic/refunds";

    private final static Logger log = LoggerFactory.getLogger(WxPayV3Service.class);

    protected final OkHttpClient okHttpClient;

    protected final ObjectMapper objectMapper;

    protected final Validator validator;

    protected final WxProperties wxProperties;

    protected final Sign sign;

    protected final AesUtil aesUtil;

    public WxPayV3Service(OkHttpClient okHttpClient, ObjectMapper objectMapper,
                          Validator validator, WxProperties wxProperties,
                          Sign sign) {
        Assert.hasText(wxProperties.getPay().getApiV3Key(),
                "wx.pay.api-v3-key 不能为空：支付/退款回调解密必须配置 32 字节 APIv3 密钥");
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.wxProperties = wxProperties;
        this.sign = sign;
        this.aesUtil = new AesUtil(wxProperties.getPay().getApiV3Key().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 直连商户下单，适用 APP / JSAPI / Native / H5。
     * <ul>
     *     <li>APP、JSAPI：返回 {@code prepay_id}</li>
     *     <li>Native：返回 {@code code_url}</li>
     *     <li>H5：返回 {@code h5_url}</li>
     * </ul>
     *
     * @param createOrderRequest 下单请求体
     * @return 下单结果，具体含义由 {@link OrderType} 决定
     * @throws WxPayException 下单失败
     */
    public String createOrder(WxCreateOrderRequest createOrderRequest) throws WxPayException {
        Assert.notNull(createOrderRequest, "下单请求体不能为空");
        Assert.notNull(createOrderRequest.getOrderType(), "orderType 不能为空");
        if (createOrderRequest.getAppid() == null) {
            createOrderRequest.setAppid(wxProperties.getAppId());
        }
        if (createOrderRequest.getMchid() == null) {
            createOrderRequest.setMchid(wxProperties.getPay().getMchId());
        }
        if (createOrderRequest.getNotifyUrl() == null) {
            createOrderRequest.setNotifyUrl(wxProperties.getPay().getNotifyUrl());
        }
        OrderType orderType = createOrderRequest.getOrderType();
        String res = post(ORDER_URL + orderType.getUrl(), createOrderRequest);
        try {
            return orderType.parseResult(objectMapper.readTree(res));
        } catch (JsonProcessingException e) {
            throw new WxPayException("解析下单响应失败", e);
        }
    }

    /**
     * 直连商户微信退款。
     *
     * <p>对应官方接口：
     * <a href="https://pay.weixin.qq.com/docs/merchant/apis/refund/refunds/create.html">普通商户/退款申请</a>
     * <p>POST {@value #ORDINARY_REFUND_URL}
     *
     * @param refundRequest 退款请求体（{@code transactionId} 与 {@code outTradeNo} 二选一必填）
     * @return 退款结果
     * @throws WxPayException 退款失败
     */
    public WxOrdinaryRefundRes ordinaryRefund(WxOrdinaryRefundRequest refundRequest) throws WxPayException {
        Assert.notNull(refundRequest, "退款请求体不能为空");
        Assert.hasText(refundRequest.getOutRefundNo(), "out_refund_no(商户退款单号) 不能为空");
        Assert.notNull(refundRequest.getAmount(), "amount(金额) 不能为空");
        Assert.isTrue(refundRequest.getAmount().getRefund() > 0, "退款金额必须大于 0");
        Assert.isTrue(refundRequest.getAmount().getTotal() > 0, "原订单总金额必须大于 0");
        if (refundRequest.getTransactionId() == null && refundRequest.getOutTradeNo() == null) {
            throw new WxPayException("transaction_id 与 out_trade_no 必须二选一");
        }
        String res = post(ORDINARY_REFUND_URL, refundRequest);
        try {
            return objectMapper.readValue(res, WxOrdinaryRefundRes.class);
        } catch (JsonProcessingException e) {
            throw new WxPayException("解析退款响应失败", e);
        }
    }

    /**
     * 验证并解密微信退款结果异步通知。
     *
     * <p>对应回调事件：{@code REFUND.SUCCESS} / {@code REFUND.ABNORMAL} / {@code REFUND.CLOSED}。
     * <p>解密后的明文结构与 {@link WxOrdinaryRefundRes} 字段一致（含 refund_id / out_refund_no /
     * refund_status / amount 等）。
     *
     * @param request HTTP 请求
     * @return 解密后的退款结果
     * @throws WxPayException 验签失败或解密失败
     */
    public WxOrdinaryRefundRes validateAndDecryptRefundNotification(HttpServletRequest request) throws WxPayException {
        return validateAndDecryptRefundNotification(new HttpServletRequestWxHeaders(request), getRequestBody(request));
    }

    /**
     * 验证并解密退款通知（适用于 body 已被框架读取，或非 Servlet 环境）。
     *
     * @param headers 回调请求 header
     * @param body    原始请求体
     * @return 解密后的退款结果
     * @throws WxPayException 验签失败或解密失败
     */
    public WxOrdinaryRefundRes validateAndDecryptRefundNotification(WxHeaders headers, String body) throws WxPayException {
        if (!validateWxRequest(headers, body)) {
            throw new WxPayException("验签不能过，非微信支付团队的消息！");
        }
        try {
            JsonNode resource = parseResource(body);
            String plainText = aesUtil.decryptToString(
                    resource.get("associated_data").asText().getBytes(StandardCharsets.UTF_8),
                    resource.get("nonce").asText().getBytes(StandardCharsets.UTF_8),
                    resource.get("ciphertext").asText());
            return objectMapper.readValue(plainText, WxOrdinaryRefundRes.class);
        } catch (GeneralSecurityException | JsonProcessingException e) {
            throw new WxPayException(body, e);
        }
    }

    /**
     * JSAPI 下单，封装前端调起支付所需参数。
     * <a href="https://pay.weixin.qq.com/wiki/doc/apiv3/wxpay/pay/transactions/chapter3_8.shtml">jsapi下单接入文档</a>
     *
     * @param createOrderRequest 下单请求体
     * @return 下单成功后 JSAPI 调起支付需要的数据
     * @throws WxPayException 下单失败
     */
    public JSAPICreateOrderRes createJSAPIOrder(WxCreateOrderRequest createOrderRequest) throws WxPayException {
        Assert.notNull(createOrderRequest, "下单请求体不能为空");
        Assert.hasText(wxProperties.getAppId(), "wx.app-id 不能为空：JSAPI 下单必须配置 AppId");
        createOrderRequest.setOrderType(OrderType.JSAPI);
        return SignUtil.sign(createOrder(createOrderRequest), wxProperties.getAppId(), sign);
    }

    /**
     * 关单 API
     *
     * @param outTradeId 商户订单号
     * @throws WxPayException 关单失败
     */
    public void closeOrder(String outTradeId) throws WxPayException {
        Assert.hasText(outTradeId, "商户订单号不能为空");
        String url = ORDER_URL + "out-trade-no/" + outTradeId + "/close";
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("mchid", wxProperties.getPay().getMchId());
        post(url, objectNode);
    }

    /**
     * 微信支付订单号查询
     *
     * @param transactionsId 微信订单号，不能为空
     * @return 支付结果
     * @throws WxPayException 查单失败
     */
    public WxPayResult queryOrderByTransactionsId(String transactionsId) throws WxPayException {
        Assert.hasText(transactionsId, "微信订单号不能为空");
        return queryOrder(ORDER_URL + "id/" + transactionsId + "?mchid=" + wxProperties.getPay().getMchId());
    }

    /**
     * 商户订单号查询
     *
     * @param outTradeId 商户订单号，不能为空
     * @return 支付结果
     * @throws WxPayException 查单失败
     */
    public WxPayResult queryOrderByOutTradeId(String outTradeId) throws WxPayException {
        Assert.hasText(outTradeId, "商户订单号不能为空");
        return queryOrder(ORDER_URL + "out-trade-no/" + outTradeId + "?mchid=" + wxProperties.getPay().getMchId());
    }

    public WxPayResult queryOrder(String url) throws WxPayException {
        String result = get(url);
        try {
            return objectMapper.readValue(result, WxPayResult.class);
        } catch (JsonProcessingException e) {
            throw new WxPayException("解析查单响应失败", e);
        }
    }

    /**
     * 验证微信发送过来的请求，以确保请求来自微信支付
     *
     * @param headers 请求 header
     * @param body    请求体
     * @return true 表示请求来自微信
     */
    protected boolean validateWxRequest(WxHeaders headers, String body) {
        return validator.validate(headers, body);
    }

    /**
     * 验证并解密支付结果通知。
     *
     * @param request HTTP 请求
     * @return 解密后的支付结果
     * @throws WxPayException 验签失败或解密失败
     */
    public WxPayResult validateAndDecryptPayNotification(HttpServletRequest request) throws WxPayException {
        return validateAndDecryptPayNotification(new HttpServletRequestWxHeaders(request), getRequestBody(request));
    }

    /**
     * 验证并解密支付结果通知（适用于 body 已被框架读取，或非 Servlet 环境）。
     *
     * @param headers 回调请求 header
     * @param body    原始请求体
     * @return 解密后的支付结果
     * @throws WxPayException 验签失败或解密失败
     */
    public WxPayResult validateAndDecryptPayNotification(WxHeaders headers, String body) throws WxPayException {
        if (!validateWxRequest(headers, body)) {
            throw new WxPayException("验签不能过，非微信支付团队的消息！");
        }
        return buildPayResult(body);
    }

    /**
     * 处理微信支付结果，请先验证签名再调用此方法
     *
     * @param data 微信 Post 过来的加密数据
     * @return 支付结果
     * @throws WxPayException 处理支付结果失败，如非微信通知的支付结果
     */
    protected WxPayResult buildPayResult(String data) throws WxPayException {
        try {
            JsonNode resource = parseResource(data);
            String plainText = aesUtil.decryptToString(
                    resource.get("associated_data").asText().getBytes(StandardCharsets.UTF_8),
                    resource.get("nonce").asText().getBytes(StandardCharsets.UTF_8),
                    resource.get("ciphertext").asText());
            return objectMapper.readValue(plainText, WxPayResult.class);
        } catch (GeneralSecurityException | JsonProcessingException e) {
            throw new WxPayException(data, e);
        }
    }

    private JsonNode parseResource(String body) throws JsonProcessingException, WxPayException {
        JsonNode resource = objectMapper.readTree(body).get("resource");
        if (resource == null) {
            throw new WxPayException("微信回调数据缺少 resource 节点");
        }
        return resource;
    }

    public String get(String url) throws WxPayException {
        Request request = new Request.Builder()
                .url(url)
                .build();
        return request(request);
    }

    public String post(String url, Object requestBody) throws WxPayException {
        String content;
        try {
            content = objectMapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException e) {
            throw new WxPayException("序列化请求体失败", e);
        }
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(content, MediaType.parse("application/json;charset=utf-8")))
                .build();
        log.debug("微信请求体：{}", content);
        return request(request);
    }

    protected String request(Request request) throws WxPayException {
        log.debug("微信url：{}", request.url());
        try (Response execute = okHttpClient.newCall(request).execute()) {
            log.debug("微信响应码：{}", execute.code());
            ResponseBody body = execute.body();
            if (execute.isSuccessful()) {
                if (body != null) {
                    String string = body.string();
                    log.debug("微信响应：{}", string);
                    Headers headers = execute.headers();
                    if (!validator.validate(new OkHttpWxHeaders(execute), string)) {
                        throw new WxPayException(String.format("验证响应失败!:响应体：%s,响应headers:%s", string, buildHeader(headers)));
                    }
                    return string;
                }
                return "";
            }
            String errorBody = body != null ? body.string() : "";
            throw new WxPayException(String.format("微信请求失败(HTTP %d)：%s", execute.code(), errorBody));
        } catch (IOException e) {
            throw new WxPayException("微信请求失败：", e);
        }
    }

    private String buildHeader(Headers headers) {
        Map<String, List<String>> stringListMap = headers.toMultimap();
        StringBuilder sb = new StringBuilder();
        stringListMap.forEach((key, value) -> sb.append(key).append("=").append(String.join(",", value)).append(","));
        if (!sb.isEmpty()) {
            sb.deleteCharAt(sb.lastIndexOf(","));
        }
        return sb.toString();
    }

    private String getRequestBody(HttpServletRequest request) throws WxPayException {
        try (InputStream inputStream = request.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new WxPayException("解析请求失败", e);
        }
    }

    /**
     * 生成 APP 调起支付所需的 paySign。
     *
     * @param timestamp 时间戳（秒）
     * @param nonceStr  随机字符串
     * @param prepayId  预支付交易会话 ID
     * @return base64 编码的签名
     */
    public String appPaySign(String timestamp, String nonceStr, String prepayId) {
        String message = wxProperties.getAppId() + "\n"
                + timestamp + "\n"
                + nonceStr + "\n"
                + prepayId + "\n";
        return sign.sign(message.getBytes(StandardCharsets.UTF_8));
    }
}
