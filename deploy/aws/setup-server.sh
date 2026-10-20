#!/usr/bin/env bash
# 새 EC2 서버(Ubuntu 24.04)에 한 번만 실행한다. 웹·백엔드·MySQL·Caddy를 한 대에 올린다 (docs/결정사항.md 58번).
#   sudo DB_PASSWORD=... bash setup-server.sh <웹 주소> <API 주소>
#   예) sudo DB_PASSWORD=... bash setup-server.sh 54-116-91-186.sslip.io api.54-116-91-186.sslip.io
# 실행 후 /etc/guard/backend.env, /etc/guard/web.env를 채우고 release.sh로 배포한다.
set -euo pipefail

WEB_HOST="$1"
API_HOST="$2"
: "${DB_PASSWORD:?DB_PASSWORD가 필요합니다}"

timedatectl set-timezone Asia/Seoul

# 메모리 2GB에 웹·백엔드·MySQL과 Next.js 빌드까지 돌리므로 스왑 2GB를 둔다.
if [ ! -f /swapfile ]; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

apt-get update
apt-get install -y ca-certificates curl gnupg git debian-keyring debian-archive-keyring apt-transport-https

# Java 25 (Amazon Corretto)
curl -fsSL https://apt.corretto.aws/corretto.key | gpg --dearmor --yes -o /usr/share/keyrings/corretto-keyring.gpg
echo "deb [signed-by=/usr/share/keyrings/corretto-keyring.gpg] https://apt.corretto.aws stable main" > /etc/apt/sources.list.d/corretto.list

# Node.js 24
curl -fsSL https://deb.nodesource.com/setup_24.x | bash -

# Caddy (HTTPS 인증서 자동 발급·갱신)
curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/gpg.key | gpg --dearmor --yes -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt > /etc/apt/sources.list.d/caddy-stable.list

apt-get update
apt-get install -y java-25-amazon-corretto-jdk nodejs mysql-server caddy

# MySQL: 메모리를 아끼려고 성능 스키마를 끈다. 외부 접속은 받지 않는다(기본 127.0.0.1).
cat > /etc/mysql/mysql.conf.d/guard.cnf <<'EOF'
[mysqld]
performance_schema = OFF
innodb_buffer_pool_size = 128M
EOF
systemctl restart mysql
mysql <<SQL
CREATE DATABASE IF NOT EXISTS project_guard CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'project_guard'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
ALTER USER 'project_guard'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON project_guard.* TO 'project_guard'@'localhost';
FLUSH PRIVILEGES;
SQL

# 앱 전용 계정과 폴더. 소스는 GitHub에서 받고, 백엔드 jar는 개발 PC에서 올린다(release.sh).
id guard >/dev/null 2>&1 || useradd --system --create-home --home-dir /opt/guard --shell /usr/sbin/nologin guard
mkdir -p /opt/guard/backend /etc/guard
[ -d /opt/guard/src ] || git clone https://github.com/chean0830/guard-project.git /opt/guard/src
chown -R guard:guard /opt/guard
# 환경변수 파일은 root가 쓰고 앱 계정(guard)은 읽기만 한다. 파일은 640 root:guard로 만든다.
chown root:guard /etc/guard
chmod 750 /etc/guard

cat > /etc/systemd/system/guard-backend.service <<'EOF'
[Unit]
Description=Project Guard backend (Spring Boot)
After=network.target mysql.service
Requires=mysql.service

[Service]
User=guard
WorkingDirectory=/opt/guard/backend
EnvironmentFile=/etc/guard/backend.env
ExecStart=/usr/bin/java -Xms256m -Xmx512m -jar /opt/guard/backend/app.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

cat > /etc/systemd/system/guard-web.service <<'EOF'
[Unit]
Description=Project Guard web (Next.js)
After=network.target guard-backend.service

[Service]
User=guard
WorkingDirectory=/opt/guard/src/frontend-web
EnvironmentFile=/etc/guard/web.env
ExecStart=/usr/bin/npx next start -p 3000 -H 127.0.0.1
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

# 웹은 WEB_HOST, 백엔드(파일 업로드·WebSocket)는 API_HOST. 앱 포트(3000, 8080)는 밖으로 열지 않는다.
cat > /etc/caddy/Caddyfile <<EOF
${WEB_HOST} {
	encode gzip
	reverse_proxy 127.0.0.1:3000
}

${API_HOST} {
	reverse_proxy 127.0.0.1:8080
}
EOF

systemctl daemon-reload
systemctl enable guard-backend guard-web
systemctl reload caddy || systemctl restart caddy
echo "설치 완료: /etc/guard/backend.env, /etc/guard/web.env를 채운 뒤 release.sh를 실행하세요."
