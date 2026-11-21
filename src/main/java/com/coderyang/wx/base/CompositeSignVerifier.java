package com.coderyang.wx.base;

import java.util.List;

/**
 * 组合式应答签名校验器，按顺序尝试委托校验器，任一通过即视为有效。
 * <p>
 * 用于同时支持「微信支付公钥」和「平台证书」两种验签模式：
 * 公钥模式优先（无网络开销），失败则回退到平台证书模式。
 *
 * @author Copilot
 */
public class CompositeSignVerifier implements SignVerifier {

    private final List<SignVerifier> delegates;

    public CompositeSignVerifier(List<SignVerifier> delegates) {
        this.delegates = delegates;
    }

    @Override
    public boolean verify(String serialNumber, byte[] message, String signature) {
        for (SignVerifier delegate : delegates) {
            if (delegate == null) {
                continue;
            }
            if (delegate.verify(serialNumber, message, signature)) {
                return true;
            }
        }
        return false;
    }
}
