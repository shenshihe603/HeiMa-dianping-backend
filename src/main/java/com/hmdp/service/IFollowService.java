package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {
    /**
     * //关注
     * @param followUserId 被关注者的id
     * @param isFollow 关注状态true or false
     * @return Result
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     * //判断有没有关注该用户
     * @param followUserId  被关注者的id
     * @return Result
     */
    Result isFollow(Long followUserId);

    /**
     * 共同关注
     * @param id id:目标用户id
     * @return List<UserDTO>: 两人共同关注的人
     */
    Result followCommons(Long id);
}
