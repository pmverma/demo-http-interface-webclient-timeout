package com.example.demohttpinterfacewebclienttimeout;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@HttpExchange
interface SampleApi {

    @GetExchange("http://localhost:8080/test")
    ResponseEntity<String> get();

}

@RestController
class Controller {
    @GetMapping("/test")
    public String test() throws InterruptedException {
        Thread.sleep(10000L);
        return "Hello World!";
    }
}

@Configuration(proxyBeanMethods = false)
class Config {
    @Bean
    public WebClient webClient(WebClient.Builder webClientBuilder) {

        // Reactor Netty HttpClient
        var rhttpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(30L))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30_000) // millis
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(30)) // seconds
                        .addHandlerLast(new WriteTimeoutHandler(30))); //seconds
        var reactorClientHttpConnector = new ReactorClientHttpConnector(rhttpClient);

        // Java 11 HttpClient
        var jhttpClient = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30L))
                .build();
        var java11ClientHttpConnector =  new JdkClientHttpConnector(jhttpClient);


        return WebClient.builder().clientConnector(reactorClientHttpConnector).build();
    }

    @Bean
    public SampleApi sampleApi(WebClient webClient) {
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builder(WebClientAdapter.forClient(webClient)).build();
        return factory.createClient(SampleApi.class);
    }
}

@SpringBootApplication
public class DemoHttpInterfaceWebclientTimeoutApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoHttpInterfaceWebclientTimeoutApplication.class, args);
    }

    @EventListener
    public void onStartup(ApplicationStartedEvent event) {
        // Non-blocking, working fine
        event.getApplicationContext().getBean(WebClient.class)
                .get()
                .uri("http://localhost:8080/test")
                .retrieve().bodyToMono(String.class)
                .subscribe(System.out::println);

        // Blocking, working fine
        String result = event.getApplicationContext().getBean(WebClient.class)
                .get()
                .uri("http://localhost:8080/test")
                .retrieve().bodyToMono(String.class)
                .block();
        System.out.println(result);

        // Blocking but with HttpInterface, is failing
        SampleApi sampleApi = event.getApplicationContext().getBean(SampleApi.class);
        System.out.println(sampleApi.get().getStatusCode());

    }
}