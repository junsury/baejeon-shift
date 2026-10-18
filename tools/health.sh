#!/bin/bash
# 매일 점검: 앱 주소·설명서·갤럭시 앱 다운로드·서버 함수·구글 로그인 주소. 하나라도 이상하면 실패(관리자 메일로 알림)
fail=0
chk(){ # 이름 URL 기대코드(정규식)
  c=$(curl -s -o /dev/null -w "%{http_code}" -L --max-time 30 "$2"); if [[ "$c" =~ ^($3)$ ]]; then echo "::notice::OK $1 ($c)"; else echo "::error::$1 응답 $c"; fail=1; fi; }
chk "앱 주소" https://baejeon-shift.web.app/ 200
chk "앱 버전 파일" https://baejeon-shift.web.app/version.json 200
chk "사용 설명서" https://baejeon-shift.web.app/guide.html 200
chk "로그인 처리 페이지" https://baejeon-shift.web.app/__/auth/handler 200
chk "예전 주소(넘겨주기)" https://junsury.github.io/baejeon-shift/ 200
chk "갤럭시 앱 다운로드" https://github.com/junsury/baejeon-shift/releases/download/android/baejeon-shift.apk 200
F=https://asia-northeast3-baejeon-shift.cloudfunctions.net
chk "위젯 서버" "$F/widgetData?t=xxxxxxxxxxxxxxxx" 404
chk "공유 서버" "$F/shareFile?t=gyeonggi&id=AAAAAAAAAAAAAAAAAAAAAAAAAAAA" 404
# 구글 로그인 리디렉션 주소 (아이폰 사파리는 web.app 쪽이 꼭 허용돼 있어야 해요)
if [ -n "$GCLOUD_OK" ]; then
  TOKEN=$(gcloud auth print-access-token)
  CID=$(curl -s -H "Authorization: Bearer $TOKEN" -H "x-goog-user-project: baejeon-shift" "https://identitytoolkit.googleapis.com/admin/v2/projects/baejeon-shift/defaultSupportedIdpConfigs/google.com" | python3 -c "import sys,json;print(json.load(sys.stdin).get('clientId',''))")
  for H in baejeon-shift.web.app baejeon-shift.firebaseapp.com; do
    R=$(curl -s -L "https://accounts.google.com/o/oauth2/v2/auth?client_id=$CID&redirect_uri=https%3A%2F%2F$H%2F__%2Fauth%2Fhandler&response_type=code&scope=openid" | grep -o "redirect_uri_mismatch\|invalid_client" | head -1)
    if [ -n "$R" ]; then echo "::error::구글 로그인 주소 $H: $R"; fail=1; else echo "::notice::OK 구글 로그인 주소 $H"; fi
  done
  # 앱이 쓰는 로그인 주소가 허용 목록에 있는지
  AD=$(curl -s https://baejeon-shift.web.app/config.js | grep -o 'baejeon-shift\.web\.app"' | head -1)
  [ -n "$AD" ] && echo "::notice::OK 앱 로그인 주소 web.app" || { echo "::warning::config.js 로그인 주소 확인 필요"; }
fi
exit $fail
