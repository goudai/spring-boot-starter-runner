# Usage

## dependency

* org.apache.curator framework 2.12.0
* org.apache.curator recipes 2.12.0

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
    * 此方法将保证在集群环境中 只有单个节点在运行次方法
    */
    @Override
    public void doRun() throws Exception {
       

    }
}

```
 
