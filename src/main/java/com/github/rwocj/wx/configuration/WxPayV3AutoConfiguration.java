package com.github.rwocj.wx.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.github.rwocj.wx.base.*;
import com.github.rwocj.wx.properties.WxProperties;
import com.github.rwocj.wx.service.WxPayV3Service;
import com.github.rwocj.wx.util.OkHttpClientBuilderUtil;
import com.github.rwocj.wx.util.PemUtil;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(JacksonAutoConfiguration.class)
public class WxPayV3AutoConfiguration {

    private final ObjectMapper objectMapper;

    public WxPayV3AutoConfiguration(ObjectProvider<ObjectMapper> objectProvider) {
        this.objectMapper = objectProvider.getIfAvailable(ObjectMapper::new);
    }

    @Bean
    @ConfigurationProperties(prefix = "wx")
    @ConditionalOnMissingBean
    public WxProperties WxProperties() {
        return new WxProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "wx.pay", name = "private-key-path")
    public Sign wxPaySign(WxProperties wxProperties) throws IOException {
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
        return new WxPayV3OkHttpInterceptor(credentials, pay.getMchId(), pay.getCertificateSerialNo());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "wx.pay", name = "private-key-path")
    public OkHttpClient okHttpClient(WxPayV3OkHttpInterceptor wxPayV3OkHttpInterceptor,
                                     ObjectProvider<OkHttpClientCustomizer> okHttpClientCustomizerObjectProvider) {
        return OkHttpClientBuilderUtil.wxPayOkHttpClient(
                wxPayV3OkHttpInterceptor,
                okHttpClientCustomizerObjectProvider.orderedStream().collect(Collectors.toList())).build();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "wx.pay", name = "private-key-path")
    public Validator wxPayValidator(OkHttpClient okHttpClient, WxProperties wxProperties) throws IOException {
        WxProperties.Pay pay = wxProperties.getPay();
        List<SignVerifier> verifiers = new ArrayList<>();

        // 1. 公钥模式（优先，新版商户必选）
        if (pay.getPublicKeyPath() != null && !pay.getPublicKeyPath().isBlank()
                && pay.getPublicKeyId() != null && !pay.getPublicKeyId().isBlank()) {
            PublicKey publicKey = PemUtil.loadPublicKeyFromPathOrContent(pay.getPublicKeyPath());
            verifiers.add(new PublicKeySignVerifier(pay.getPublicKeyId(), publicKey));
        }

        // 2. 平台证书模式（兜底，旧版兼容）
        if (pay.getApiV3Key() != null && !pay.getApiV3Key().isBlank()) {
            verifiers.add(new DefaultCertificatesSignVerifier(
                    pay.getApiV3Key().getBytes(StandardCharsets.UTF_8), okHttpClient));
        }

        if (verifiers.isEmpty()) {
            throw new IllegalStateException("微信支付验签未配置：请配置 wx.pay.public-key-id+public-key-path 或 wx.pay.api-v3-key");
        }
        SignVerifier verifier = verifiers.size() == 1 ? verifiers.get(0) : new CompositeSignVerifier(verifiers);
        return new DefaultV3Validator(verifier);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "wx.pay", name = "private-key-path")
    public WxPayV3Service wxPayV3Service(OkHttpClient okHttpClient,
                                         Validator validator,
                                         Sign sign, WxProperties wxProperties) {
        return new WxPayV3Service(okHttpClient, objectMapper, validator, wxProperties, sign);
    }

}
