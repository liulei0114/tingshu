package com.atguigu.tingshu.user.service;

import com.atguigu.tingshu.model.user.UserListenProcess;

import java.util.Map;

public interface UserListenProcessService {


    UserListenProcess getUserListenProcess(Long userId, Long trackId);

    void saveOrUpdateUserListenProcess(UserListenProcess userListenProcess);

    Map<String, Long> getLatelyTrack(Long userId);
}