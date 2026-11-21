package com.coderyang.wx.properties;

import lombok.Data;

@Data
public class WxProperties {

    /**
     * 微信公众应用appId
     */
    private String appId;

    /**
     * 支付相关配置
     */
    private Pay pay = new Pay();

    @Data
    public static class Pay {
        /**
         * v3接口密钥
         */
        private String apiV3Key;

        /**
         * 微信支付商户号
         */
        private String mchId;

        /**
         * 证书序列号
         */
        private String certificateSerialNo;

        /**
         * 微信支付api私钥，支持两种形式：
         * 1. 资源路径：classpath:/cert/apiclient_key.pem 、 file:/abs/path/key.pem 、 绝对路径
         * 2. PEM 格式字符串：以 -----BEGIN 开头的私钥原文
         */
        private String privateKeyPath;

        /**
         * 微信支付平台公钥ID（启用「微信支付公钥」模式后由商户平台分配，例：PUB_KEY_ID_xxx）
         * 配置后将优先使用公钥模式验签，无需自动下载平台证书。
         */
        private String publicKeyId;

        /**
         * 微信支付平台公钥，支持两种形式：
         * 1. 资源路径：classpath:/cert/pub_key.pem 、 file:/abs/path/pub_key.pem 、 绝对路径
         * 2. PEM 格式字符串：以 -----BEGIN 开头的公钥原文
         */
        private String publicKeyPath;

        /**
         * 支付通知地址
         */
        private String notifyUrl;

        /**
         * 退款通知地址
         */
        private String refundNotifyUrl;
    }

}
