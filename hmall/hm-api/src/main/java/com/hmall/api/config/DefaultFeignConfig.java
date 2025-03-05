package com.hmall.api.config;

import com.hmall.api.client.fallback.*;
import com.hmall.common.utils.UserContext;
import feign.Logger;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;

/**
 * Feign配置类

 */
public class DefaultFeignConfig {

    /**
     * 返回一个Feign日志级别为FULL的Logger.Level对象
     * (Feign默认的日志级别就是NONE，所以默认看不到请求日志。)
     * @return Logger.Level.FULL日志级别的Feign日志对象
     */
    @Bean
    public Logger.Level feignLogLevel(){
        return Logger.Level.FULL;
    }

    /**
     * 返回一个自定义的RequestInterceptor，用于在发送请求前添加用户信息到请求头中
     * (让每一个由OpenFeign发起的请求自动携带登录用户信息)
     * @return RequestInterceptor 用于在发送请求前添加用户信息到请求头中的拦截器
     */
    @Bean
    public RequestInterceptor userInfoRequestInterceptor(){
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                // 获取登录用户
                Long userId = UserContext.getUser();
                if(userId == null) {
                    // 如果为空则直接跳过
                    return;
                }
                // 如果不为空则放入请求头中，传递给下游微服务
                template.header("user-info", userId.toString());
            }
        };
    }


    /**
     * 创建一个ItemClientFallback的Bean对象，用于在调用ItemClient时发生异常时进行回退处理
     *
     * @return 返回一个ItemClientFallback对象
     */
    @Bean
    public ItemClientFallback itemClientFallback(){
        return new ItemClientFallback();
    }

    @Bean
    public CartClientFallback cartClientFallback(){
        return new CartClientFallback();
    }

    @Bean
    public UserClientFallback userClientFallback(){
        return new UserClientFallback();
    }

    @Bean
    public TradeClientFallback tradeClientFallback(){
        return new TradeClientFallback();
    }

    @Bean
    public PayClientFallback payClientFallback(){
        return new PayClientFallback();
    }
}
