package com.coderyang.wx.base;

/**
 * 签名验证
 */
public interface SignVerifier {

    boolean verify(String serialNumber, byte[] message, String signature);

}
