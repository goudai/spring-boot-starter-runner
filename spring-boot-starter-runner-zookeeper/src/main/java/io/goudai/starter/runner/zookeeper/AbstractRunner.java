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
import org.springframework.util.StringUtils;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Setter
@Getter
@EnableConfigurationProperties(ZookeeperRunnerAutoConfiguration.RunnerZookeeperProperties.class)
public abstract class AbstractRunner extends LeaderSelectorListenerAdapter implements Closeable, InitializingBean {

    Logger logger = LoggerFactory.getLogger(AbstractRunner.class);


    @Autowired
    protected ZookeeperRunnerAutoConfiguration.RunnerZookeeperProperties properties;
    @Autowired
    private CuratorFramework curatorFramework;


    protected String name;
    protected String path;
    protected String id;
    protected LeaderSelector leaderSelector;
    private final AtomicInteger leaderCount = new AtomicInteger();


    public AbstractRunner(String name) {
        this.name = name;
    }

    public AbstractRunner() {
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        name = StringUtils.isEmpty(name) ? this.getClass().getSimpleName() : name;
        this.path = "/" + properties.getRoot() + "/" + this.name + "/leader";
        leaderSelector = new LeaderSelector(curatorFramework, path, this);
        leaderSelector.autoRequeue();
        leaderSelector.start();
        logger.info("name = {} ,path={} is started", this.name, this.path);
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

            do {
                doRun();
                long currentTime = System.currentTimeMillis();
                //保证节点至少干活5分钟才进行切换
                // 切换过快导致zk 负载较高
                if (currentTime - l > 1000 * 60 * 5) {

                    break;
                } else {
                    // 干完一轮活儿 休息一下
                    TimeUnit.SECONDS.sleep(5);
                }
            } while (true);
            logger.error(" 已经干活超过5分钟 进行切换");
        } catch (Exception e) {
            logger.error("sleep 30s  name = " + this.name + " path = " + this.path + " message : " + e.getMessage(), e);
            try {
                TimeUnit.SECONDS.sleep(30);
            } catch (Exception e1) {
            }
        } finally {
            try {
                final long det = System.currentTimeMillis() - l;
                if (det < 3000) {
                    TimeUnit.SECONDS.sleep(3);
                    logger.debug("sleep 3s ");
                } else {
                    logger.debug(" det ： {}", det);
                    TimeUnit.SECONDS.sleep(3);
                }
                logger.debug(name + " is now the leader. Waiting " + det + " millis...");
            } catch (Exception e2) {
            }
        }
    }

    public abstract void doRun() throws Exception;
}
