package com.atguigu.tingshu.user.service.impl;

import com.atguigu.tingshu.user.service.DeliveryStrategy;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DeliveryStrategyFactory {

    private final Map<String, DeliveryStrategy> strategyMap;

    public DeliveryStrategyFactory(Map<String, DeliveryStrategy> strategyMap) {
        this.strategyMap = strategyMap;
    }

    /**
     * 根据订单明细物品类型获取发货策略
     * Spring 会以 bean 名称（即物品类型码）作为 key 注入所有 DeliveryStrategy 实现
     */
    public DeliveryStrategy getStrategy(String itemType) {
        DeliveryStrategy strategy = strategyMap.get(itemType);
        if (strategy == null) {
            throw new IllegalArgumentException("Invalid strategy: " + itemType);
        }
        return strategy;
    }
}
