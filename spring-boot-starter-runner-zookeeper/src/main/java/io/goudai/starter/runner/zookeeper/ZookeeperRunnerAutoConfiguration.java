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

        private int runningIntervalSeconds = 1;

        private int switchRunningIntervalSeconds = 60 * 10;


    }

}
