#!/usr/bin/env bash
# 서버에서 실행하는 배포 스크립트. GitHub main의 웹을 빌드하고, 개발 PC가 올려 둔 백엔드 jar로 바꾼 뒤 재시작한다.
#   개발 PC: ./gradlew bootJar 후 scp build/libs/backend-0.0.1-SNAPSHOT.jar ubuntu@서버:/tmp/app.jar
#   서버:    sudo bash /opt/guard/src/deploy/aws/release.sh
set -euo pipefail

cd /opt/guard/src
sudo -u guard git fetch --quiet origin main
sudo -u guard git reset --hard --quiet origin/main
# 저장소 주인이 guard라 root로 git을 부르면 "dubious ownership"으로 거부된다.
echo "소스: $(sudo -u guard git log --oneline -1)"

if [ -f /tmp/app.jar ]; then
  install -o guard -g guard -m 640 /tmp/app.jar /opt/guard/backend/app.jar
  rm /tmp/app.jar
  echo "백엔드 jar 교체"
fi

# NEXT_PUBLIC_ 값과 APP_ENV는 빌드할 때 화면 코드에 들어가므로 실행용 환경변수를 빌드에도 그대로 쓴다.
cd /opt/guard/src/frontend-web
sudo -u guard bash -c 'set -a; . /etc/guard/web.env; set +a; npm ci --no-audit --no-fund && npx next build'

systemctl restart guard-backend
systemctl restart guard-web
echo "재시작 완료"
