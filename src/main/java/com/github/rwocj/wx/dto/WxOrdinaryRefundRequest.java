package com.github.rwocj.wx.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 普通商户(直连商户)微信退款请求体
 *
 * <p>对应微信官方接口：
 * <a href="https://pay.weixin.qq.com/docs/merchant/apis/refund/refunds/create.html">普通商户/退款申请</a>
 * <p>POST {@code https://api.mch.weixin.qq.com/v3/refund/domestic/refunds}
 *
 * <p>{@link #transactionId} 与 {@link #outTradeNo} 二选一必填。
 *
 * @author Copilot
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class WxOrdinaryRefundRequest {

    /**
     * 微信支付订单号（与 outTradeNo 二选一）
     */
    @JsonProperty("transaction_id")
    private String transactionId;

    /**
     * 商户订单号（与 transactionId 二选一）
     */
    @JsonProperty("out_trade_no")
    private String outTradeNo;

    /**
     * 商户退款单号
     * <p>商户系统内部唯一，只能是数字、大小写字母_-|*@，最长 64 字符。
     * 同一退款单号多次请求只退一笔；退款失败时再次请求需使用相同退款单号。
     *
     * @required
     */
    @JsonProperty("out_refund_no")
    private String outRefundNo;

    /**
     * 退款原因（最长 80 字符）。商户传入后会展示在用户侧退款消息中。
     */
    @JsonProperty("reason")
    private String reason;

    /**
     * 退款结果回调 URL。如果传入则覆盖商户平台配置的回调地址。
     */
    @JsonProperty("notify_url")
    private String notifyUrl;

    /**
     * 退款资金来源。仅对部分商户开放（先退完订单可用余额，再退到银行卡）。
     * 可选值：AVAILABLE = 仅使用可用余额退款。
     */
    @JsonProperty("funds_account")
    private String fundsAccount;

    /**
     * 金额信息（必填）。
     *
     * @required
     */
    @JsonProperty("amount")
    private Amount amount;

    /**
     * 退款商品（部分退款时必填）。
     */
    @JsonProperty("goods_detail")
    private List<GoodsDetail> goodsDetail;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Amount {

        /**
         * 退款金额，单位：分。不能超过原订单支付金额。
         *
         * @required
         */
        @JsonProperty("refund")
        private int refund;

        /**
         * 退款出资账户及金额（部分场景必填）。
         */
        @JsonProperty("from")
        private List<From> from;

        /**
         * 原订单总金额，单位：分（必须与原下单金额一致）。
         *
         * @required
         */
        @JsonProperty("total")
        private int total;

        /**
         * 退款币种（默认 CNY）。
         *
         * @required
         */
        @JsonProperty("currency")
        private String currency = "CNY";
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class From {
        /** 出资账户类型：AVAILABLE / UNAVAILABLE */
        @JsonProperty("account")
        private String account;
        /** 出资金额，单位：分 */
        @JsonProperty("amount")
        private int amount;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GoodsDetail {
        @JsonProperty("merchant_goods_id")
        private String merchantGoodsId;
        @JsonProperty("wechatpay_goods_id")
        private String wechatpayGoodsId;
        @JsonProperty("goods_name")
        private String goodsName;
        @JsonProperty("unit_price")
        private int unitPrice;
        @JsonProperty("refund_amount")
        private int refundAmount;
        @JsonProperty("refund_quantity")
        private int refundQuantity;
    }
}
