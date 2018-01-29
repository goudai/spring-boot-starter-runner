package io.goudai.starter.runner.zookeeper;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Setter
@Slf4j
@Getter
@EnableConfigurationProperties(ZookeeperRunnerAutoConfiguration.RunnerZookeeperProperties.class)
public abstract class AbstractMultipartRunner implements InitializingBean, DisposableBean {

    private Map<String, AbstractRunner> runnerMap = new ConcurrentHashMap<String, AbstractRunner>();
    private ScheduledExecutorService scheduledExecutorService;
    private long delay = 20;

    @Autowired
    protected ZookeeperRunnerAutoConfiguration.RunnerZookeeperProperties properties;
    @Autowired
    private CuratorFramework curatorFramework;

    private CountDownLatch countDownLatch = new CountDownLatch(1);
    private AtomicBoolean isStarted = new AtomicBoolean(false);

    @Override
    public void afterPropertiesSet() throws Exception {
        String simpleName = this.getClass().getSimpleName();
        for (final String projectId : getAllProjects()) {
            initRunner(simpleName, projectId);
        }
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName(simpleName + " Thread ");
            return thread;
        });
        scheduledExecutorService.scheduleWithFixedDelay(this::refresh, 120, delay, TimeUnit.SECONDS);
        countDownLatch.countDown();
    }

    private void initRunner(String simpleName, String projectId) {
        try {
            AbstractRunner runner = new AbstractRunner(simpleName + projectId) {
                public void doRun() throws Exception {
                    if (!isStarted.get()) {
                        countDownLatch.await();
                        isStarted.set(true);
                    }
                    apply(projectId);
                }
            };
            runner.setCuratorFramework(curatorFramework);
            runner.setProperties(properties);
            runner.afterPropertiesSet();
            runnerMap.put(projectId, runner);
        } catch (Exception e) {
            log.error(String.format("init projectId %s failed ", projectId), e);
        }

    }

    private void refresh() {
        try {
            String simpleName = this.getClass().getSimpleName();
            for (String projectId : getAllProjects()) {
                if (!runnerMap.containsKey(projectId)) {
                    initRunner(simpleName, projectId);
                }
            }
        } catch (Exception e) {
            log.error("get all project failed", e);
        }


    }


    /**
     * 此方法传入项目id
     *
     * @param projectId
     * @throws Exception
     */
    public abstract void apply(String projectId) throws Exception;

    /**
     * 定时检测项目runner是否启用
     *
     * @return
     */
    public abstract Set<String> getAllProjects();

    @Override
    public void destroy() {
        try {
            scheduledExecutorService.shutdownNow();
        } catch (Exception e) {
            // ig
        }
        for (AbstractRunner runner : runnerMap.values()) {
            try {
                runner.close();
            } catch (Exception e) {
                // ig
            }
        }
    }
}
