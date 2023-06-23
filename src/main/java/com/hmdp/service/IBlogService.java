package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Result queryHotBlog(Integer current);

    /**
     * 达人探店-查看探店笔记
     * @param id blog的id
     * @return 返回blog
     */
    Result queryBlogById(Long id);

    /**达人探店-点赞功能
     * //        需求：
     * //                * * 同一个用户只能点赞一次，再次点击则取消点赞
     * //                * * 如果当前用户已经点赞，则点赞按钮高亮显示（前端已实现，判断字段Blog类的isLike属性）
     * @param id blog的id
     * @return 返回blog
     */
    Result likeBlog(Long id);

    /**
     * 笔记的点赞排行榜
     * @param id 笔记blog的id
     * @return
     */
    Result queryBlogLikes(Long id);

    /**
     * 核心的意思：就是我们在保存完探店笔记后，获得到当前笔记的粉丝，然后把数据推送到粉丝的redis中去。
     * @param blog
     * @return
     */
    Result saveBlog(Blog blog);
}
