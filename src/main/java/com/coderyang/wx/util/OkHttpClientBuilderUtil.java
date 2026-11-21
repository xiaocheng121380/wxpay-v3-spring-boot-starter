package com.coderyang.wx.util;

import com.coderyang.wx.configuration.OkHttpClientCustomizer;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class OkHttpClientBuilderUtil {

    private OkHttpClientBuilderUtil() {

    }

    public static OkHttpClient.Builder wxPayOkHttpClient(Interceptor interceptor, List<OkHttpClientCustomizer> customizers) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.readTimeout(6000, TimeUnit.MILLISECONDS)
                .writeTimeout(10000, TimeUnit.MILLISECONDS)
                .connectTimeout(3000, TimeUnit.MILLISECONDS);
        if (interceptor != null) {
            builder.addInterceptor(interceptor);
        }
        if (customizers != null) {
            for (OkHttpClientCustomizer customizer : customizers) {
                customizer.customize(builder);
            }
        }
        return builder;
    }
}
