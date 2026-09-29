package com.projectguard.backend;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		// me.paulschwarz:spring-dotenv은 Spring Boot 4.1.1에서 SpringApplicationRunListener가
		// 전혀 호출되지 않아 .env 값이 로드되지 않는 문제가 있었다 (직접 확인, docs/결정사항.md 참고).
		// dotenv-java로 직접 읽어 JVM 시스템 프로퍼티로 등록한다 (시스템 프로퍼티는 Spring Environment에서
		// 가장 우선순위가 높은 프로퍼티 소스라 @Value/${...} 어디서든 안정적으로 조회된다).
		Dotenv.configure().ignoreIfMissing().load().entries()
				.forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
		SpringApplication.run(BackendApplication.class, args);
	}

}
