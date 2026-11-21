package com.coderyang.wx.enums;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.function.Function;

/**
 * 下单类型。
 *
 * <p>不同下单类型调用不同的微信接口路径，成功响应中返回的字段也不同：
 * APP / JSAPI 返回 {@code prepay_id}，Native 返回 {@code code_url}，H5 返回 {@code h5_url}。
 */
public enum OrderType {

    APP("app", "APP下单", node -> node.get("prepay_id").asText()),
    JSAPI("jsapi", "JSAPI下单", node -> node.get("prepay_id").asText()),
    NATIVE("native", "Native下单", node -> node.get("code_url").asText()),
    H5("h5", "H5下单", node -> node.get("h5_url").asText());

    private final String url;

    private final String remark;

    private final Function<JsonNode, String> resultFunc;

    OrderType(String url, String remark, Function<JsonNode, String> resultFunc) {
        this.url = url;
        this.remark = remark;
        this.resultFunc = resultFunc;
    }

    public String getUrl() {
        return url;
    }

    public String getRemark() {
        return remark;
    }

    /**
     * 从下单响应中解析出业务需要的结果（prepay_id / code_url / h5_url）。
     *
     * @param response 微信下单响应 JSON
     * @return 下单结果
     */
    public String parseResult(JsonNode response) {
        return resultFunc.apply(response);
    }
}
