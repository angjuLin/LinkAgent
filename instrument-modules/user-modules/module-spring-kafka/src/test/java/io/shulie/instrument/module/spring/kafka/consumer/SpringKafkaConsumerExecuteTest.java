package io.shulie.instrument.module.spring.kafka.consumer;

import com.pamirs.pradar.bean.SyncObjectData;
import io.shulie.instrument.module.messaging.consumer.execute.ShadowServer;
import junit.framework.TestCase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.GenericMessageListener;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.Acknowledgment;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Licey
 * @date 2022/7/28
 */
public class SpringKafkaConsumerExecuteTest extends TestCase {

    private String kafkaServer = "192.168.1.61:9092";
    private String topic = "lik-topic-spring-kafka";
    private String groupId = "lik-groupId";

    private Map<String,Object> config(){
        Map<String, Object> props = new HashMap();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaServer);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "100");
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "15000");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        System.out.println("KafkaConsumer consumerConfigs " + props);
        return props;
    }

    public void testPrepareConfig() throws InterruptedException {
        SpringKafkaConsumerExecute springKafkaConsumerExecute = new SpringKafkaConsumerExecute();

        Map<String, Object> configs = config();
        configs.put("", "");
        DefaultKafkaConsumerFactory consumerFactory = new DefaultKafkaConsumerFactory(configs);
        ContainerProperties containerProperties = new ContainerProperties(topic);
        containerProperties.setMessageListener(new MessageListener(){
            @Override
            public void onMessage(Object data) {
                System.out.println("on message data:" + data);
            }

            @Override
            public void onMessage(Object data, Acknowledgment acknowledgment) {
                System.out.println("on message data ack");
            }

            @Override
            public void onMessage(Object data, Acknowledgment acknowledgment, Consumer consumer) {
                System.out.println("on message data ac con");
            }

            @Override
            public void onMessage(Object data, Consumer consumer) {
                System.out.println("on message data con");
            }
        });
        KafkaMessageListenerContainer target = new KafkaMessageListenerContainer(consumerFactory, containerProperties);
        SyncObjectData syncObjectData = new SyncObjectData(target, null, null);

        target.start();
        io.shulie.instrument.module.messaging.consumer.module.ConsumerConfig config = springKafkaConsumerExecute.prepareConfig(syncObjectData);

        ShadowServer shadowServer = springKafkaConsumerExecute.fetchShadowServer(config, "");
        shadowServer.start();
        Thread.sleep(10 * 1000);
    }

    public void testFetchShadowServer() {
    }
}