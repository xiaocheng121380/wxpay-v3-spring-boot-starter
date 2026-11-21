package com.coderyang.wx.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class WxPayUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WxPayUtil() {

    }

    /**
     * 反序列化证书并解密
     *
     * @param apiV3Key v3密钥
     * @param body     需要反序列化的数据
     * @return 反序列化后得到的证书
     * @throws IOException              非法的json数据
     * @throws GeneralSecurityException 反序列化证书失败
     */
    public static List<X509Certificate> deserializeToCerts(byte[] apiV3Key, String body)
            throws GeneralSecurityException, IOException {
        AesUtil decryptor = new AesUtil(apiV3Key);
        JsonNode dataNode = MAPPER.readTree(body).get("data");
        List<X509Certificate> newCertList = new ArrayList<>();
        if (dataNode != null) {
            for (int i = 0, count = dataNode.size(); i < count; i++) {
                JsonNode encryptCertificate = dataNode.get(i).get("encrypt_certificate");
                String cert = decryptor.decryptToString(
                        encryptCertificate.get("associated_data").asText().getBytes(StandardCharsets.UTF_8),
                        encryptCertificate.get("nonce").asText().getBytes(StandardCharsets.UTF_8),
                        encryptCertificate.get("ciphertext").asText());

                CertificateFactory cf = CertificateFactory.getInstance("X509");
                X509Certificate x509Cert = (X509Certificate) cf.generateCertificate(
                        new ByteArrayInputStream(cert.getBytes(StandardCharsets.UTF_8)));
                try {
                    x509Cert.checkValidity();
                } catch (CertificateExpiredException | CertificateNotYetValidException e) {
                    continue;
                }
                newCertList.add(x509Cert);
            }
        }
        return newCertList;
    }
}
