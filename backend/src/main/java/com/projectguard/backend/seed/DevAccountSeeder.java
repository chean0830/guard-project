package com.projectguard.backend.seed;

import com.projectguard.backend.auth.User;
import com.projectguard.backend.auth.UserRepository;
import com.projectguard.backend.lawyer.Lawyer;
import com.projectguard.backend.lawyer.LawyerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 개발용 고정 테스트 계정(일반 회원 1명, 승인된 변호사 1명)을 서버 시작 시 만들어 둔다.
 * 개발 DB가 인메모리 H2라 재시작할 때마다 계정이 사라지기 때문. 계정 정보는 .env의
 * SEED_* 값으로만 받으며(저장소에 비밀번호를 남기지 않음), 값이 비어 있으면 아무것도 하지 않는다 —
 * 배포 환경에서는 SEED_* 를 설정하지 않으면 된다. (관리자 계정은 ADMIN_EMAIL/ADMIN_PASSWORD로 따로 동작)
 */
@Component
public class DevAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAccountSeeder.class);

    private final UserRepository userRepository;
    private final LawyerRepository lawyerRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final String userEmail;
    private final String userPassword;
    private final String lawyerEmail;
    private final String lawyerPassword;

    public DevAccountSeeder(
            UserRepository userRepository,
            LawyerRepository lawyerRepository,
            @Value("${SEED_USER_EMAIL:}") String userEmail,
            @Value("${SEED_USER_PASSWORD:}") String userPassword,
            @Value("${SEED_LAWYER_EMAIL:}") String lawyerEmail,
            @Value("${SEED_LAWYER_PASSWORD:}") String lawyerPassword
    ) {
        this.userRepository = userRepository;
        this.lawyerRepository = lawyerRepository;
        this.userEmail = userEmail;
        this.userPassword = userPassword;
        this.lawyerEmail = lawyerEmail;
        this.lawyerPassword = lawyerPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!userEmail.isBlank() && !userPassword.isBlank() && userRepository.findByEmail(userEmail).isEmpty()) {
            userRepository.save(new User(userEmail, passwordEncoder.encode(userPassword)));
            log.info("개발용 일반 회원 계정 생성: {}", userEmail);
        }

        if (!lawyerEmail.isBlank() && !lawyerPassword.isBlank() && lawyerRepository.findByEmail(lawyerEmail).isEmpty()) {
            Lawyer lawyer = new Lawyer(lawyerEmail, passwordEncoder.encode(lawyerPassword), "테스트 변호사", null, "SEED-0000");
            lawyer.approve();
            lawyer.updateProfile("테스트 변호사", "법무법인 가드", "전세사기, 임대차분쟁, 부동산",
                    "전세 계약 전 검토부터 보증금 반환 소송까지 함께합니다.");
            lawyer.updateStrengths(
                    "전세보증금 반환 사건 전문",
                    8,
                    "첫 상담 무료 · 착수금 100만원부터 (사건 난이도에 따라 협의)",
                    "전세보증금 반환 소송 다수 수행\n임대인 파산 사건 배당 참여\n대한변협 등록 부동산 전문변호사"
            );
            // 개발용 주소가 실제 남의 메일함일 수 있어, 새 문의 알림 메일은 기본으로 꺼 둔다.
            lawyer.setEmailNotificationsEnabled(false);
            lawyerRepository.save(lawyer);
            log.info("개발용 변호사 계정 생성(승인 완료): {}", lawyerEmail);
        }

        if (!lawyerPassword.isBlank()) {
            seedDemoLawyers();
        }
    }

    /** 변호사 목록 화면을 채우기 위한 가상 변호사. 이름·소속·실적은 모두 지어낸 것이다. */
    private record DemoLawyer(
            String name, String lawFirm, String specialties, String introduction,
            String headline, int careerYears, String feeInfo, String achievements
    ) {
    }

    private static final List<DemoLawyer> DEMO_LAWYERS = List.of(
            new DemoLawyer("김강석", "법무법인 한결", "부동산, 전세사기",
                    "전세사기 피해자분들의 보증금을 한 푼이라도 더 돌려받는 데 집중합니다.",
                    "부동산·전세사기 전문 변호사", 12,
                    "첫 상담 무료 · 착수금 150만원부터",
                    "전세사기 피해자 집단소송 대리\n보증금 반환 소송 40건 이상 수행\n주택임대차분쟁조정위원회 조정위원 경력"),
            new DemoLawyer("김지혜", "지혜 법률사무소", "사기, 형사고소",
                    "임대인 사기 사건의 형사고소부터 민사상 손해배상까지 한 번에 진행합니다.",
                    "사기 사건 형사고소부터 민사 회수까지", 9,
                    "상담 30분 3만원 · 고소 대리 200만원부터",
                    "전세사기 임대인 형사고소 대리 다수\n수사 단계 피해자 진술 동행\n사기 피해금 가압류·회수 경험"),
            new DemoLawyer("이준호", "법무법인 바른길", "임대차분쟁, 명도소송",
                    "소송까지 가기 전에 합의로 빠르게 끝낼 수 있는 방법부터 찾아드립니다.",
                    "임대차 분쟁 빠른 합의 지향", 6,
                    "착수금 80만원 · 성공보수 별도 협의",
                    "임대차 분쟁 조정 성립 다수\n명도소송 대리\n내용증명 작성·발송 대행"),
            new DemoLawyer("박서연", "서연 법률사무소", "경매, 배당",
                    "집이 경매로 넘어갔을 때 임차인이 받을 수 있는 배당을 꼼꼼히 챙깁니다.",
                    "부동산 경매·배당 절차 전문", 11,
                    "첫 상담 무료 · 배당이의 소송 120만원부터",
                    "임차인 배당요구 대리 다수\n배당이의 소송 수행\n부동산 경매 실무 강의 경력"),
            new DemoLawyer("최민재", "법무법인 온유", "부동산, 등기",
                    "계약서에 도장 찍기 전, 등기부등본과 계약서를 함께 검토해드립니다.",
                    "계약 전 등기부 권리분석 15년", 15,
                    "계약 전 권리분석 검토 10만원",
                    "전세 계약 전 권리분석 상담 다수\n근저당·가압류 말소 절차 대리\n부동산 등기 분쟁 소송 수행"),
            new DemoLawyer("정하은", "하은 법률사무소", "전세보증보험, 보증금반환",
                    "보증보험 이행청구가 거절됐거나 서류가 막막할 때 도와드립니다.",
                    "전세보증보험 이행청구 도움", 5,
                    "상담 무료 · 착수금 70만원부터",
                    "보증보험 이행청구 절차 지원\n보증금 반환 지급명령 신청 대리\n임차권등기명령 신청 대리"),
            new DemoLawyer("강태윤", "법무법인 정도", "사기, 민사집행",
                    "판결문만 받고 끝나지 않도록, 실제로 돈을 돌려받는 강제집행까지 책임집니다.",
                    "판결 후 강제집행까지 책임", 10,
                    "착수금 120만원 · 회수금액 기준 성공보수",
                    "채권 압류·추심 절차 대리 다수\n재산명시·재산조회 신청\n임대인 은닉재산 추적 경험"),
            new DemoLawyer("윤소희", "소희 법률사무소", "임대차, 소액사건",
                    "보증금 규모가 작아도 부담 없이 맡기실 수 있도록 수임료를 낮췄습니다.",
                    "소액 보증금 사건 합리적 수임료", 4,
                    "소액사건 착수금 50만원 · 상담 무료",
                    "소액 보증금 반환 소송 수행\n지급명령·소액심판 절차 대리\n청년 임차인 법률상담 봉사"),
            new DemoLawyer("한도현", "법무법인 새빛", "부동산, 개인회생",
                    "전세사기로 빚이 생긴 피해자분들의 회생·파산 절차도 함께 상담합니다.",
                    "전세사기 피해자 회생·파산 상담", 7,
                    "첫 상담 무료 · 회생 신청 대리 180만원부터",
                    "전세사기 피해자 개인회생 신청 대리\n피해자 지원 특별법 절차 안내\n부동산 관련 파산 사건 수행")
    );

    private void seedDemoLawyers() {
        for (int i = 0; i < DEMO_LAWYERS.size(); i++) {
            DemoLawyer demo = DEMO_LAWYERS.get(i);
            // example.com은 실제 메일이 절대 가지 않는 예약 도메인이다.
            String email = String.format("demo-lawyer-%02d@example.com", i + 1);
            if (lawyerRepository.findByEmail(email).isPresent()) {
                continue;
            }
            Lawyer lawyer = new Lawyer(email, passwordEncoder.encode(lawyerPassword), demo.name(), demo.lawFirm(),
                    String.format("DEMO-%04d", i + 1));
            lawyer.approve();
            lawyer.updateProfile(demo.name(), demo.lawFirm(), demo.specialties(), demo.introduction());
            lawyer.updateStrengths(demo.headline(), demo.careerYears(), demo.feeInfo(), demo.achievements());
            lawyer.setEmailNotificationsEnabled(false);
            lawyerRepository.save(lawyer);
        }
        log.info("개발용 가상 변호사 {}명 준비 완료", DEMO_LAWYERS.size());
    }
}
