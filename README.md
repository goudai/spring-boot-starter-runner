## dependency

* org.apache.curator framework 2.12.0
* org.apache.curator recipes 2.12.0

# Usage



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

```
 
