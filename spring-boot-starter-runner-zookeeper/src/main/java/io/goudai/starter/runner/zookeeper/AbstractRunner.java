package io.goudai.starter.runner.zookeeper;

import lombok.Getter;
import lombok.Setter;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.Closeable;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.goudai.starter.runner.zookeeper.ZookeeperRunnerAutoConfiguration.RunnerZookeeperProperties;

@Setter
@Getter
@EnableConfigurationProperties(RunnerZookeeperProperties.class)
public abstract class AbstractRunner extends LeaderSelectorListenerAdapter implements Closeable, InitializingBean {

    Logger logger = LoggerFactory.getLogger(AbstractRunner.class);

    @Autowired
    protected RunnerZookeeperProperties properties;
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
    public void afterPropertiesSet() {
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
        try{
            final long l = System.currentTimeMillis();
            logger.debug(name + " has been leader " + leaderCount.getAndIncrement() + " time(s) before.");
            final String smsPath = "/"+this.name+"sendSms";
            try {
                logger.debug(this.getClass().getName() + " is running ");
                doRun();
                final Stat stat = client.checkExists().forPath(smsPath);
                if (stat != null) {
                    final String format = String.format("%s:恢复正常，事件发生时间:[%s] 事件描述 %s %s"
                            , this.name
                            , new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                            , "成功执行"
                            , properties.getSign()
                    );
                    SmsUtils.send(format,properties.getApiKey(),properties.getPhoneList());
                    client.delete().forPath(smsPath);
                }
            }catch (InterruptedException e){
                // ig
            }

            catch (Exception e) {
                final Stat stat = client.checkExists().forPath(smsPath);
                final String message = e.getMessage();
                if (stat == null && (!StringUtils.isEmpty(properties.getApiKey())) && properties.getPhoneList() != null && !properties.getPhoneList().isEmpty()) {
                    final String format = String.format("%s:发生错误，事件发生时间:[%s] 事件描述 %s %s"
                            , this.name
                            , new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                            , message
                            , properties.getSign()
                    );
                    SmsUtils.send(format,properties.getApiKey(),properties.getPhoneList());
                    client.create().forPath(smsPath);
                }else {
                    logger.warn("api key is null, skip send sms");
                }
                logger.error("sleep 30s  name = " + this.name + " path = " + this.path + " message : " + message, e);
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
        }catch (Exception e){
            logger.error(e.getMessage(),e);
        }

    }



    public abstract void doRun() throws Exception;
}
