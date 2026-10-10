package com.atguigu.tingshu.receiver;

import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.search.service.SearchService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class SearchReceiver {

    @Autowired
    private SearchService searchService;

    @RabbitListener(
            bindings = {
                    @QueueBinding(
                            exchange = @Exchange(value = MqConst.EXCHANGE_ALBUM, durable = "true"),
                            value = @Queue(value = MqConst.QUEUE_ALBUM_UPPER, durable = "true"),
                            key = MqConst.ROUTING_ALBUM_UPPER
                    )
            }
    )
    public void upperAlbum(Long id, Channel channel, Message message) throws IOException {

        log.info("[搜索服务]上架专辑,专辑id：{}", id);
        if (id != null) {
            searchService.upperAlbum(id);
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    @RabbitListener(
            bindings = {
                    @QueueBinding(
                            exchange = @Exchange(value = MqConst.EXCHANGE_ALBUM, durable = "true"),
                            value = @Queue(value = MqConst.QUEUE_ALBUM_LOWER, durable = "true"),
                            key = MqConst.ROUTING_ALBUM_LOWER
                    )
            }
    )
    public void lowerAlbum(Long id, Channel channel, Message message) throws IOException {

        log.info("[搜索服务]下架专辑,专辑id：{}", id);
        if (id != null) {
            searchService.lowerAlbum(String.valueOf(id));
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }
}
