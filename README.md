## dependency

* org.apache.curator framework 2.12.0
* org.apache.curator recipes 2.12.0

# update
* 4.0.1 支持saas 多商户隔离
* 4.0.3 saas多租户模式下 异常打印出商户id
* 4.0.7 支持更加细粒度的runner配置

# Usage

## runner

* add dependency to maven
 
 ```xml
<dependency>
  <groupId>io.github.goudai</groupId>
  <artifactId>spring-boot-starter-runner-zookeeper</artifactId>
  <version>4.0.12</version>
</dependency>
 ```
 
 * using on spring boot 
 
```yaml
# application.yml
goudai:
  runner:
    zookeeper:
      zookeeper-servers: ${RUNNER_ZOOKEEPER_SERVERS:localhost:2181}
      root: ${spring.application.name}
      refresh-project-interval-seconds: 120 # 检测是否有新商家间隔 默认120s 无需设置
      runner-interval-milliseconds: 2000 # 业务运行间隔 默认2秒 无需设置
      switch-interval-milliseconds: 1000L * 10 * 60 #runner在一台节点中至少存活时间 默认10分钟无需设置
      
``` 
```java

@Component
public class UserProducerRunner extends AbstractRunner {


    /**
    * This method will ensure that only a single node is running in a clustered environment
    */
    @Override
    public void doRun() throws Exception {
       

    }
}
// SAAS 多商户模式
@Component
public class OrderStockNotifyRunner extends AbstractMultipartRunner {

	
	@Override
	// 此处方法框架自动调用 会传入商户的id 只要根据商户id 编写相关业务即可
	// 框架自动进行集群调度，会为没一个商户创建一个runner分布到所有集群的机器上随机选择一台运行
	public void apply(String projectId) throws Exception {
		log.info("OrderStockNotifyRunner apply..." + projectId);
	}

    // 返回所有商户id 后期考虑做成泛型
	@Override
	public Set<String> getAllProjects() {
		return Sets.newHashSet("1","2","3");
	}
	
	// 以下方法为4.0.7 新增 增加细粒度控制
	
	/**
	* 此方法会在apply执行之后调用 在传入的context中会携带
	* apply 开始时间，结束时间
	* 上一次休眠时间 以及projectid
	* 返回一个需要休眠的毫秒 默认为两秒
    * @param runnerContext
    * @return long 
    */
    public Long changeAndGetIntervalMilliSeconds(RunnerContext runnerContext) {
        long rim = properties.getRunnerIntervalMilliseconds();
        setIntervalMilliSeconds(runnerContext.getProjectId,rim);
        return rim;
    }

    // 此方法决定一个node至少执行多少时间
    // 如果出错讲进行调度切换
    public long getSwitchIntervalMilliseconds() {
        return properties.getSwitchIntervalMilliseconds();
    }
    
    // 此方法决定 间隔多久去检测是否有新增的商家
    public long getDelaySeconds() {
        return properties.getRefreshProjectIntervalSeconds();
    }

}
```
 
