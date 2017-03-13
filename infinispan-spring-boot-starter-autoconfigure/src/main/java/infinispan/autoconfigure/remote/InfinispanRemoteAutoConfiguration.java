package infinispan.autoconfigure.remote;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.Properties;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import infinispan.autoconfigure.common.InfinispanProperties;

@Configuration
@ComponentScan
//Since a jar with configuration might be missing (which would result in TypeNotPresentExceptionProxy), we need to
//use String based methods.
//See https://github.com/spring-projects/spring-boot/issues/1733
@ConditionalOnClass(name = "org.infinispan.client.hotrod.RemoteCacheManager")
@ConditionalOnProperty(value = "infinispan.remote.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(InfinispanProperties.class)
public class InfinispanRemoteAutoConfiguration {

   public static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Autowired
   private InfinispanProperties infinispanProperties;

   @Autowired(required = false)
   private InfinispanRemoteConfigurer infinispanRemoteConfigurer;

   @Autowired
   private ApplicationContext ctx;

   @Bean
   @Conditional(InfinispanRemoteCacheManagerChecker.class)
   public RemoteCacheManager remoteCacheManager() throws IOException {
      InfinispanProperties.Remote remoteProperties = infinispanProperties.getRemote();

      boolean hasHotRodPropertiesFile = ctx.getResource(remoteProperties.getClientProperties()).exists();
      boolean hasConfigurer = infinispanRemoteConfigurer != null;
      boolean hasProperties = StringUtils.hasText(remoteProperties.getServerList());

      org.infinispan.client.hotrod.configuration.Configuration configuration;
      if (hasConfigurer) {
         configuration = infinispanRemoteConfigurer.getRemoteConfiguration();
      } else if (hasHotRodPropertiesFile) {
         String remoteClientPropertiesLocation = remoteProperties.getClientProperties();
         Resource hotRodClientPropertiesFile = ctx.getResource(remoteClientPropertiesLocation);
         Properties hotrodClientProperties = new Properties();
         try (InputStream stream = hotRodClientPropertiesFile.getURL().openStream()) {
            hotrodClientProperties.load(stream);
            configuration = new ConfigurationBuilder().withProperties(hotrodClientProperties).build();
         }
      } else if (hasProperties) {
         ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
         configurationBuilder.addServers(remoteProperties.getServerList());
         Optional.ofNullable(remoteProperties.getConnectTimeout()).map(v -> configurationBuilder.connectionTimeout(v));
         Optional.ofNullable(remoteProperties.getMaxRetries()).map(v -> configurationBuilder.maxRetries(v));
         Optional.ofNullable(remoteProperties.getSocketTimeout()).map(v -> configurationBuilder.socketTimeout(v));
         configuration = configurationBuilder.build();
      } else {
         throw new IllegalStateException("Not enough data to create RemoteCacheManager. Check InfinispanRemoteCacheManagerChecker" +
               "and update conditions.");
      }
      return new RemoteCacheManager(configuration);
   }
}