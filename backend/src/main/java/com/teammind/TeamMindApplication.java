package com.teammind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TeamMind Backend Application
 * 
 * AI Agent 协作平台后端服务
 * 支持智能体自主进化的多 Agent 协作系统
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class TeamMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeamMindApplication.class, args);
    }
}
