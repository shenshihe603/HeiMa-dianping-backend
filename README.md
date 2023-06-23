# HeiMa-dianping-backend

## 项目介绍
基于 Spring Boot + Redis 的店铺点评 APP，实现了找店铺 => 写点评 => 看热评 => 点赞关注 => 关注 Feed 流的完整业务流程。
在线店铺点评 APP（H5 网页），有点类似美团点评。
项目整体比较精简，项目中大量运用到了 Redis，项目的代码细节很多，能学到不少优化技巧

![image](https://github.com/shenshihe603/HeiMa-dianping-backend/assets/82700340/95e7b15f-e838-4480-998e-ebf2538134a8)

## 项目功能
查看点评（热评）、发布点评、点赞
关注、查询关注的人发的帖子、查看好友共同关注
分类浏览店铺、查看附近的店铺
个人信息查看和管理

![image](https://github.com/shenshihe603/HeiMa-dianping-backend/assets/82700340/8b22bd66-58be-4e66-af40-230d1da7eda1)



## 技术栈
### 后端
#### Spring 相关：
●Spring Boot 2.x

●Spring MVC

#### 数据存储层：
●MySQL：存储数据

●MyBatis Plus：数据访问框架

#### Redis 相关：
●spring-data-redis：操作 Redis

●Lettuce：操作 Redis 的高级客户端

●Apache Commons Pool：用于实现 Redis 连接池

●Redisson：基于 Redis 的分布式数据网格

### 工具库：

●HuTool：工具库合集

●Lombok：注解式代码生成工具

### 前端

●原生 HTML、CSS、JS 三件套

●Vue 2

●Element UI 组件库

●axios 请求库

## 技术架构图
这个项目是单体项目，架构比较简单，下图依然是理想架构，实际上只用单台 Tomcat、MySQL、Redis 即可：

![image](https://github.com/shenshihe603/HeiMa-dianping-backend/assets/82700340/e3d9603a-d87a-4206-aec9-0e593a830d4c)





## 学习重点
本项目可以说是为了帮大家学习 Redis 而定制的，因此学习重点在后端 Redis 上。
这个项目几乎用到了 Redis 的所有主流特性，都值得重点学习，如官方提供的项目介绍图：
![image](https://github.com/shenshihe603/HeiMa-dianping-backend/assets/82700340/e96eac5e-282d-4cab-84e6-54bdbab6e85e)

●  **主要工作**：
- 为提高热点店铺的查询效率，使用Redis对高频访问店铺进行**缓存**，降低DB压力同时**提升60%的数据查询性能**。
- 为实现优惠券秒杀，使用**Redis + Lua脚本实现库存预检**，并通过**RabbitMQ队列**实现订单的异步创建，**解决了超卖问题**、实现一人一单。实现相比传统数据库，接口**响应时间从20ms减少到2ms**。
- 为方便其他业务后续使用缓存，使用**泛型＋函数式编程**实现了通用缓存访问静态方法，并解决了缓存雪崩、缓存穿透等问题。
- 在系统用户量不大的前提下，基于推模式实现关注Feed流，保证了新点评消息的及时可达，并减少用户访问的等待时间.
- 为实现短信登录，使用Redis实现**分布式Session**，解决集群间登录态同步问题;使用Hash代替String 来存储用户信息，节约了Json字符串额外占用的的内存并便于单字段的修改。
- 使用Redis的Geo + Hash数据结构分类存储附近商户，并使用Geo Search 命令实现高性能商户查询及按距离排序。
- 使用Redis Set数据结构实现用户关注、共同关注功能(交集),使用Redis BitMap实现用户连续签到统计功能，使用Redis List数据结构存储用户点赞信息，并基于ZSet实现TopN点赞排行。

