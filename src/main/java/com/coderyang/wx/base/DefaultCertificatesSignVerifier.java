package com.coderyang.wx.base;

import com.coderyang.wx.util.WxPayUtil;
import lombok.Setter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于「微信支付平台证书」的应答签名校验器。
 *
 * <p>首次验签或证书过期时自动从微信支付下载平台证书，并周期性刷新（默认 12 小时）。
 */
public class DefaultCertificatesSignVerifier implements SignVerifier {

    private final static Logger log = LoggerFactory.getLogger(DefaultCertificatesSignVerifier.class);

    private final Map<BigInteger, X509Certificate> certificates = new ConcurrentHashMap<>();

    /**
     * 平台证书下载地址
     */
    private static final String CERT_DOWNLOAD_PATH = "https://api.mch.weixin.qq.com/v3/certificates";

    /**
     * 上次更新时间
     */
    private volatile Instant instant;

    /**
     * 证书更新间隔时间，单位为分钟
     */
    @Setter
    private int minutesInterval = TimeInterval.TwelveHours.minutes;

    private final byte[] apiV3Key;

    private final OkHttpClient okHttpClient;

    private final ReentrantLock lock = new ReentrantLock();

    public DefaultCertificatesSignVerifier(byte[] apiV3Key, OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
        this.apiV3Key = apiV3Key;
    }

    @Override
    public boolean verify(String serialNumber, byte[] message, String signature) {
        BigInteger serial = new BigInteger(serialNumber, 16);
        if (needRefresh(serial)) {
            refreshCertificates(serial);
        }
        X509Certificate certificate = certificates.get(serial);
        return certificate != null && verify(certificate, message, signature);
    }

    /**
     * 证书不存在、缓存过期或从未更新过时需要刷新。
     */
    private boolean needRefresh(BigInteger serial) {
        return instant == null
                || Duration.between(instant, Instant.now()).toMinutes() >= minutesInterval
                || !certificates.containsKey(serial);
    }

    private void refreshCertificates(BigInteger serial) {
        lock.lock();
        try {
            // 双重检查：避免并发请求重复下载
            if (!needRefresh(serial)) {
                return;
            }
            autoUpdateCert();
            instant = Instant.now();
        } catch (GeneralSecurityException | IOException e) {
            log.warn("自动更新平台证书失败", e);
        } finally {
            lock.unlock();
        }
    }

    private boolean verify(X509Certificate certificate, byte[] message, String signature) {
        try {
            Signature sign = Signature.getInstance("SHA256withRSA");
            sign.initVerify(certificate);
            sign.update(message);
            return sign.verify(Base64.getDecoder().decode(signature));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("当前Java环境不支持SHA256withRSA", e);
        } catch (SignatureException e) {
            throw new RuntimeException("签名验证过程发生了错误", e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException("无效的证书", e);
        }
    }

    private void autoUpdateCert() throws IOException, GeneralSecurityException {
        Request request = new Request.Builder().url(CERT_DOWNLOAD_PATH).get().build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful()) {
                log.error("下载平台证书失败：HTTP {} {}", response.code(), body != null ? body.string() : "");
                return;
            }
            if (body != null) {
                List<X509Certificate> x509Certificates = WxPayUtil.deserializeToCerts(apiV3Key, body.string());
                for (X509Certificate x509Certificate : x509Certificates) {
                    certificates.put(x509Certificate.getSerialNumber(), x509Certificate);
                }
            }
        }
    }

    /**
     * 证书刷新间隔，支持一小时、六小时以及十二小时。
     */
    public enum TimeInterval {
        OneHour(60), SixHours(60 * 6), TwelveHours(60 * 12);

        private final int minutes;

        TimeInterval(int minutes) {
            this.minutes = minutes;
        }

        public int getMinutes() {
            return minutes;
        }
    }
}
