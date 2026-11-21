package com.coderyang.wx.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.coderyang.wx.base.*;
import com.coderyang.wx.dto.*;
import com.coderyang.wx.enums.OrderType;
import com.coderyang.wx.properties.WxProperties;
import com.coderyang.wx.util.AesUtil;
import com.coderyang.wx.util.PemUtil;
import com.coderyang.wx.util.SignUtil;
import jakarta.servlet.http.HttpServletRequest;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WxPayV3Service {

    /**
     * 普通商户下单url
     */
    final static String ORDER_URL = "https://api.mch.weixin.qq.com/v3/pay/transactions/";
    /**
     * 电商平台退款url
     */
    final static String REFUND_URL = "https://api.mch.weixin.qq.com/v3/ecommerce/refunds/apply";
    /**
     * 普通商户(直连)退款url
     */
    final static String ORDINARY_REFUND_URL = "https://api.mch.weixin.qq.com/v3/refund/domestic/refunds";
    private final static Logger log = LoggerFactory.getLogger(WxPayV3Service.class);
    protected final OkHttpClient okHttpClient;

    protected final ObjectMapper objectMapper;

    protected final Validator validator;

    protected final WxProperties WxProperties;

    protected final Sign sign;

    protected final AesUtil aesUtil;

    public WxPayV3Service(OkHttpClient okHttpClient, ObjectMapper objectMapper,
                          Validator validator, WxProperties WxProperties,
                          Sign sign) {
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.WxProperties = WxProperties;
        this.sign = sign;
        this.aesUtil = new AesUtil(WxProperties.getPay().getApiV3Key().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 普通商户原生下单，得到prepay_id或地址等,适用app/h5/jsapi/native
     * app/jsapi：返回prepay_id
     * h5: 返回h5_url
     * native: 返回code_url
     *
     * @param createOrderRequest 下单请求体
     * @return 预下单id, prepay_id
     * @throws WxPayException 下单失败
     */
    public String createOrder(WxCreateOrderRequest createOrderRequest) throws WxPayException {
        if (createOrderRequest.getAppid() == null) {
            createOrderRequest.setAppid(WxProperties.getAppId());
        }
        if (createOrderRequest.getMchid() == null) {
            createOrderRequest.setMchid(WxProperties.getPay().getMchId());
        }
        if (createOrderRequest.getNotifyUrl() == null) {
            createOrderRequest.setNotifyUrl(WxProperties.getPay().getNotifyUrl());
        }
        OrderType orderType = createOrderRequest.getOrderType();
        String url = ORDER_URL + orderType.getUrl();
        String res = post(url, createOrderRequest);
        try {
            JsonNode jsonNode = objectMapper.readTree(res);
            return orderType.resultFunc.apply(jsonNode);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * 电商平台微信退款
     * <a href="https://pay.weixin.qq.com/wiki/doc/apiv3/wxpay/ecommerce/refunds/chapter3_1.shtml">电商平台微信退款接入文档</a>
     *
     * @param refundRequest 退款请求体
     * @return 退款结果
     * @throws WxPayException 退款失败
     */
    public WxRefundRes refund(WxRefundRequest refundRequest) throws WxPayException {
        if (refundRequest.getSpAppid() == null) {
            refundRequest.setSpAppid(WxProperties.getAppId());
        }
        if (refundRequest.getSubMchid() == null) {
            refundRequest.setSubMchid(WxProperties.getPay().getMchId());
        }
        String res = post(REFUND_URL, refundRequest);
        try {
            return objectMapper.readValue(res, WxRefundRes.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * 普通商户(直连商户)微信退款。
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
        Assert.notNull(refundRequest.getOutRefundNo(), "out_refund_no(商户退款单号) 不能为空");
        Assert.notNull(refundRequest.getAmount(), "amount(金额) 不能为空");
        if (refundRequest.getTransactionId() == null && refundRequest.getOutTradeNo() == null) {
            throw new IllegalArgumentException("transaction_id 与 out_trade_no 必须二选一");
        }
        String res = post(ORDINARY_REFUND_URL, refundRequest);
        try {
            return objectMapper.readValue(res, WxOrdinaryRefundRes.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
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
        String body = getRequestBody(request);
        boolean b = validateWxRequest(new HttpServletRequestWxHeaders(request), body);
        if (!b) {
            throw new WxPayException("验签不能过，非微信支付团队的消息！");
        }
        try {
            JsonNode jsonNode = objectMapper.readTree(body);
            JsonNode resource = jsonNode.get("resource");
            String s = aesUtil.decryptToString(
                    resource.get("associated_data").asText().getBytes(StandardCharsets.UTF_8),
                    resource.get("nonce").asText().getBytes(StandardCharsets.UTF_8),
                    resource.get("ciphertext").asText());
            return objectMapper.readValue(s, WxOrdinaryRefundRes.class);
        } catch (GeneralSecurityException | JsonProcessingException e) {
            throw new WxPayException(body, e);
        }
    }

    /**
     * jsapi下单，封装jsapi调用支付需要的参数
     * <a href="https://pay.weixin.qq.com/wiki/doc/apiv3/wxpay/pay/transactions/chapter3_8.shtml">jsapi下单接入文档</a>
     *
     * @param createOrderRequest 下单请求体
     * @return 下单成功后JSAPI调用支付需要的数据
     * @throws WxPayException 下单失败
     */
    public JSAPICreateOrderRes createJSAPIOrder(WxCreateOrderRequest createOrderRequest) throws WxPayException {
        createOrderRequest.setOrderType(OrderType.jsapi);
        return SignUtil.sign(createOrder(createOrderRequest), WxProperties.getAppId(), sign);
    }

    /**
     * 关单API
     *
     * @param outTradeId 商户订单号
     * @throws WxPayException 关单失败
     */
    public void closeOrder(String outTradeId) throws WxPayException {
        Assert.notNull(outTradeId, "商户订单号不能为nll");
        String url = ORDER_URL + "out-trade-no/" + outTradeId + "/close";
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("mchid", WxProperties.getPay().getMchId());
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
        Assert.notNull(transactionsId, "微信订单号不能为nll");
        return queryOrder(ORDER_URL + "id/" + transactionsId + "?mchid=" + WxProperties.getPay().getMchId());
    }

    /**
     * 商户订单号查询
     *
     * @param outTradeId 商户订单号，不能为空
     * @return 支付结果
     * @throws WxPayException 查单失败
     */
    public WxPayResult queryOrderByOutTradeId(String outTradeId) throws WxPayException {
        Assert.notNull(outTradeId, "商户订单号不能为nll");
        return queryOrder(ORDER_URL + "/out-trade-no/" + outTradeId + "?mchid=" + WxProperties.getPay().getMchId());
    }

    public WxPayResult queryOrder(String url) throws WxPayException {
        String result = get(url);
        try {
            return objectMapper.readValue(result, WxPayResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * 验证微信发送过来的请求，以确保请求来自微信支付
     *
     * @param headers 请求header
     * @param body    请求体
     * @return true表示请求来自微信
     */
    protected boolean validateWxRequest(WxHeaders headers, String body) {
        return validator.validate(headers, body);
    }

    public WxPayResult validateAndDecryptPayNotification(HttpServletRequest request) throws WxPayException {
        String body = getRequestBody(request);
        boolean b = validateWxRequest(new HttpServletRequestWxHeaders(request), body);
        if (b) {
            return buildPayResult(body);
        } else {
            throw new WxPayException("验签不能过，非微信支付团队的消息！");
        }
    }

    /**
     * 处理微信支付结果，请先验证签名再调用此方法
     *
     * @param data 微信Post过来的加密的数据
     * @return 支付结果
     * @throws WxPayException 处理支付结果失败，如非微信通知的支付结果
     */
    protected WxPayResult buildPayResult(String data) throws WxPayException {
        try {
            JsonNode jsonNode = objectMapper.readTree(data);
            JsonNode resource = jsonNode.get("resource");
            String s = aesUtil.decryptToString(resource.get("associated_data").asText().getBytes(StandardCharsets.UTF_8),
                    resource.get("nonce").asText().getBytes(StandardCharsets.UTF_8), resource.get("ciphertext").asText());
            return objectMapper.readValue(s, WxPayResult.class);
        } catch (GeneralSecurityException | JsonProcessingException e) {
            throw new WxPayException(data, e);
        }
    }

    public String get(String url) throws WxPayException {
        Request request = new Request.Builder()
                .url(url)
                .build();
        return request(request);
    }


    public String post(String url, Object requestBody) throws WxPayException {
        String content = null;
        try {
            content = objectMapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
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
                    boolean validate = validator.validate(new OkHttpWxHeaders(execute), string);
                    if (!validate) {
                        throw new WxPayException(String.format("验证响应失败!:响应体：%s,响应headers:%s", string, buildHeader(headers)));
                    } else {
                        return string;
                    }
                } else {
                    return "";
                }
            } else {
                throw new WxPayException(body != null ? body.string() : "请求响应码:" + execute.code());
            }
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
            byte[] bytes = new byte[1024];
            int bytesRead;
            StringBuilder result = new StringBuilder();
            while ((bytesRead = inputStream.read(bytes)) != -1) {
                result.append(new String(bytes, 0, bytesRead, StandardCharsets.UTF_8));
            }
            return result.toString();
        } catch (IOException e) {
            throw new WxPayException("解析请求失败", e);
        }
    }

    public String appPaySign(WxProperties wxProperties, String timestamp, String nonceStr, String prepayId) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException, IOException {
        //上传私钥
        PrivateKey privateKey = getPrivateKey(wxProperties);
        String signatureStr = Stream.of(wxProperties.getAppId(), timestamp, nonceStr, prepayId)
                .collect(Collectors.joining("\n", "", "\n"));
        Signature sign = Signature.getInstance("SHA256withRSA");
        sign.initSign(privateKey);
        sign.update(signatureStr.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(sign.sign());
    }

    public PrivateKey getPrivateKey(WxProperties wxProperties) {
        return PemUtil.loadPrivateKeyFromPathOrContent(wxProperties.getPay().getPrivateKeyPath());
    }
}
