#!/bin/bash
# 테스트 사용자 50명 생성 + API Key 수집
# 사용법: bash k6/setup-test-data.sh

BASE_URL="http://localhost:8080"
OUTPUT_FILE="k6/api-keys.json"
USER_COUNT=50

echo "=== 테스트 사용자 ${USER_COUNT}명 생성 시작 ==="
echo ""

# JSON 배열 시작
echo "[" > "$OUTPUT_FILE"

for i in $(seq 1 $USER_COUNT); do
    # 전화번호: 010 + 8자리 (01090000001 ~ 01090000050)
    NUMBER=$(printf "0109000%04d" $i)
    NAME="테스터"
    PASSWORD="111"
    BIRTH="1990-01-01"

    # 1. 회원가입
    SIGNUP_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/user/signup" \
        -H "Content-Type: application/json" \
        -d "{
            \"number\": \"${NUMBER}\",
            \"name\": \"${NAME}\",
            \"password\": \"${PASSWORD}\",
            \"birth\": \"${BIRTH}\",
            \"homeAddress\": \"06134\",
            \"homeStreetAddress\": \"서울 강남구 테헤란로 123\",
            \"homeStreetAddressDetail\": \"101동 101호\"
        }")

    SIGNUP_STATUS=$(echo "$SIGNUP_RESPONSE" | tail -1)

    # 이미 존재하는 사용자면 (409) 로그인만 진행
    if [ "$SIGNUP_STATUS" -eq 201 ] || [ "$SIGNUP_STATUS" -eq 200 ] || [ "$SIGNUP_STATUS" -eq 409 ] || [ "$SIGNUP_STATUS" -eq 400 ]; then

        # 2. 로그인 → API Key 획득
        SIGNIN_RESPONSE=$(curl -s -X POST "${BASE_URL}/user/signIn" \
            -H "Content-Type: application/json" \
            -d "{\"number\": \"${NUMBER}\", \"password\": \"${PASSWORD}\"}")

        API_KEY=$(echo "$SIGNIN_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('apiKey',''))" 2>/dev/null)

        if [ -n "$API_KEY" ] && [ "$API_KEY" != "" ]; then
            # JSON 항목 추가
            if [ $i -gt 1 ]; then
                echo "," >> "$OUTPUT_FILE"
            fi
            echo "  {\"number\": \"${NUMBER}\", \"apiKey\": \"${API_KEY}\"}" >> "$OUTPUT_FILE"
            echo "  [${i}/${USER_COUNT}] ${NUMBER} → API Key 획득 완료"
        else
            echo "  [${i}/${USER_COUNT}] ${NUMBER} → 로그인 실패 (응답: ${SIGNIN_RESPONSE})"
        fi
    else
        echo "  [${i}/${USER_COUNT}] ${NUMBER} → 회원가입 실패 (HTTP ${SIGNUP_STATUS})"
    fi
done

# JSON 배열 종료
echo "" >> "$OUTPUT_FILE"
echo "]" >> "$OUTPUT_FILE"

echo ""
echo "=== 완료: ${OUTPUT_FILE} 생성 ==="
echo "생성된 API Key 수: $(grep -c 'apiKey' "$OUTPUT_FILE")"
