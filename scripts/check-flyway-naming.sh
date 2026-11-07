#!/usr/bin/env bash
set -euo pipefail

migration_dir="$(cd "$(dirname "$0")/.." && pwd)/backend/platform/fenglin-hub-migrations/src/main/resources/db/migration"
pattern='^V[0-9]+(_[0-9]+)*__[a-z0-9_]+\.sql$'
failed=0

while IFS= read -r file; do
  name="$(basename "$file")"
  if [[ ! "$name" =~ $pattern ]]; then
    echo "非法 Flyway 文件名: $name（应匹配 V<version>__<snake_case>.sql）" >&2
    failed=1
  fi
done < <(find "$migration_dir" -maxdepth 1 -type f -name 'V*.sql' | sort)

if [[ "$failed" -ne 0 ]]; then
  exit 1
fi

echo "Flyway 迁移命名校验通过"
