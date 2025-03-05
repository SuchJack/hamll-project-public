package com.hmall.trade.constants;

public interface MQConstants {

    // 订单交换机
    String DELAY_EXCHANGE_NAME = "trade.delay.direct";
    // 订单队列名字
    String DELAY_ORDER_QUEUE_NAME = "trade.delay.order.queue";
    // 订单路由key
    String DELAY_ORDER_KEY = "delay.order.query";
}