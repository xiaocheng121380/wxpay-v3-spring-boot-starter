package com.coderyang.wx.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Base64;

/**
 * 基于「微信支付公钥」的应答签名校验器。
 * <p>
 * 当商户在商户平台启用了「微信支付公钥」（应答 header `Wechatpay-Serial` 形如 `PUB_KEY_ID_xxx`），
 * 平台不再返回平台证书，需用商户后台下载的公钥进行验签。
 */
public class PublicKeySignVerifier implements SignVerifier {

    private final static Logger log = LoggerFactory.getLogger(PublicKeySignVerifier.class);

    private final String publicKeyId;
    private final PublicKey publicKey;

    public PublicKeySignVerifier(String publicKeyId, PublicKey publicKey) {
        this.publicKeyId = publicKeyId;
        this.publicKey = publicKey;
    }

    public String getPublicKeyId() {
        return publicKeyId;
    }

    @Override
    public boolean verify(String serialNumber, byte[] message, String signature) {
        // 仅当 header serial 与配置的 publicKeyId 完全一致时才使用公钥验签
        if (publicKeyId == null || !publicKeyId.equals(serialNumber)) {
            return false;
        }
        try {
            Signature sign = Signature.getInstance("SHA256withRSA");
            sign.initVerify(publicKey);
            sign.update(message);
            return sign.verify(Base64.getDecoder().decode(signature));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("当前Java环境不支持SHA256withRSA", e);
        } catch (SignatureException e) {
            log.warn("使用微信支付公钥验签失败: serial={} err={}", serialNumber, e.getMessage());
            return false;
        } catch (InvalidKeyException e) {
            throw new RuntimeException("无效的微信支付公钥", e);
        }
    }
}
