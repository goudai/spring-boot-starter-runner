package io.goudai.starter.runner.zookeeper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.Charset;
import java.util.Base64;
import java.util.List;

@Slf4j
public class SmsUtils {

    public static RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private ZookeeperRunnerAutoConfiguration.RunnerZookeeperProperties properties;
    private static final String URL = "http://sms-api.luosimao.com/v1/send.json";


    public static void send(String format, String apiKey, List<String> phoneList) {
        String authorization = "Basic " + Base64.getEncoder().encodeToString(("api:" + apiKey).getBytes(Charset.forName("UTF-8")));
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authorization);
        headers.set("Content-Type", "application/x-www-form-urlencoded");

        for (String phone : phoneList) {
            LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("mobile", phone);
            params.add("message", format);
            final String post = restTemplate.postForObject(URL, new HttpEntity<>(params, headers), String.class);
            log.info(post + "提交告警信息成功");
        }
    }
}
