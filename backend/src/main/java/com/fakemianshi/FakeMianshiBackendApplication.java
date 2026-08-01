package com.fakemianshi;

import com.fakemianshi.util.StartupInitializer;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.fakemianshi.repository")
public class FakeMianshiBackendApplication {

	public static void main(String[] args) {
		// 数据库/上传目录不随 git 仓库分发，启动前先确保存在
		StartupInitializer.ensureRuntimeDirectories();
		SpringApplication.run(FakeMianshiBackendApplication.class, args);
	}

}
