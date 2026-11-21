package com.coderyang.wx.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Base64;

/**
 * 基于商户 API 证书私钥的 V3 签名实现（SHA256withRSA）。
 */
public class DefaultV3Sign implements Sign {

    private final static Logger log = LoggerFactory.getLogger(DefaultV3Sign.class);

    private final PrivateKey privateKey;

    public DefaultV3Sign(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    @Override
    public String sign(byte[] message) {
        try {
            Signature sign = Signature.getInstance("SHA256withRSA");
            sign.initSign(privateKey);
            sign.update(message);
            return Base64.getEncoder().encodeToString(sign.sign());
        } catch (SignatureException | InvalidKeyException | NoSuchAlgorithmException e) {
            log.error("微信支付签名失败", e);
            throw new RuntimeException("微信支付签名失败", e);
        }
    }
}
