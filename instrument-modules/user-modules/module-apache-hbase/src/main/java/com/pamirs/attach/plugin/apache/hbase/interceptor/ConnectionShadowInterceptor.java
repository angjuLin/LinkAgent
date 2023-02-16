/**
 * Copyright 2021 Shulie Technology, Co.Ltd
 * Email: shulie@shulie.io
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pamirs.attach.plugin.apache.hbase.interceptor;

import com.pamirs.attach.plugin.apache.hbase.interceptor.shadowserver.HbaseMediatorConnection;
import com.pamirs.attach.plugin.apache.hbase.utils.ShadowConnectionHolder;
import com.pamirs.attach.plugin.common.datasource.hbaseserver.InvokeSwitcher;
import com.pamirs.attach.plugin.common.datasource.hbaseserver.MediatorConnection;
import com.pamirs.attach.plugin.dynamic.Attachment;
import com.pamirs.attach.plugin.dynamic.ResourceManager;
import com.pamirs.attach.plugin.dynamic.reflect.ReflectionUtils;
import com.pamirs.attach.plugin.dynamic.template.HbaseTemplate;
import com.pamirs.pradar.CutOffResult;
import com.pamirs.pradar.Pradar;
import com.pamirs.pradar.interceptor.ResultInterceptorAdaptor;
import com.pamirs.pradar.pressurement.ClusterTestUtils;
import com.shulie.instrument.simulator.api.listener.ext.Advice;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.security.User;
import org.apache.hadoop.hbase.security.UserProvider;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * @Author <a href="tangyuhan@shulie.io">yuhan.tang</a>
 * @package: com.pamirs.attach.plugin.apache.hbase.interceptor.shadowserver
 * @Date 2021/4/18 8:34 下午
 */
public class ConnectionShadowInterceptor extends ResultInterceptorAdaptor {

    private ExecutorService service = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "[hbase-shadow-db-create-connection]");
        }
    });

    void attachment(Advice advice) {
        try {
            Object[] args = advice.getParameterArray();
            Configuration configuration = (Configuration) args[0];
            String quorum = configuration.get(HConstants.ZOOKEEPER_QUORUM);
            String port = configuration.get(HConstants.ZOOKEEPER_CLIENT_PORT);
            String znode = configuration.get(HConstants.ZOOKEEPER_ZNODE_PARENT);
            ResourceManager.set(
                    new Attachment(
                            null, "apache-hbase", new String[]{"hbase"}
                            , new HbaseTemplate()
                            .setZookeeper_quorum(quorum)
                            .setZookeeper_client_port(port)
                            .setZookeeper_znode_parent(znode)
                    )
            );
        } catch (Throwable t) {

        }
    }

    @Override
    protected Object getResult0(Advice advice) {
        Object[] args = advice.getParameterArray();
        Object result = advice.getReturnObj();
        if (InvokeSwitcher.status()) {
            return CutOffResult.passed();
        }
        if (!(args[0] instanceof Configuration)) {
            return CutOffResult.passed();
        }
        ClusterTestUtils.validateClusterTest();
        attachment(advice);
        Configuration configuration = (Configuration) args[0];
        HbaseMediatorConnection mediatorConnection = (HbaseMediatorConnection) MediatorConnection.mediatorMap.get(converConfiguration(configuration));
        if (mediatorConnection != null) {
            return mediatorConnection;
        }
        try {
            mediatorConnection = new HbaseMediatorConnection();
            mediatorConnection.setBusinessConnection((Connection) result);

            Configuration ptConfiguration = (Configuration) mediatorConnection.matching(configuration);
            if (null != ptConfiguration) {
                if (Pradar.isClusterTest()) {
                    // 影子流量同步创建链接,防止因为影子链接创建失败导致业务阻塞
                    Connection prefConnection = ConnectionFactory.createConnection(ptConfiguration, (ExecutorService) args[1], (User) args[2]);
                    ShadowConnectionHolder.setShadowConnection(prefConnection);
                    mediatorConnection.setPerformanceTestConnection(prefConnection);
                } else {
                    // 业务流量异步创建影子链接
                    service.submit(new HbaseShadowDbCreateConnectionTask((User) args[2], (ExecutorService) args[1], ptConfiguration, mediatorConnection));
                }
            }
            mediatorConnection.setArgs(args);
            mediatorConnection.setConfiguration(args[0]);
            MediatorConnection.mediatorMap.put(converConfiguration(configuration), mediatorConnection);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }

        return mediatorConnection;
    }

    public static String converConfiguration(Configuration configuration) {
        String quorum = configuration.get(HConstants.ZOOKEEPER_QUORUM);
        String port = configuration.get(HConstants.ZOOKEEPER_CLIENT_PORT);
        String znode = configuration.get(HConstants.ZOOKEEPER_ZNODE_PARENT);
        String token = configuration.get(MediatorConnection.sf_token);
        String username = configuration.get(MediatorConnection.sf_username);
        return getKey(quorum, port, znode, token, username);
    }

    private static String getKey(String quorum, String port, String znode, String token, String username) {
        String key = quorum.concat("|").concat(port).concat("|").concat(znode).concat("|");
        if (token != null) {
            key = key.concat(token).concat("|");
        }
        if (username != null) {
            key = key.concat(username);
        }
        return key;
    }

    private static class HbaseShadowDbCreateConnectionTask implements Runnable {

        private final Logger logger = org.slf4j.LoggerFactory.getLogger(HbaseShadowDbCreateConnectionTask.class);

        private User user;
        private ExecutorService executorService;
        private Configuration ptConfiguration;
        private HbaseMediatorConnection mediatorConnection;

        public HbaseShadowDbCreateConnectionTask(User user, ExecutorService executorService, Configuration ptConfiguration, HbaseMediatorConnection mediatorConnection) {
            this.user = user;
            this.executorService = executorService;
            this.ptConfiguration = ptConfiguration;
            this.mediatorConnection = mediatorConnection;
        }

        @Override
        public void run() {
            Connection prefConnection;
            while (true) {
                try {
                    if (user == null) {
                        UserProvider provider = UserProvider.instantiate(ptConfiguration);
                        user = provider.getCurrent();
                    }
                    prefConnection = ReflectionUtils.invokeStatic(ConnectionFactory.class, "createConnection", ptConfiguration, false, executorService, user);
                    break;
                } catch (IOException e) {
                    logger.error("[hbase] create shadow connection occur exception, sleep 3s then try it again.", e);
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ex) {
                    }
                }
            }
            // 如果查询影子库配置则使用影子库模式，否则未影子表模式
            ShadowConnectionHolder.setShadowConnection(prefConnection);
            mediatorConnection.setPerformanceTestConnection(prefConnection);
        }
    }

}
