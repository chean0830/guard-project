#!/usr/bin/env bash
# 일일 DB 백업 타이머 설치. 서버에서 한 번 실행: sudo bash /opt/guard/src/deploy/aws/install-db-backup.sh
set -euo pipefail

install -m 755 /opt/guard/src/deploy/aws/db-backup.sh /usr/local/bin/guard-db-backup

cat > /etc/systemd/system/guard-db-backup.service <<'EOF'
[Unit]
Description=Project Guard MySQL backup
After=mysql.service

[Service]
Type=oneshot
ExecStart=/usr/local/bin/guard-db-backup
EOF

cat > /etc/systemd/system/guard-db-backup.timer <<'EOF'
[Unit]
Description=Project Guard MySQL backup (daily 04:00)

[Timer]
OnCalendar=*-*-* 04:00:00
# 서버가 꺼져 있어 놓친 백업은 켜진 뒤 바로 한다.
Persistent=true

[Install]
WantedBy=timers.target
EOF

systemctl daemon-reload
systemctl enable --now guard-db-backup.timer
systemctl list-timers guard-db-backup.timer --no-pager
