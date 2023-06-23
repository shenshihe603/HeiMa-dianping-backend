package com.hmdp.dto;

import lombok.Data;

@Data//因为@Data注解中包含了get，set和toString，所以我们直接在实体类中是@Data注解就可以免了再去手动创建这步骤了。
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
}
