package com.atguigu.tingshu.user.service;

import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.vo.album.AlbumTrackListVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.atguigu.tingshu.vo.user.UserListenProcessVo;
import com.atguigu.tingshu.vo.user.UserPaidRecordVo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface UserInfoService extends IService<UserInfo> {

    Map<String, String> wxLogin(String code);

    UserInfoVo getUserInfo(Long userId);

    void updateUserInfo(UserInfoVo userInfoVo, String token);

    List<AlbumTrackListVo> userIsPaidTrack(Long userId, Long albumId, List<AlbumTrackListVo> needCheckPayStatusTrackIdList);

    BigDecimal getTrackBreakSecond(Long userId, Long trackId);

    void updateUserListenProcess(Long userId, UserListenProcessVo userListenProcessVo);

    Boolean isPaidAlbum(Long userId, Long albumId);

    List<Long> findUserPaidTrackList(Long userId, Long albumId);

    void savePaidRecord(UserPaidRecordVo userPaidRecordVo);
}
