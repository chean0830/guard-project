package com.projectguard.backend;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

@SpringBootApplication
public class BackendApplication {

	/**
	 * application.properties가 ${KEY:} 형태로 참조하는 민감한 설정 키. 개발 PC의 OS 환경변수에
	 * 이 이름과 같은 값이 이미 있으면(다른 프로젝트용으로 설정해둔 것이라도) Spring이 그 값을
	 * 그대로 읽어버린다 — 실제로 MAIL_HOST/MAIL_USERNAME/MAIL_PASSWORD가 무관한 다른 프로젝트
	 * ("Festlog")의 실제 Gmail 계정 값으로 OS 환경변수에 남아있어서, 상담 알림 메일 기능이
	 * 그 계정으로 진짜 메일을 보내려 시도하는 것을 라이브 테스트 중 발견했다(수신자가 전부
	 * @example.com이라 실제 전달은 되지 않았지만, 그 계정으로 실제 SMTP 인증 시도가 발생했음).
	 * .env에 값이 없으면 System 프로퍼티를 빈 문자열로 명시해 OS 환경변수보다 항상 우선하게 만든다
	 * (System 프로퍼티가 Spring Environment에서 가장 우선순위가 높은 소스이기 때문).
	 *
	 * 주의: dotenv-java의 Dotenv.entries()는 .env 파일에 없는 키를 System.getenv()로 자동
	 * 대체해 돌려준다(라이브 테스트로 직접 확인) — 그래서 "이 키가 .env 파일에 실제로 있는지"는
	 * entries()로 판단할 수 없고, 파일을 직접 읽어서 확인해야 한다.
	 */
	private static final Set<String> SENSITIVE_KEYS_REQUIRING_EXPLICIT_ENV = Set.of(
			"MAIL_HOST", "MAIL_PORT", "MAIL_USERNAME", "MAIL_PASSWORD"
	);

	public static void main(String[] args) {
		// me.paulschwarz:spring-dotenv은 Spring Boot 4.1.1에서 SpringApplicationRunListener가
		// 전혀 호출되지 않아 .env 값이 로드되지 않는 문제가 있었다 (직접 확인, docs/결정사항.md 참고).
		// dotenv-java로 직접 읽어 JVM 시스템 프로퍼티로 등록한다 (시스템 프로퍼티는 Spring Environment에서
		// 가장 우선순위가 높은 프로퍼티 소스라 @Value/${...} 어디서든 안정적으로 조회된다).
		Dotenv.configure().ignoreIfMissing().load().entries()
				.forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

		Set<String> definedInEnvFile = readKeysDefinedInEnvFile();
		for (String key : SENSITIVE_KEYS_REQUIRING_EXPLICIT_ENV) {
			if (!definedInEnvFile.contains(key)) {
				System.setProperty(key, "");
			}
		}

		SpringApplication.run(BackendApplication.class, args);
	}

	private static Set<String> readKeysDefinedInEnvFile() {
		Path envPath = Path.of(".env");
		if (!Files.isRegularFile(envPath)) {
			return Set.of();
		}
		try {
			return Files.readAllLines(envPath).stream()
					.map(String::trim)
					.filter(line -> !line.isEmpty() && !line.startsWith("#") && line.contains("="))
					.map(line -> line.split("=", 2)[0].trim())
					.collect(java.util.stream.Collectors.toSet());
		} catch (IOException e) {
			return Set.of();
		}
	}

}
