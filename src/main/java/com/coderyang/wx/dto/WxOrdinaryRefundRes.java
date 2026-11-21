package com.coderyang.wx.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 普通商户(直连商户)微信退款响应体
 *
 * <p>对应官方文档：
 * <a href="https://pay.weixin.qq.com/docs/merchant/apis/refund/refunds/create.html">普通商户/退款申请</a>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WxOrdinaryRefundRes {

    /** 微信支付退款单号 */
    @JsonProperty("refund_id")
    private String refundId;

    /** 商户退款单号 */
    @JsonProperty("out_refund_no")
    private String outRefundNo;

    /** 微信支付交易订单号 */
    @JsonProperty("transaction_id")
    private String transactionId;

    /** 原商户订单号 */
    @JsonProperty("out_trade_no")
    private String outTradeNo;

    /** 退款渠道：ORIGINAL / BALANCE / OTHER_BALANCE / OTHER_BANKCARD */
    @JsonProperty("channel")
    private String channel;

    /** 退款入账账户 */
    @JsonProperty("user_received_account")
    private String userReceivedAccount;

    /** 退款成功时间（仅退款成功时返回） */
    @JsonProperty("success_time")
    private String successTime;

    /** 退款创建时间 */
    @JsonProperty("create_time")
    private String createTime;

    /**
     * 退款状态：SUCCESS / CLOSED / PROCESSING / ABNORMAL
     * <p>注意：微信退款异步通知原文字段名是 refund_status（不是 status），
     * 退款查询/退款下单同步响应的字段名是 status。这里用 refund_status 主映射，
     * 同时保留 status 别名兼容同步响应，确保两类来源都能正确反序列化。
     */
    @JsonProperty("refund_status")
    @JsonAlias({"status"})
    private String status;

    /** 资金账户：UNSETTLED / AVAILABLE / UNAVAILABLE / OPERATION / BASIC / ECNY_BASIC */
    @JsonProperty("funds_account")
    private String fundsAccount;

    /** 金额信息 */
    @JsonProperty("amount")
    private Amount amount;

    /** 优惠退款信息 */
    @JsonProperty("promotion_detail")
    private List<PromotionDetail> promotionDetail;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Amount {
        @JsonProperty("total")
        private int total;
        @JsonProperty("refund")
        private int refund;
        @JsonProperty("payer_total")
        private int payerTotal;
        @JsonProperty("payer_refund")
        private int payerRefund;
        @JsonProperty("settlement_refund")
        private int settlementRefund;
        @JsonProperty("settlement_total")
        private int settlementTotal;
        @JsonProperty("discount_refund")
        private int discountRefund;
        @JsonProperty("currency")
        private String currency;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PromotionDetail {
        /** 券 ID */
        @JsonProperty("promotion_id")
        private String promotionId;
        /** 优惠范围：GLOBAL / SINGLE */
        @JsonProperty("scope")
        private String scope;
        /** 优惠类型：COUPON / DISCOUNT */
        @JsonProperty("type")
        private String type;
        /** 优惠券面额 */
        @JsonProperty("amount")
        private int amount;
        /** 优惠退款金额 */
        @JsonProperty("refund_amount")
        private int refundAmount;
    }
}
