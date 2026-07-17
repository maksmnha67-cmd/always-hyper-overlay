#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# Создаёт репозиторий на GitHub через API и пушит в него текущий проект.
#
# Нужен Personal Access Token (classic) со scope: repo, workflow
# Создать токен: https://github.com/settings/tokens/new
#
# Использование:
#   GITHUB_TOKEN=ghp_xxx ./scripts/push_to_github.sh <github_username> <repo_name> [private|public]
#
# Пример:
#   GITHUB_TOKEN=ghp_xxx ./scripts/push_to_github.sh shyz always-hyper private
# ---------------------------------------------------------------------------

if [ -z "${GITHUB_TOKEN:-}" ]; then
  echo "Ошибка: переменная окружения GITHUB_TOKEN не задана." >&2
  echo "Задай токен так: export GITHUB_TOKEN=ghp_xxxxxxxx" >&2
  exit 1
fi

if [ $# -lt 2 ]; then
  echo "Использование: $0 <github_username> <repo_name> [private|public]" >&2
  exit 1
fi

USERNAME="$1"
REPO_NAME="$2"
VISIBILITY="${3:-private}"

if [ "$VISIBILITY" = "private" ]; then
  IS_PRIVATE=true
else
  IS_PRIVATE=false
fi

echo "→ Создаю репозиторий ${USERNAME}/${REPO_NAME} (${VISIBILITY}) на GitHub..."

HTTP_CODE=$(curl -s -o /tmp/gh_create_repo_response.json -w "%{http_code}" \
  -X POST \
  -H "Authorization: token ${GITHUB_TOKEN}" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/user/repos \
  -d "{\"name\":\"${REPO_NAME}\",\"private\":${IS_PRIVATE}}")

if [ "$HTTP_CODE" = "201" ]; then
  echo "✓ Репозиторий создан."
elif [ "$HTTP_CODE" = "422" ]; then
  echo "! Репозиторий, похоже, уже существует — продолжаю и просто запушу в него."
else
  echo "Ошибка создания репозитория (HTTP $HTTP_CODE):" >&2
  cat /tmp/gh_create_repo_response.json >&2
  exit 1
fi

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_DIR"

if [ ! -d ".git" ]; then
  git init
  git branch -M main
fi

git add .
git commit -m "Always Hyper 1.3 beta" || echo "→ Нечего коммитить (уже всё закоммичено)."

REMOTE_URL="https://${USERNAME}:${GITHUB_TOKEN}@github.com/${USERNAME}/${REPO_NAME}.git"

if git remote get-url origin >/dev/null 2>&1; then
  git remote set-url origin "$REMOTE_URL"
else
  git remote add origin "$REMOTE_URL"
fi

echo "→ Пушу в origin/main..."
git push -u origin main

# Убираем токен из сохранённого remote URL, чтобы он не остался в .git/config
git remote set-url origin "https://github.com/${USERNAME}/${REPO_NAME}.git"

echo ""
echo "✓ Готово. Репозиторий: https://github.com/${USERNAME}/${REPO_NAME}"
echo "✓ Сборка запустится автоматически во вкладке Actions."
