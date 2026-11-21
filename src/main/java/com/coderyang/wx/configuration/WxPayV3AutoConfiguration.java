package com.coderyang.wx.configuration;

import com.coderyang.wx.base.CompositeSignVerifier;
import com.coderyang.wx.base.Credentials;
import com.coderyang.wx.base.DefaultCertificatesSignVerifier;
import com.coderyang.wx.base.DefaultV3Credentials;
import com.coderyang.wx.base.DefaultV3Sign;
import com.coderyang.wx.base.DefaultV3Validator;
import com.coderyang.wx.base.PublicKeySignVerifier;
import com.coderyang.wx.base.Sign;
import com.coderyang.wx.base.SignVerifier;
import com.coderyang.wx.base.Validator;
import com.coderyang.wx.base.WxPayV3OkHttpInterceptor;
import com.coderyang.wx.properties.WxProperties;
import com.coderyang.wx.service.WxPayV3Service;
import com.coderyang.wx.util.OkHttpClientBuilderUtil;
import com.coderyang.wx.util.PemUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 微信支付 V3 自动配置。
 *
 * <p>仅在配置了 {@code wx.pay.private-key-path} 时装配微信支付相关 Bean。
 * 微信支付专用 {@link OkHttpClient} 的 Bean 名为 {@value #WX_PAY_OK_HTTP_CLIENT}，
 * 不会覆盖宿主机中已有的 {@code OkHttpClient}。
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnClass({OkHttpClient.class, ObjectMapper.class})
public class WxPayV3AutoConfiguration {

    /**
     * 微信支付专用 OkHttpClient Bean 名称。
     */
    public static final String WX_PAY_OK_HTTP_CLIENT = "wxPayOkHttpClient";

    private final ObjectMapper objectMapper;

    public WxPayV3AutoConfiguration(ObjectProvider<ObjectMapper> objectProvider) {
        this.objectMapper = objectProvider.getIfAvailable(ObjectMapper::new);
    }

    @Bean
    @ConfigurationProperties(prefix = "wx")
    @ConditionalOnMissingBean
    public WxProperties wxProperties() {
        return new WxProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "wx.pay", name = "private-key-path")
    public Sign wxPaySign(WxProperties wxProperties) {
        WxProperties.Pay pay = wxProperties.getPay();
        PrivateKey privateKey = PemUtil.loadPrivateKeyFromPathOrContent(pay.getPrivateKeyPath());
        return new DefaultV3Sign(privateKey);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "wx.pay", name = "private-key-path")
    public Credentials wxPayCredentials(Sign sign) {
        return new DefaultV3Credentials(sign);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "wx.pay", name = "private-key-path")
    public WxPayV3OkHttpInterceptor wxPayInterceptor(Credentials credentials, WxProperties wxProperties) {
        WxProperties.Pay pay = wxProperties.getPay();
        Assert.hasText(pay.getMchId(), "wx.pay.mch-id 不能为空");
        Assert.hasText(pay.getCertificateSerialNo(), "wx.pay.certificate-serial-no 不能为空");
        return new WxPayV3OkHttpInterceptor(credentials, pay.getMchId(), pay.getCertificateSerialNo());
    }

    @Bean(WX_PAY_OK_HTTP_CLIENT)
    @ConditionalOnMissingBean(name = WX_PAY_OK_HTTP_CLIENT)
    @ConditionalOnProperty(prefix = "wx.pay", name = "private-key-path")
    public OkHttpClient wxPayOkHttpClient(WxPayV3OkHttpInterceptor wxPayV3OkHttpInterceptor,
                                          ObjectProvider<OkHttpClientCustomizer> okHttpClientCustomizerObjectProvider) {
        return OkHttpClientBuilderUtil.wxPayOkHttpClient(
                wxPayV3OkHttpInterceptor,
                okHttpClientCustomizerObjectProvider.orderedStream().collect(Collectors.toList())).build();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "wx.pay", name = "private-key-path")
    public Validator wxPayValidator(@Qualifier(WX_PAY_OK_HTTP_CLIENT) OkHttpClient wxPayOkHttpClient,
                                    WxProperties wxProperties) {
        WxProperties.Pay pay = wxProperties.getPay();
        List<SignVerifier> verifiers = new ArrayList<>();

        // 1. 公钥模式（优先，新版商户）
        if (pay.getPublicKeyPath() != null && !pay.getPublicKeyPath().isBlank()
                && pay.getPublicKeyId() != null && !pay.getPublicKeyId().isBlank()) {
            PublicKey publicKey = PemUtil.loadPublicKeyFromPathOrContent(pay.getPublicKeyPath());
            verifiers.add(new PublicKeySignVerifier(pay.getPublicKeyId(), publicKey));
        }

        // 2. 平台证书模式（兜底，旧版商户）
        if (pay.getApiV3Key() != null && !pay.getApiV3Key().isBlank()) {
            verifiers.add(new DefaultCertificatesSignVerifier(
                    pay.getApiV3Key().getBytes(StandardCharsets.UTF_8), wxPayOkHttpClient));
        }

        if (verifiers.isEmpty()) {
            throw new IllegalStateException("微信支付验签未配置：请配置 wx.pay.public-key-id + public-key-path 或 wx.pay.api-v3-key");
        }
        SignVerifier verifier = verifiers.size() == 1 ? verifiers.get(0) : new CompositeSignVerifier(verifiers);
        return new DefaultV3Validator(verifier);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "wx.pay", name = "private-key-path")
    public WxPayV3Service wxPayV3Service(@Qualifier(WX_PAY_OK_HTTP_CLIENT) OkHttpClient wxPayOkHttpClient,
                                         Validator validator,
                                         Sign sign,
                                         WxProperties wxProperties) {
        return new WxPayV3Service(wxPayOkHttpClient, objectMapper, validator, wxProperties, sign);
    }
}
