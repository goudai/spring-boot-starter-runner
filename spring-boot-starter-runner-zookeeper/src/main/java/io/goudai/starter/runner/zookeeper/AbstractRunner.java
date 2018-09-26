package io.goudai.starter.runner.zookeeper;

import io.goudai.starter.runner.zookeeper.ZookeeperRunnerAutoConfiguration.RunnerZookeeperProperties;
import jodd.mail.Email;
import jodd.mail.SendMailSession;
import jodd.mail.SmtpServer;
import lombok.*;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.io.Closeable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Setter
@Getter
@EnableConfigurationProperties(RunnerZookeeperProperties.class)
@Configuration
public abstract class AbstractRunner extends LeaderSelectorListenerAdapter implements Closeable, InitializingBean {

    Logger logger = LoggerFactory.getLogger(AbstractRunner.class);

    @Autowired
    protected RunnerZookeeperProperties properties;
    @Autowired
    private CuratorFramework curatorFramework;
    @Autowired
    private Environment environment;


    protected String name;
    protected String path;
    protected String projectId;
    protected LeaderSelector leaderSelector;
    private final AtomicInteger leaderCount = new AtomicInteger();

    private SmtpServer smtpServer;
    private BlockingQueue<EmailMate> emailQueue;


    @Autowired
    private ZookeeperRunnerAutoConfiguration.RunnerZookeeperProperties runnerZookeeperProperties;


    public AbstractRunner(String name, String projectId) {
        this.projectId = projectId;
        this.name = name;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.name = StringUtils.isEmpty(name) ? this.getClass().getSimpleName() : name;
        this.path = "/" + properties.getRoot() + "/" + this.name + "/leader";
        this.leaderSelector = new LeaderSelector(curatorFramework, path, this);
        leaderSelector.autoRequeue();
        leaderSelector.start();
        logger.info("name = {} ,path={} is started", this.name, this.path);
    }


    @Override
    public void close() {
        leaderSelector.close();
    }

    @Override
    public void takeLeadership(CuratorFramework client) throws Exception {
        try {
            final long l = System.currentTimeMillis();
            logger.debug(name + " has been leader " + leaderCount.getAndIncrement() + " time(s) before.");
            final String smsPath = "/" + this.name + "sendSms";
            try {
                logger.debug(this.getClass().getName() + " is running ");
                doRun();
            } catch (InterruptedException e) {
                // ig
            } catch (Exception e) {
                final String format = "runner[" + this.name + "]异常，请排查 sleep 30s";
                send(format, e);
                logger.error(format, e);
                try {
                    TimeUnit.SECONDS.sleep(30);
                } catch (Exception e1) {
                }
            } finally {
                try {
                    final long det = System.currentTimeMillis() - l;
                    final long intervalMilliSeconds = getIntervalMilliSeconds();
                    if (det < intervalMilliSeconds) {
                        TimeUnit.SECONDS.sleep(intervalMilliSeconds);
                    }
                } catch (Exception e2) {
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

    }

    private void send(String format, Throwable e) {
        if (runnerZookeeperProperties.getEmail().isEnabled()) {
            init();
            final String[] activeProfiles = environment.getActiveProfiles();
            if (projectId.equals("1") || projectId.equals("23")) {

                emailQueue.offer(EmailMate.builder().message(format).throwable(e).build());
            } else {
                if (activeProfiles != null && activeProfiles.length > 0 && !properties.getProfiles().isEmpty()) {
                    for (String activeProfile : activeProfiles) {
                        for (String profile : properties.getProfiles()) {
                            if (activeProfile.equals(profile)) {
                                if (runnerZookeeperProperties.getEmail() != null && runnerZookeeperProperties.getEmail().isEnabled()) {
                                    emailQueue.offer(EmailMate.builder().message(format).throwable(e).build());
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public long getIntervalMilliSeconds() {
        return 3000L;
    }

    private boolean contains(String message) {
        final List<String> ignoreExceptionList = this.properties.getIgnoreExceptionList();
        for (String ex : ignoreExceptionList) {
            if (message.contains(ex))
                return true;
        }
        return false;
    }

    public abstract void doRun() throws Exception;


    public void startEmail() {
        if (runnerZookeeperProperties.getEmail().isEnabled()) {
            Executors.newCachedThreadPool().execute(() -> {
                Thread thread = Thread.currentThread();
                thread.setName("Email-Consumer-Thread ");
                while (runnerZookeeperProperties.getEmail().isEnabled()) {
                    try {
                        final EmailMate take = emailQueue.take();
                        final String message = take.message;

                        try (final StringWriter out = new StringWriter();
                             final PrintWriter printWriter = new PrintWriter(out);
                             final SendMailSession session = smtpServer.createSession();) {
                            session.open();
                            take.throwable.printStackTrace(printWriter);
                            final RunnerZookeeperProperties.Email.Smtp smtp = runnerZookeeperProperties.getEmail().getSmtp();
                            session.sendMail(Email.create()
                                    .from(smtp.getFrom())
                                    .to(smtp.getTo().toArray(new String[smtp.getTo().size()]))
                                    .subject(message)
                                    .htmlMessage(
                                            String.format("<html><META http-equiv=Content-Type content=\"text/html; " +
                                                    "charset=utf-8\"><body>%s</body></html>", out.toString().replaceAll("(\r\n|\n)", "<br />"))
                                            , "utf-8")
                            );
                        }
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            });
        }

    }


    private void init() {
        if (runnerZookeeperProperties.getEmail().isEnabled()) {
            if (smtpServer == null) {
                synchronized (AbstractRunner.class) {
                    if (smtpServer == null) {
                        if (runnerZookeeperProperties.getEmail().isEnabled()) {
                            this.emailQueue = new ArrayBlockingQueue<>(runnerZookeeperProperties.getEmail().getEmailQueueSize());
                            final ZookeeperRunnerAutoConfiguration.RunnerZookeeperProperties.Email.Smtp smtp = runnerZookeeperProperties.getEmail().getSmtp();
                            this.smtpServer = SmtpServer.create()
                                    .host(smtp.getHost())
                                    .port(smtp.getPort())
                                    .ssl(smtp.isUseSSL())
                                    .auth(smtp.getUsername(), smtp.getPassword())
                                    .debugMode(smtp.isDebugMode())
                                    .buildSmtpMailServer();
                            startEmail();
                        } else {
                            this.emailQueue = new ArrayBlockingQueue<>(0);
                        }
                    }
                }
            }
        }
    }

    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Setter
    @Getter
    @ToString
    public static class EmailMate {
        private Throwable throwable;
        private String message;
    }
}
