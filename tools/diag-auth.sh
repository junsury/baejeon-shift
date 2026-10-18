#!/bin/bash
# 구글 로그인 리디렉션 주소(web.app) 허용 여부 확인 — 공개돼도 되는 정보만 출력
TOKEN=$(gcloud auth print-access-token)
CID=$(curl -s -H "Authorization: Bearer $TOKEN" -H "x-goog-user-project: baejeon-shift" "https://identitytoolkit.googleapis.com/admin/v2/projects/baejeon-shift/defaultSupportedIdpConfigs/google.com" | python3 -c "import sys,json;print(json.load(sys.stdin).get('clientId',''))")
echo "::notice::google idp client found: $([ -n "$CID" ] && echo yes || echo no)"
DOMS=$(curl -s -H "Authorization: Bearer $TOKEN" -H "x-goog-user-project: baejeon-shift" "https://identitytoolkit.googleapis.com/admin/v2/projects/baejeon-shift/config" | python3 -c "import sys,json;print(','.join(json.load(sys.stdin).get('authorizedDomains',[])))")
echo "::notice::authorized domains: $DOMS"
for H in baejeon-shift.firebaseapp.com baejeon-shift.web.app; do
  R=$(curl -s -L "https://accounts.google.com/o/oauth2/v2/auth?client_id=$CID&redirect_uri=https%3A%2F%2F$H%2F__%2Fauth%2Fhandler&response_type=code&scope=openid" | grep -o "redirect_uri_mismatch" | head -1)
  echo "::notice::$H handler: ${R:-ok}"
done
