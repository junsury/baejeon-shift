// Firebase 설정값 — Firebase 콘솔 → 프로젝트 설정 → 내 앱(웹)에서 복사한 값으로 바꾸세요.
// 이 값들은 공개돼도 괜찮아요(보안은 firestore.rules가 지켜요).
window.FIREBASE_CONFIG = {
  apiKey: "AIzaSyD_oo24MEDYagUECrYPbTPbzrBFQKYEhRY",
  // 로그인도 앱과 같은 주소에서 처리 (아이폰 사파리가 다른 주소 간 로그인 정보를 막아서)
  authDomain: location.hostname === "baejeon-shift.web.app" ? "baejeon-shift.web.app" : "baejeon-shift.firebaseapp.com",
  projectId: "baejeon-shift",
  storageBucket: "baejeon-shift.firebasestorage.app",
  messagingSenderId: "222465525681",
  appId: "1:222465525681:web:ce3721aad36f43aeb01f90"
};
// 웹 푸시 키 (Firebase 콘솔 → 프로젝트 설정 → 클라우드 메시징 → 웹 푸시 인증서). 비어 있으면 알림 기능이 숨겨져요.
window.VAPID_KEY = "BMM8wqffz9iScA9EYBNI4sthYSU-2UjPvBhwJHmwUahKgJPEHRZ0-FYTdHP9e4C2rHGC3qylob9KW5NxW1gRK3E";
// 팀 구분 이름 (바꾸지 않아도 돼요)
window.TEAM_ID = "gyeonggi";
