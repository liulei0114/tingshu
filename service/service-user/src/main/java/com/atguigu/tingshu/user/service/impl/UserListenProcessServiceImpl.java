package com.atguigu.tingshu.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.atguigu.tingshu.model.user.UserListenProcess;
import com.atguigu.tingshu.user.service.UserListenProcessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;

@Service
@SuppressWarnings({"all"})
public class UserListenProcessServiceImpl implements UserListenProcessService {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public UserListenProcess getUserListenProcess(Long userId, Long trackId) {
        UserListenProcess userListenProcess = mongoTemplate.findOne(
                new Query(Criteria.where("userId").is(userId).and("trackId").is(trackId)),
                UserListenProcess.class);
        return userListenProcess;
    }


    public void saveOrUpdateUserListenProcess(UserListenProcess userListenProcess) {
        mongoTemplate.save(userListenProcess);
    }

    @Override
    public Map<String, Long> getLatelyTrack(Long userId) {
        // 1.根据userId查询最近一条播放进度记录（按更新时间倒序取第1条）
        UserListenProcess userListenProcess = mongoTemplate.findOne(
                new Query(Criteria.where("userId").is(userId))
                        .with(Sort.by(Sort.Direction.DESC, "updateTime"))
                        .limit(1),
                UserListenProcess.class);
        // 2.无收听记录时返回空Map
        if (userListenProcess == null) {
            return Map.of();
        }
        // 3.封装最近收听的专辑id和声音id（trackId为0表示浏览的是专辑）
        return Map.of(
                "albumId", userListenProcess.getAlbumId(),
                "trackId", userListenProcess.getTrackId());
    }
}
