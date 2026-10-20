#!/usr/bin/env bash
# 운영 MySQL 일일 백업. guard-db-backup.timer가 매일 04:00에 실행한다 (install-db-backup.sh로 설치).
# 압축한 덤프를 /var/backups/guard에 14일치 보관한다. 서버 디스크 안에 있으므로 실수로 지운 데이터 복구용이고,
# 디스크·서버 자체가 사라지는 경우까지 대비하려면 가끔 개발 PC로 내려받는다(scp).
# 복구: gunzip -c 파일.sql.gz | sudo mysql project_guard
set -euo pipefail

BACKUP_DIR=/var/backups/guard
KEEP_DAYS=14
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

file="$BACKUP_DIR/project_guard-$(date +%Y%m%d-%H%M%S).sql.gz"
# --single-transaction: 서비스를 멈추지 않고 InnoDB 테이블을 한 시점 기준으로 덤프한다.
mysqldump --single-transaction --routines --triggers --no-tablespaces project_guard | gzip > "$file.tmp"
mv "$file.tmp" "$file"
chmod 600 "$file"

find "$BACKUP_DIR" -name 'project_guard-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
echo "백업 완료: $file ($(du -h "$file" | cut -f1))"
