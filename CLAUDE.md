# 작업 규칙 (Claude가 이 저장소를 고칠 때 매번 지킬 것)

사용자는 코딩을 하지 않는 메인관리자예요. 결과만 받으므로, **고치기 전·후 점검을 Claude가 스스로** 해야 해요.
팀원 개인정보(이름·이메일·근무 기록)는 공개 저장소·로그·주석에 남기지 않아요. 진단은 개수·ID만.

## 구조
- 웹앱 원본: `src/shift-board.html`(화면·근무 계산) + `src/app-layer.js`(Firebase) + `src/app.css` → `python3 build.py` → `app/index.html`
  - build.py는 `render();\n\n/* ---------- 공유 저장소 연결` 부터 끝까지 잘라 app-layer로 바꿔요. **새 코드는 그 앞**(예: `/* A4 한 장에 맞추기` 앞)에.
  - CSS는 두 번째 `<style>` 블록에. 첫 번째(감싸는 스타일)는 빌드 때 사라져요.
- 사용 설명서: `app/guide.html` (기능을 바꾸면 **설명서도 같이** 고치기)
- 서버: `functions/index.js` (asia-northeast3), 규칙: `firestore.rules`
- 갤럭시 앱(TWA): `android/` — `android/**`를 올리면 CI가 버전 올려 APK 릴리스. 웹에서 앱으로는 `intent://…#Intent;scheme=baejeonshift;package=kr.baejeon.shift;end`
- 테스트: `python3 test/all.py <폴더>` (가짜 Firebase로 화면 흐름 점검). `BROWSER=webkit`이면 아이폰 사파리 엔진.

## 고칠 때마다 확인할 것 (환경별)
| 환경 | 특히 조심 |
|---|---|
| 아이폰 사파리 / 홈 화면 앱 | 다른 주소 간 저장소 차단(로그인 authDomain은 앱과 같은 web.app), 팝업 막힘, 공유(navigator.share)는 **직접 누른 순간**에만, 큰 그림(한 변 4096px 넘으면 실패) |
| 갤럭시 앱(TWA) | 크롬 공유창 깜빡임 → 공유는 앱(ShareActivity)이 직접. 새 기능이 앱 쪽에 필요하면 **APK 업데이트 안내** 꼭 하기 |
| 카톡 안 브라우저 | 구글 로그인 막힘 → 바깥 브라우저로 넘김 |
| 가족 링크(로그인 없음) | 쓰기 불가, 메모·일정은 본인/가족만 |
| PC | 다운로드는 일반 파일 저장 |
| 갤럭시 앱 화면(네이티브) | 삼성 One UI 테마는 기본 글자색이 달라 **Spinner 등 글자가 안 보일 수 있음** → 글자색·크기 직접 지정. Robolectric 미리보기와 실제 폰이 다를 수 있음. 폴드(펼침) 넓은 화면도 고려 |

- 화면을 바꾸면 `python3 test/t_contrast.py <폴더>`로 글자 대비(시인성) 점검 (밝게·어둡게). 같은 클래스 이름 충돌 주의(예: `.sm`이 `.btn.sm`을 덮었던 일).
- 새 화면/버튼은 **간단히 보기(과장 버전)와 자세히 보기 양쪽**에서 확인.
- 인쇄(A4 한 장)·카톡 이미지(휴대폰 폭, 큰 글씨)·화면 세 가지 모양을 따로 확인.
- 근무 계산을 바꾸면 `functions/engine.js`(위젯·서버용)도 같은지 확인.
- 규칙(firestore.rules)에 새 필드·컬렉션을 쓰면 허용했는지 확인.
- 외부 설정이 필요한 변경(구글 클라우드·Firebase 콘솔)은 **먼저 설정 → 확인(tools/diag-auth.sh 등) → 그다음 코드 배포** 순서.
- 사용자에게 보고할 땐: 무엇이 바뀌었는지, 사용자가 해야 할 일(앱 업데이트·새로고침), 실기기에서 확인 못 한 부분을 솔직하게.

## 자동 안전장치
- `Firebase 배포` 워크플로: 배포 전에 test/all.py를 크롬+사파리 엔진으로 돌려 실패하면 배포 중단, src와 app이 다르면 중단.
- `매일 점검`(health.yml): 매일 08:47 KST + 배포 직후, 앱·설명서·APK 링크·서버 함수·구글 로그인 리디렉션 주소 점검. 실패 시 GitHub 메일.
- 실패 원인 보기: Actions 로그는 내려받기가 막혀 있으니 `gh api repos/junsury/baejeon-shift/check-runs/<job id>/annotations`로 `::error::` 줄을 읽어요.
