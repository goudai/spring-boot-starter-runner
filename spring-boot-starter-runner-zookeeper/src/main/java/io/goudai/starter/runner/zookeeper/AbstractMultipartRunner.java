package io.goudai.starter.runner.zookeeper;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Setter
@Slf4j
@Getter
@EnableConfigurationProperties(ZookeeperRunnerAutoConfiguration.RunnerZookeeperProperties.class)
public abstract class AbstractMultipartRunner implements InitializingBean, DisposableBean {

    private Map<String, AbstractRunner> runnerMap = new ConcurrentHashMap<String, AbstractRunner>();
    private ScheduledExecutorService scheduledExecutorService;

    @Autowired
    protected ZookeeperRunnerAutoConfiguration.RunnerZookeeperProperties properties;
    @Autowired
    private CuratorFramework curatorFramework;

    private Map<String, Long> intervalMilliSecondsMap = new ConcurrentHashMap<>();


    private CountDownLatch countDownLatch = new CountDownLatch(1);
    private AtomicBoolean isStarted = new AtomicBoolean(false);

    @Override
    public void afterPropertiesSet() {
        String simpleName = this.getClass().getSimpleName();
        for (final String projectId : getAllProjects0()) {
            initRunner(simpleName, projectId);
        }
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName(simpleName + " Thread ");
            return thread;
        });
        scheduledExecutorService.scheduleWithFixedDelay(this::refresh, 120, getDelaySeconds(), TimeUnit.SECONDS);
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
                    intervalMilliSecondsMap.putIfAbsent(projectId, properties.getRunnerIntervalMilliseconds());
                    final long l = System.currentTimeMillis();
                    do {
                        final Date beginTime = new Date();
                        apply(projectId);
                        final Date endTime = new Date();
                        if (endTime.getTime() - l > getSwitchIntervalMilliseconds()) {
                            break;
                        }
                        Long intervalMilliSeconds = intervalMilliSecondsMap.get(projectId);
                        if (beginTime.getTime() - endTime.getTime() < intervalMilliSeconds) {
                            TimeUnit.MILLISECONDS.sleep(changeAndGetIntervalMilliSeconds(RunnerContext
                                    .builder()
                                    .beginTime(beginTime)
                                    .endTime(endTime)
                                    .projectId(projectId)
                                    .preSleepMilliSeconds(intervalMilliSeconds)
                                    .build()
                            ));
                        }
                    } while (true);
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
            for (String projectId : getAllProjects0()) {
                if (!runnerMap.containsKey(projectId)) {
                    initRunner(simpleName, projectId);
                }
            }
        } catch (Exception e) {
            log.error("get all project failed", e);
        }
    }


    public Long changeAndGetIntervalMilliSeconds(RunnerContext runnerContext) {
        return properties.getRunnerIntervalMilliseconds();
    }

    public long getSwitchIntervalMilliseconds() {
        return properties.getSwitchIntervalMilliseconds();
    }

    public long getDelaySeconds() {
        return properties.getRefreshProjectIntervalSeconds();
    }


    public void setIntervalMilliSeconds(String projectId, Long intervalMilliSeconds) {
        intervalMilliSecondsMap.put(projectId, intervalMilliSeconds);
    }


    /**
     * 此方法传入项目id
     *
     * @param projectId
     * @throws Exception
     */
    public abstract void apply(String projectId) throws Exception;

    /**
     * 定时检测项目是否有新的项目添加进来
     *
     * @return
     */
    public abstract Set<String> getAllProjects();

    public Set<String> getAllProjects0(){
        try {
            return getAllProjects();
        }catch (Exception e){
            log.error("获取全部project失败2分钟中重试",e);
            return new HashSet<>(0);
        }
    }

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

    @AllArgsConstructor
    @NoArgsConstructor
    @Setter
    @Getter
    @Builder
    public static class RunnerContext {
        private Date beginTime;
        private Date endTime;
        private String projectId;
        private Long preSleepMilliSeconds;
    }
}
