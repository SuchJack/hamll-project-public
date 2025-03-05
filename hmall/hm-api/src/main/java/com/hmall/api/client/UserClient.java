package com.hmall.api.client;

import com.hmall.api.client.fallback.UserClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(value = "user-service",fallbackFactory = UserClientFallback.class)
public interface UserClient {

    /**
     * 从用户余额中扣除指定金额
     *
     * @param pw 用户的支付密码
     * @param amount 需要扣除的金额
     */
    @PutMapping("/users/money/deduct")
    void deductMoney(@RequestParam("pw") String pw, @RequestParam("amount") Integer amount);
}
