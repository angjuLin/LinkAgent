/**
 * Copyright 2021 Shulie Technology, Co.Ltd
 * Email: shulie@shulie.io
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pamirs.attach.plugin.log4j.interceptor.v1.creator;

import java.io.IOException;

import com.pamirs.pradar.Pradar;
import org.apache.log4j.DailyRollingFileAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jirenhe | jirenhe@shulie.io
 * @since 2021/11/05 11:52 上午
 */
public class DailyRollingFileShadowAppenderCreator implements ShadowAppenderCreatorV1<DailyRollingFileAppender> {

    private final static Logger logger = LoggerFactory.getLogger(
        DailyRollingFileShadowAppenderCreator.class);

    @Override
    public DailyRollingFileAppender creatorPtAppender(DailyRollingFileAppender oldAppender,
        String bizShadowLogPath) {
        try {
            DailyRollingFileAppender ptAppender = new DailyRollingFileAppender(
                oldAppender.getLayout()
                , bizShadowLogPath + oldAppender.getFile()
                , oldAppender.getDatePattern()
            );
            ptAppender.setName(Pradar.CLUSTER_TEST_PREFIX + oldAppender.getName());
            ptAppender.setAppend(oldAppender.getAppend());
            ptAppender.setBufferedIO(oldAppender.getBufferedIO());
            ptAppender.setBufferSize(oldAppender.getBufferSize());
            ptAppender.setEncoding(oldAppender.getEncoding());
            ptAppender.setErrorHandler(oldAppender.getErrorHandler());
            ptAppender.setImmediateFlush(oldAppender.getImmediateFlush());
            return ptAppender;
        } catch (IOException e) {
            logger.error("add DailyRollingFileAppender to category in Log4j module for v1 error.", e);
        }
        return null;
    }
}
