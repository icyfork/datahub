package com.linkedin.gms.factory.kafka;

import com.linkedin.gms.factory.kafka.schemaregistry.AwsGlueSchemaRegistryFactory;
import com.linkedin.gms.factory.kafka.schemaregistry.KafkaSchemaRegistryFactory;
import com.linkedin.gms.factory.kafka.schemaregistry.SchemaRegistryConfig;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;


@Slf4j
@Configuration
@EnableConfigurationProperties(KafkaProperties.class)
@Import({KafkaSchemaRegistryFactory.class, AwsGlueSchemaRegistryFactory.class})
public class KafkaEventConsumerFactory {

  @Value("${KAFKA_BOOTSTRAP_SERVER:http://localhost:9092}")
  private String kafkaBootstrapServers;

  @Value("${SCHEMA_REGISTRY_TYPE:KAFKA}")
  private String schemaRegistryType;

  @Autowired
  @Lazy
  @Qualifier("kafkaSchemaRegistry")
  private SchemaRegistryConfig kafkaSchemaRegistryConfig;

  @Autowired
  @Lazy
  @Qualifier("awsGlueSchemaRegistry")
  private SchemaRegistryConfig awsGlueSchemaRegistryConfig;

  @Bean(name = "kafkaEventConsumer")
  protected KafkaListenerContainerFactory<?> createInstance(KafkaProperties properties) {

    KafkaProperties.Consumer consumerProps = properties.getConsumer();

    // Specify (de)serializers for record keys and for record values.
    consumerProps.setKeyDeserializer(StringDeserializer.class);
    // Records will be flushed every 10 seconds.
    consumerProps.setEnableAutoCommit(true);
    consumerProps.setAutoCommitInterval(Duration.ofSeconds(10));

    // KAFKA_BOOTSTRAP_SERVER has precedence over SPRING_KAFKA_BOOTSTRAP_SERVERS
    if (kafkaBootstrapServers != null && kafkaBootstrapServers.length() > 0) {
      consumerProps.setBootstrapServers(Arrays.asList(kafkaBootstrapServers.split(",")));
    } // else we rely on KafkaProperties which defaults to localhost:9092

    SchemaRegistryConfig schemaRegistryConfig;
    if (schemaRegistryType.equals(KafkaSchemaRegistryFactory.TYPE)) {
      schemaRegistryConfig = kafkaSchemaRegistryConfig;
    } else {
      schemaRegistryConfig = awsGlueSchemaRegistryConfig;
    }

    consumerProps.setValueDeserializer(schemaRegistryConfig.getDeserializer());
    Map<String, Object> props = properties.buildConsumerProperties();
    props.putAll(schemaRegistryConfig.getProperties());

    ConcurrentKafkaListenerContainerFactory<String, GenericRecord> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props));

    log.info("Event-based KafkaListenerContainerFactory built successfully");

    return factory;
  }
}