package io.goudai.starter.runner.zookeeper;

import lombok.Getter;
import lombok.Setter;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Setter
@Getter
@EnableConfigurationProperties(ZookeeperRunnerAutoConfiguration.RunnerZookeeperProperties.class)

public abstract class AbstractRunner extends LeaderSelectorListenerAdapter implements Closeable, InitializingBean {

    Logger logger = LoggerFactory.getLogger(AbstractRunner.class);


    @Autowired
    ZookeeperRunnerAutoConfiguration.RunnerZookeeperProperties properties;

    private  String name;
    private String path;
    private LeaderSelector leaderSelector;
    private final AtomicInteger leaderCount = new AtomicInteger();

    @Autowired
    private CuratorFramework curatorFramework;


    @Override
    public void afterPropertiesSet() throws Exception {
        name = this.getClass().getSimpleName();
        this.path = "/" + properties.getRoot() + "/" + this.name + "/leader";
        leaderSelector = new LeaderSelector(curatorFramework, path, this);
        leaderSelector.autoRequeue();
        leaderSelector.start();
        logger.info("this.name = {} ,this.path={} is started", this.name, this.path);
    }


    @Override
    public void close() throws IOException {
        leaderSelector.close();
    }

    @Override
    public void takeLeadership(CuratorFramework client) throws Exception {
        final long l = System.currentTimeMillis();
        logger.debug(name + " has been leader " + leaderCount.getAndIncrement() + " time(s) before.");
        try {
            logger.debug(this.getClass().getName() + " is running ");
            doRun();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            TimeUnit.SECONDS.sleep(30);
        } finally {
            final long det = System.currentTimeMillis() - l;
            if (det < 3000) {
                TimeUnit.SECONDS.sleep(3);
                logger.debug("sleep 3s ");
            } else {
                logger.debug(" det ： {}", det);
                TimeUnit.SECONDS.sleep(3);
            }
            logger.debug(name + " is now the leader. Waiting " + det + " millis...");

        }
    }

    public abstract void doRun() throws Exception;
}
