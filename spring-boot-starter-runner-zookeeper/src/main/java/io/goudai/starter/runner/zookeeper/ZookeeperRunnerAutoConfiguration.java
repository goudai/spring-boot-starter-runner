package io.goudai.starter.runner.zookeeper;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableConfigurationProperties(ZookeeperRunnerAutoConfiguration.RunnerZookeeperProperties.class)
@Slf4j
public class ZookeeperRunnerAutoConfiguration {

    /**
     * 默认runner运行间隔
     */
    private static Long DEFAULT_INTERVAL_MILLISECONDS = 2000L;
    /**
     * runner在一台节点中至少存活时间
     */
    private static Long SWITCH_INTERVAL_MILLISECONDS = 1000L * 10 * 60;
    /**
     * 新项目检测间隔
     */
    private static int REFRESH_PROJECT_INTERVAL_SECONDS = 120;


    @Bean
    public CuratorFramework curatorFramework(RunnerZookeeperProperties properties) {
        CuratorFramework curatorFramework = CuratorFrameworkFactory.newClient(properties.zookeeperServers, new ExponentialBackoffRetry(1000, 20));
        curatorFramework.start();
        return curatorFramework;
    }

    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }


    @Setter
    @Getter
    @ConfigurationProperties(prefix = "goudai.runner.zookeeper")
    public static class RunnerZookeeperProperties {

        private String zookeeperServers;

        private String root;

        private long runnerIntervalMilliseconds = DEFAULT_INTERVAL_MILLISECONDS;

        private long switchIntervalMilliseconds = SWITCH_INTERVAL_MILLISECONDS;

        private long refreshProjectIntervalSeconds = REFRESH_PROJECT_INTERVAL_SECONDS;

        private List<String> ignoreExceptionList = Arrays.asList("com.my.common.exception.ConcurrentException");

        private List<String> profiles = Arrays.asList("prod");

        private Email email = new Email();

        @Setter
        @Getter
        public static class Email {
            private boolean enabled = false;
            private Smtp smtp;
            private int emailQueueSize;

            @Setter
            @Getter
            public static class Smtp {
                private String host;
                private int port;
                private String username;
                private String password;
                private boolean useSSL;
                private boolean debugMode = false;
                private String from;
                private List<String> to;

            }
        }
    }

}
