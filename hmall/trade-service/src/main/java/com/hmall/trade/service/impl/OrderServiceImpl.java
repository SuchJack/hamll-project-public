package com.hmall.trade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.api.client.CartClient;
import com.hmall.api.client.ItemClient;
import com.hmall.api.dto.ItemDTO;
import com.hmall.api.dto.OrderDetailDTO;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.utils.UserContext;
import com.hmall.trade.constants.MQConstants;
import com.hmall.trade.domain.dto.OrderFormDTO;
import com.hmall.trade.domain.po.Order;
import com.hmall.trade.domain.po.OrderDetail;
import com.hmall.trade.mapper.OrderMapper;
import com.hmall.trade.service.IOrderDetailService;
import com.hmall.trade.service.IOrderService;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    private final ItemClient itemClient;
    private final IOrderDetailService detailService;
    private final CartClient cartClient;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 创建订单
     *
     * @param orderFormDTO 订单表单DTO
     * @return 订单ID
     * @throws BadRequestException 如果商品不存在
     * @throws RuntimeException 如果库存不足
     * @GlobalTransactional 全局事务注解
     */
    @Override
    @GlobalTransactional // Seata分布式事务
    public Long createOrder(OrderFormDTO orderFormDTO) {
        // 1.订单数据
        Order order = new Order();
        // 1.1.查询商品
        List<OrderDetailDTO> detailDTOS = orderFormDTO.getDetails();
        // 1.2.获取商品id和数量的Map
        Map<Long, Integer> itemNumMap = detailDTOS.stream()
                .collect(Collectors.toMap(OrderDetailDTO::getItemId, OrderDetailDTO::getNum));
        Set<Long> itemIds = itemNumMap.keySet();
        // 1.3.查询商品
        List<ItemDTO> items = itemClient.queryItemByIds(itemIds);
        if (items == null || items.size() < itemIds.size()) {
            throw new BadRequestException("商品不存在");
        }
        // 1.4.基于商品价格、购买数量计算商品总价：totalFee
        int total = 0;
        for (ItemDTO item : items) {
            total += item.getPrice() * itemNumMap.get(item.getId());
        }
        order.setTotalFee(total);
        // 1.5.其它属性
        order.setPaymentType(orderFormDTO.getPaymentType());
        order.setUserId(UserContext.getUser());
        order.setStatus(1);
        // 1.6.将Order写入数据库order表中
        save(order);

        // 2.保存订单详情
        List<OrderDetail> details = buildDetails(order.getId(), items, itemNumMap);
        detailService.saveBatch(details);

        // 3.清理购物车商品
        cartClient.deleteCartItemByIds(itemIds);

        // 4.扣减库存
        try {
            itemClient.deductStock(detailDTOS);
        } catch (Exception e) {
            throw new RuntimeException("库存不足！");
        }
        // 5.发送延迟消息，检测订单支付状态
        rabbitTemplate.convertAndSend(
                MQConstants.DELAY_EXCHANGE_NAME,
                MQConstants.DELAY_ORDER_KEY,
                order.getId(),
                message -> {
//                     message.getMessageProperties().setDelay(900000);
                     message.getMessageProperties().setDelay(10000);  // 测试用
                     return message;
                });
        return order.getId();
    }

    @Override
    public void markOrderPaySuccess(Long orderId) {
//        Order order = new Order();
//        order.setId(orderId);
//        order.setStatus(2);
//        order.setPayTime(LocalDateTime.now());
//        updateById(order);

        // 判断和更新是两步动作，因此在极小概率下可能存在线程安全问题。
//        // 1.查询订单
//        Order old = getById(orderId);
//        // 2.判断订单状态
//        if (old == null || old.getStatus() != 1) {
//            // 订单不存在或者订单状态不是1，放弃处理
//            return;
//        }
//        // 3.尝试更新订单
//        Order order = new Order();
//        order.setId(orderId);
//        order.setStatus(2);
//        order.setPayTime(LocalDateTime.now());
//        updateById(order);

        // 合并判断+更新，防止极小概率下的线程安全问题
        // UPDATE `order` SET status = ? , pay_time = ? WHERE id = ? AND status = 1
        lambdaUpdate()
                .set(Order::getStatus, 2)
                .set(Order::getPayTime, LocalDateTime.now())
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, 1)
                .update();
    }

    @Override
    @GlobalTransactional
    public void cancelOrder(Long orderId) {

        // 1.查询订单
        Order order = getById(orderId);
        if (order == null) {
            return;
        }

        // 2.判断订单状态,状态必须是未支付
        if(order.getStatus() != 1){
            return;
        }

        // 3.标记订单已关闭
        boolean success = lambdaUpdate()
                .set(Order::getStatus, 5) // 设置订单状态为关闭
                .set(Order::getCloseTime, LocalDateTime.now())
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, 1) // 乐观锁,确保状态是未支付
                .update();
        if (!success) {
            // 订单状态已变更,无需处理
            return;
        }

        // 4.查询订单详情
        List<OrderDetail> orderDetails = detailService.lambdaQuery()
                .eq(OrderDetail::getOrderId, orderId)
                .list();
        
        // 5.构建待恢复库存的商品数据
        List<OrderDetailDTO> detailDTOs = new ArrayList<>(orderDetails.size());
        for (OrderDetail detail : orderDetails) {
            OrderDetailDTO dto = new OrderDetailDTO();
            dto.setItemId(detail.getItemId());
            dto.setNum(detail.getNum());
            detailDTOs.add(dto);
        }

        // 6.恢复库存
        try {
            itemClient.restoreStock(detailDTOs);
        } catch (Exception e) {
            log.error("恢复库存异常，order_id={}", orderId, e);
        }
    }

    private List<OrderDetail> buildDetails(Long orderId, List<ItemDTO> items, Map<Long, Integer> numMap) {
        List<OrderDetail> details = new ArrayList<>(items.size());
        for (ItemDTO item : items) {
            OrderDetail detail = new OrderDetail();
            detail.setName(item.getName());
            detail.setSpec(item.getSpec());
            detail.setPrice(item.getPrice());
            detail.setNum(numMap.get(item.getId()));
            detail.setItemId(item.getId());
            detail.setImage(item.getImage());
            detail.setOrderId(orderId);
            details.add(detail);
        }
        return details;
    }
}
