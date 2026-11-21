package com.coderyang.wx.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class PemUtil {

    private PemUtil() {

    }

    /**
     * 智能加载私钥：入参可以是 PEM 格式原文字符串，也可以是资源路径
     * （classpath:xxx 、 file:xxx 或绝对路径）。
     */
    public static PrivateKey loadPrivateKeyFromPathOrContent(String pathOrContent) {
        if (pathOrContent == null || pathOrContent.isBlank()) {
            throw new IllegalArgumentException("私钥配置为空");
        }
        if (pathOrContent.contains("-----BEGIN")) {
            return loadPrivateKeyFromString(pathOrContent);
        }
        try (InputStream is = new org.springframework.core.io.DefaultResourceLoader()
                .getResource(pathOrContent).getInputStream()) {
            return loadPrivateKey(is);
        } catch (IOException e) {
            throw new RuntimeException("加载私钥失败：" + pathOrContent, e);
        }
    }

    /**
     * 智能加载公钥：入参可以是 PEM 格式原文字符串，也可以是资源路径。
     */
    public static PublicKey loadPublicKeyFromPathOrContent(String pathOrContent) {
        if (pathOrContent == null || pathOrContent.isBlank()) {
            throw new IllegalArgumentException("公钥配置为空");
        }
        if (pathOrContent.contains("-----BEGIN")) {
            return loadPublicKeyFromString(pathOrContent);
        }
        try (InputStream is = new org.springframework.core.io.DefaultResourceLoader()
                .getResource(pathOrContent).getInputStream()) {
            return loadPublicKey(is);
        } catch (IOException e) {
            throw new RuntimeException("加载公钥失败：" + pathOrContent, e);
        }
    }

    public static PrivateKey loadPrivateKey(InputStream inputStream) {
        try {
            ByteArrayOutputStream array = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                array.write(buffer, 0, length);
            }

            String privateKey = array.toString("utf-8")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");

            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("当前Java环境不支持RSA", e);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException("无效的密钥格式");
        } catch (IOException e) {
            throw new RuntimeException("无效的密钥");
        }
    }

    public static PrivateKey loadPrivateKeyFromString(String pemContent) {
        String privateKey = pemContent
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("当前Java环境不支持RSA", e);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException("无效的密钥格式", e);
        }
    }

    /**
     * 从PEM字符串加载微信支付公钥（X.509 SubjectPublicKeyInfo格式）
     */
    public static PublicKey loadPublicKeyFromString(String pemContent) {
        String publicKey = pemContent
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("当前Java环境不支持RSA", e);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException("无效的公钥格式", e);
        }
    }

    /**
     * 从输入流加载微信支付公钥
     */
    public static PublicKey loadPublicKey(InputStream inputStream) {
        try {
            ByteArrayOutputStream array = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                array.write(buffer, 0, length);
            }
            return loadPublicKeyFromString(array.toString("utf-8"));
        } catch (IOException e) {
            throw new RuntimeException("无效的公钥");
        }
    }

    public static X509Certificate loadCertificate(InputStream inputStream) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(inputStream);
            cert.checkValidity();
            return cert;
        } catch (CertificateExpiredException e) {
            throw new RuntimeException("证书已过期", e);
        } catch (CertificateNotYetValidException e) {
            throw new RuntimeException("证书尚未生效", e);
        } catch (CertificateException e) {
            throw new RuntimeException("无效的证书", e);
        }
    }
}
