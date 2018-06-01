package io.goudai.starter.runner.zookeeper;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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


    @Setter
    @Getter
    @ConfigurationProperties(prefix = "goudai.runner.zookeeper")
    public static class RunnerZookeeperProperties {

        private String zookeeperServers;

        private String root;

        private long runnerIntervalMilliseconds = DEFAULT_INTERVAL_MILLISECONDS;

        private long switchIntervalMilliseconds = SWITCH_INTERVAL_MILLISECONDS;

        private long refreshProjectIntervalSeconds = REFRESH_PROJECT_INTERVAL_SECONDS;


    }

}
