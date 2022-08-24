/*
 * Copyright 2021 Shulie Technology, Co.Ltd
 * Email: shulie@shulie.io
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pamirs.attach.plugin.cluster.test.check.interceptor.rpc;

import com.pamirs.attach.plugin.cluster.test.check.interceptor.AbstractCheckInterceptor;
import com.shulie.instrument.simulator.api.listener.ext.Advice;
import io.grpc.Metadata;

/**
 * @Description
 * @Author ocean_wll
 * @Date 2022/8/24 10:44
 */
public class GrpcInterceptor extends AbstractCheckInterceptor {

    @Override
    public Object getParam(Advice advice, String key) {
        Object param = advice.getParameterArray()[3];
        if (param instanceof Metadata) {
            Metadata headers = (Metadata) param;
            return headers.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
        }
        return null;
    }
}
