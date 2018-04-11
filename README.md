## dependency

* org.apache.curator framework 2.12.0
* org.apache.curator recipes 2.12.0

# update
* 4.0.1 支持saas 多商户隔离
* 4.0.3 saas多租户模式下 异常打印出商户id

# Usage

## Download

</br> wget https://github.com/goudai/spring-boot-starter-runner/archive/spring-boot-starter-runner-4.0.3.zip


## runner

* add dependency to maven
 
 ```xml
<dependency>
    <groupId>io.goudai</groupId>
    <artifactId>spring-boot-starter-runner-zookeeper</artifactId>
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

}
```
 
