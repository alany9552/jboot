/**
 * Copyright (c) 2015-2020, Michael Yang 杨福海 (fuhai999@gmail.com).
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.jboot.components.http;

import io.jboot.app.config.annotation.ConfigModel;


@ConfigModel(prefix = "jboot.http")
public class JbootHttpConfig {
    public static final String TYPE_DEFAULT = "default";
    public static final String TYPE_HTTPCLIENT = "httpclient";
    public static final String TYPE_OKHTTP = "okhttp";

    public String type = TYPE_DEFAULT;
    private String certPath;
    private String certPass;
    private int readTimeOut = JbootHttpRequest.READ_TIME_OUT;
    private int connectTimeOut = JbootHttpRequest.CONNECT_TIME_OUT;
    private String contentType = JbootHttpRequest.CONTENT_TYPE_URL_ENCODED;



    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCertPath() {
        return certPath;
    }

    public void setCertPath(String certPath) {
        this.certPath = certPath;
    }

    public String getCertPass() {
        return certPass;
    }

    public void setCertPass(String certPass) {
        this.certPass = certPass;
    }

    public int getReadTimeOut() {
        return readTimeOut;
    }

    public void setReadTimeOut(int readTimeOut) {
        this.readTimeOut = readTimeOut;
    }

    public int getConnectTimeOut() {
        return connectTimeOut;
    }

    public void setConnectTimeOut(int connectTimeOut) {
        this.connectTimeOut = connectTimeOut;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}
