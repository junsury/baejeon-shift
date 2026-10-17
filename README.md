# 배전센터 근무표 (앱)

4조2교대 근무표 · 휴가 · 대근 · 근무변경 · 대근총량 · 근무요약을 팀원과 가족이 함께 보는 웹앱(PWA)입니다.
휴대폰에서 "홈 화면에 추가"하면 앱처럼 아이콘으로 열려요.

- 로그인: 구글 계정 (카카오 로그인은 2단계)
- 저장소: Firebase Firestore (무료 요금제로 충분)
- 배포: GitHub Pages (main 브랜치에 올리면 자동)

## 역할

| 역할 | 할 수 있는 일 |
|---|---|
| 메인관리자 | 모든 관리 + 서브관리자 지정·해제 + 메인관리자 넘기기 |
| 서브관리자 | 근태 등록, 조 편성, 대근총량, 설정, 팀원 연결 해제·내보내기 |
| 팀원 | 내 근무·팀 근무표·근태 신청/승인·대근총량·근무요약 보기, 가족 초대 |
| 가족 | 초대한 사람의 내 근무(일·주·월)와 메모만 보기 |

## 처음 설정 (한 번만)

### 1. Firebase
1. https://console.firebase.google.com → 프로젝트 추가 (예: `baejeon-shift`), 애널리틱스는 꺼도 됨
2. 빌드 → **Authentication** → 시작하기 → 로그인 방법 → **Google** 사용 설정
3. 빌드 → **Firestore Database** → 데이터베이스 만들기 → 위치 `asia-northeast3 (서울)` → **프로덕션 모드**
4. Firestore → **규칙** 탭 → 이 저장소의 `firestore.rules` 내용을 통째로 붙여 넣고 **게시**
5. 프로젝트 설정(톱니바퀴) → 내 앱 → 웹(`</>`) 앱 추가 → 나오는 `firebaseConfig` 값을 `app/config.js`에 넣기
6. Authentication → 설정 → **승인된 도메인**에 `아이디.github.io` 추가

### 2. GitHub Pages
1. 저장소 → Settings → Pages → Source: **GitHub Actions**
2. main 브랜치에 올리면 Actions 탭에서 "배포"가 돌고, 끝나면 `https://아이디.github.io/저장소이름/` 에서 열림

### 3. 팀 만들기
1. 근무표 아티팩트 → 관리 → 설정 → **데이터 내보내기**로 `.json` 파일 받기
2. 앱 주소로 들어가서 구글 로그인 → "팀 만들기"에서 그 파일을 넣고 만들기 → 이 계정이 메인관리자
3. 본인 이름 고르기
4. 관리 → 설정 → **팀 초대 → 보내기**로 단톡방에 초대 링크 공유

## 팀원 · 가족 가입
- 팀원: 앱 주소 열기 → 구글 로그인 → 본인 이름 고르기 → 관리자(메인·서브)가 관리 → 설정 → 가입 승인에서 승인
- 가족: 팀원이 오른쪽 위 👤 → 가족 링크 만들기 → 보내기. 가족은 링크만 누르면 로그인 없이 보기 (팀원이 끊으면 바로 못 봄)
- 카카오톡에서 링크를 열면 바깥 브라우저(크롬·사파리)로 자동으로 넘어가요. (카톡 안에서는 구글 로그인이 막혀 있어요)

## 파일 구성
- `app/` — 실제 배포되는 앱 (index.html, config.js, sw.js, manifest, icons)
- `firestore.rules` — 보안 규칙 (Firebase 콘솔에 붙여 넣기)
- `src/app-layer.js`, `src/app.css` — 로그인·권한·Firestore 연결 부분
- `build.py` — 근무표 아티팩트(`shift-board.html`)를 앱 페이지로 변환: `python3 build.py` (src/shift-board.html 사용)
- `test/` — 가짜 Firebase로 가입·권한 흐름을 확인하는 테스트

## 다음 단계
- 카카오 로그인 (Firebase Blaze 요금제 + Cloud Functions 필요)
- 요청·승인 푸시 알림 (FCM)
- 앱스토어·플레이스토어 등록 (Capacitor로 감싸기)
