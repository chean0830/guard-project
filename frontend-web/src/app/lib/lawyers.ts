export type Lawyer = {
  id: string
  name: string
  firm: string
  specialties: string[]
  experience: string
  intro: string
  avatarColor: string
}

/**
 * 포트폴리오 데모용 가상 변호사 데이터. 실존 인물/법인이 아니며, 실제 상담을
 * 제공하지 않는다 (docs/결정사항.md 참고). 채팅 응답도 실시간 상담이 아니라
 * 미리 정해진 문구로만 답한다.
 */
export const LAWYERS: Lawyer[] = [
  {
    id: 'kim-doyoon',
    name: '김도윤 변호사',
    firm: '법률사무소 이음',
    specialties: ['전세사기', '임대차분쟁'],
    experience: '전세사기 피해자 소송 다수 승소',
    intro: '안녕하세요, 김도윤 변호사입니다. 전세사기·임대차 사건을 주로 맡고 있어요. 어떤 상황이신가요?',
    avatarColor: 'bg-orange-500',
  },
  {
    id: 'lee-seoyeon',
    name: '이서연 변호사',
    firm: '한빛 법률사무소',
    specialties: ['부동산사기', '계약분쟁'],
    experience: '부동산 계약 사기 사건 다수 승소',
    intro: '안녕하세요, 이서연 변호사입니다. 부동산 계약 관련 사기 사건을 많이 다뤄왔어요. 편하게 말씀해주세요.',
    avatarColor: 'bg-rose-500',
  },
  {
    id: 'park-jihoon',
    name: '박지훈 변호사',
    firm: '정도 법률사무소',
    specialties: ['등기·근저당분쟁', '경매대응'],
    experience: '근저당·경매 관련 분쟁 다수 대리',
    intro: '안녕하세요, 박지훈 변호사입니다. 등기부상 근저당·경매 관련 분쟁을 주로 맡고 있어요.',
    avatarColor: 'bg-blue-500',
  },
  {
    id: 'choi-yuna',
    name: '최유나 변호사',
    firm: '온새미 법률사무소',
    specialties: ['전세보증금반환', '임대차보호법'],
    experience: '전세보증금 반환소송 다수 승소',
    intro: '안녕하세요, 최유나 변호사입니다. 보증금을 돌려받지 못한 분들을 많이 도와드렸어요.',
    avatarColor: 'bg-emerald-500',
  },
  {
    id: 'jung-minjae',
    name: '정민재 변호사',
    firm: '든든 법률사무소',
    specialties: ['부동산형사(사기)', '전세사기'],
    experience: '부동산 사기 형사고소 다수 진행',
    intro: '안녕하세요, 정민재 변호사입니다. 부동산 사기는 형사 고소도 함께 검토해야 하는 경우가 많아요.',
    avatarColor: 'bg-indigo-500',
  },
  {
    id: 'han-soyul',
    name: '한소율 변호사',
    firm: '바로 법률사무소',
    specialties: ['임대차보호법', '소액임차인'],
    experience: '소액임차인 최우선변제 소송 다수 승소',
    intro: '안녕하세요, 한소율 변호사입니다. 임대차보호법 관련해서 궁금하신 점 편하게 물어보세요.',
    avatarColor: 'bg-purple-500',
  },
  {
    id: 'oh-taeyang',
    name: '오태양 변호사',
    firm: '신뢰 법률사무소',
    specialties: ['경매대응', '근저당분쟁'],
    experience: '경매 관련 임차인 대리 다수 진행',
    intro: '안녕하세요, 오태양 변호사입니다. 집이 경매로 넘어갈 위기라면 지금 상황부터 말씀해주세요.',
    avatarColor: 'bg-amber-500',
  },
  {
    id: 'yoon-chaeeun',
    name: '윤채은 변호사',
    firm: '곧은 법률사무소',
    specialties: ['부동산사기', '피해자대리'],
    experience: '부동산 사기 피해자 대리 다수 승소',
    intro: '안녕하세요, 윤채은 변호사입니다. 부동산 사기 피해자분들 대리를 많이 해왔어요.',
    avatarColor: 'bg-pink-500',
  },
  {
    id: 'kang-hyunwoo',
    name: '강현우 변호사',
    firm: '밝음 법률사무소',
    specialties: ['전세사기특별법', '전세사기'],
    experience: '전세사기 특별법 관련 대응 다수 진행',
    intro: '안녕하세요, 강현우 변호사입니다. 전세사기 특별법으로 도움받을 수 있는지도 함께 확인해드려요.',
    avatarColor: 'bg-teal-500',
  },
  {
    id: 'seo-jian',
    name: '서지안 변호사',
    firm: '참길 법률사무소',
    specialties: ['보증금반환', '임차권등기명령'],
    experience: '임차권등기명령·보증금반환 소송 다수 승소',
    intro: '안녕하세요, 서지안 변호사입니다. 이사를 가야 하는데 보증금을 못 받으셨다면 지금 상황이 중요해요.',
    avatarColor: 'bg-cyan-500',
  },
]

export function getLawyerById(id: string): Lawyer | undefined {
  return LAWYERS.find((lawyer) => lawyer.id === id)
}

export function pickRandomLawyerId(): string {
  const index = Math.floor(Math.random() * LAWYERS.length)
  return LAWYERS[index].id
}
