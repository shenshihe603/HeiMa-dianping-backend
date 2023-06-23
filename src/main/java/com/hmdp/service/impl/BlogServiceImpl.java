package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.cron.timingwheel.SystemTimer;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import jodd.system.SystemUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private IFollowService followService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(
            blog->{
                this.queryBlogUser(blog);
                this.isBlogLiked(blog);
            }
        );
        return Result.ok(records);
    }
    //封装方法 查询用户
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());//blog中存入用户昵称
        blog.setIcon(user.getIcon());//blog中存入用户头像
//        blog.setIsLike(blog.getIsLike() == true ? false : true);
    }

    /**
     * 达人探店-查看探店笔记
     * @param id blog的id
     * @return 返回blog
     */
    @Override
    public Result queryBlogById(Long id) {
        //1.查询blog
        Blog blog = getById(id);//BlogService的方法
        if (blog == null) {
            return Result.fail("笔记不存在!");
        }
        //2.查询blog有关的用户
        queryBlogUser(blog);
        //3.查询blog是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    /**
     * 判断笔记是否被点赞
     * @param blog blog的id
     */
    private void isBlogLiked(Blog blog) {
        // 1.获取登录用户，有个问题，访问首页时就会访问这个接口，而用户并未登录，则getId会出错
//        Long userId = UserHolder.getUser().getId();
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;//用户未登录
        }
        // 2.判断当前登录用户是否已经点赞
        Long userId = user.getId();
        String key = BLOG_LIKED_KEY + blog.getId();
        //        Boolean isExeist = stringRedisTemplate.opsForSet().isMember(key, userId.toString());//set中存放userId，存在则代表文字已被改用户点赞
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());//score存在则代表文章已被该用户点赞
//        blog.setIsLike(BooleanUtil.isTrue(isExeist));
        blog.setIsLike(score != null);
    }

    /**达人探店-点赞功能
     * //        需求：
     * //                * * 同一个用户只能点赞一次，再次点击则取消点赞
     * //                * * 如果当前用户已经点赞，则点赞按钮高亮显示（前端已实现，判断字段Blog类的isLike属性）
     * @param id blog的id
     * @return 返回blog
     */
    @Override
    public Result likeBlog(Long id) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.判断当前登录用户是否已经点赞
        String key = BLOG_LIKED_KEY + id;
//        Boolean isExeist = stringRedisTemplate.opsForSet().isMember(key, userId.toString());//set中存放userId，存在则代表文字已被改用户点赞
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());//score存在则代表文章已被该用户点赞
//3.如果未点赞，可以点赞
        if (score == null) {

            //3.1 数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {

                //3.2 保存用户到Redis的set集合 zadd key value score
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
//            queryBlogUser(blog);//修改blog中的isLike变量
//            return Result.ok(blog);
        } else {

            //4.如果已点赞，取消点赞
            //4.1 数据库点赞数-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {

                //4.2 把用户从Redis的set集合移除
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
//            queryBlogUser(blog);
            }

        }
//        isBlogLiked(id);
        return Result.ok();//不用传入blog

    }

    /**
     * 笔记的点赞排行榜
     * @param id 笔记blog的id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        //1.查询笔记id前五的userId  zrange key 0 4
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5Ids = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5Ids == null || top5Ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //2.根据查询进行解析
        List<Long> ids = top5Ids.stream().map(Long::valueOf).collect(Collectors.toList());
        String idstr = StrUtil.join(",", ids);
        //3.查数据库拿到用户数据
        List<UserDTO> userDTOS = userService
                .query()
                .in("id", ids)//.orderBy("id")
                .last("order by field(id," + idstr + ")").list()
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    /**
     * 核心的意思：就是我们在保存完探店笔记后，获得到当前笔记的粉丝，然后把数据推送到粉丝的redis中去。
     * @param blog
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 2.保存探店笔记
        boolean isSuccess = save(blog);
        if(!isSuccess){
            return Result.fail("新增笔记失败!");
        }
        // 3.查询笔记作者的所有粉丝 select * from tb_follow where follow_user_id = ?
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        // 4.推送笔记id给所有粉丝
        for (Follow follow : follows) {
            // 4.1.获取粉丝id
            Long userId = follow.getUserId();
            // 4.2.推送
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 5.返回id
        return Result.ok(blog.getId());
    }
}
