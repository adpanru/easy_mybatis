package com.adru.easy_mybatis.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/aa")
public class testController {

    @GetMapping("/test")
    public String aa(){
        return "success";
    }
}
