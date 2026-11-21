package com.coderyang.wx.configuration;

import okhttp3.OkHttpClient;

/**
 * {@link OkHttpClient.Builder} 定制回调，业务方可通过注册该接口的 Bean 调整超时、代理等参数。
 *
 * @since 2024/7/5 11:33
 **/
public interface OkHttpClientCustomizer {

    void customize(OkHttpClient.Builder builder);
}
