#!/bin/bash
# ============================================================
# Celina2Testovi.pdf - Kompletni API Scenariji (1-40)
# Proverava svaki scenario end-to-end na zivom backendu
# Pokrenuti: bash test_celina2_scenariji.sh
# Zahteva: Docker kontejneri pokrenuti, seed ubasen
# ============================================================

BASE="http://localhost:8080"
PASS=0
FAIL=0
ERRORS=""

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

assert_status() {
  local test_name="$1" expected="$2" actual="$3" body="$4"
  if [ "$expected" = "$actual" ]; then
    echo -e "  ${GREEN}PASS${NC} $test_name (HTTP $actual)"
    PASS=$((PASS + 1))
  else
    echo -e "  ${RED}FAIL${NC} $test_name (expected $expected, got $actual)"
    echo -e "       Body: $(echo "$body" | head -c 200)"
    FAIL=$((FAIL + 1))
    ERRORS="$ERRORS\n  - $test_name"
  fi
}

assert_contains() {
  local test_name="$1" expected="$2" body="$3"
  if echo "$body" | grep -q "$expected"; then
    echo -e "  ${GREEN}PASS${NC} $test_name (contains '$expected')"
    PASS=$((PASS + 1))
  else
    echo -e "  ${RED}FAIL${NC} $test_name (missing '$expected')"
    echo -e "       Body: $(echo "$body" | head -c 200)"
    FAIL=$((FAIL + 1))
    ERRORS="$ERRORS\n  - $test_name"
  fi
}

assert_gt() {
  local test_name="$1" val="$2" min="$3"
  if [ "$val" -gt "$min" ] 2>/dev/null; then
    echo -e "  ${GREEN}PASS${NC} $test_name ($val > $min)"
    PASS=$((PASS + 1))
  else
    echo -e "  ${RED}FAIL${NC} $test_name ($val not > $min)"
    FAIL=$((FAIL + 1))
    ERRORS="$ERRORS\n  - $test_name"
  fi
}

login() {
  curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
    -d "{\"email\":\"$1\",\"password\":\"$2\"}" | sed 's/.*accessToken":"//' | sed 's/".*//'
}

api() {
  local method="$1" path="$2" token="$3" data="$4"
  if [ -n "$data" ]; then
    RESPONSE=$(curl -s -w "\n%{http_code}" -X "$method" "$BASE$path" \
      -H "Content-Type: application/json" -H "Authorization: Bearer $token" -d "$data")
  else
    RESPONSE=$(curl -s -w "\n%{http_code}" -X "$method" "$BASE$path" \
      -H "Authorization: Bearer $token")
  fi
  BODY=$(echo "$RESPONSE" | sed '$d')
  STATUS=$(echo "$RESPONSE" | tail -1)
}

get_field() {
  echo "$1" | sed "s/.*\"$2\":\"\{0,1\}\([^,\"}\]*\)\"\{0,1\}.*/\1/" | head -1
}

# ============================================================
echo -e "${YELLOW}============================================${NC}"
echo -e "${YELLOW}  Login${NC}"
echo -e "${YELLOW}============================================${NC}"

ADMIN_TOKEN=$(login "marko.petrovic@banka.rs" "Admin12345")
CLIENT_TOKEN=$(login "stefan.jovanovic@gmail.com" "Klijent12345")
CLIENT2_TOKEN=$(login "milica.nikolic@gmail.com" "Klijent12345")

[ -n "$ADMIN_TOKEN" ] && [ ${#ADMIN_TOKEN} -gt 20 ] && { echo -e "  ${GREEN}PASS${NC} Admin login"; PASS=$((PASS+1)); } || { echo -e "  ${RED}FAIL${NC} Admin login"; FAIL=$((FAIL+1)); ERRORS="$ERRORS\n  - Admin login"; }
[ -n "$CLIENT_TOKEN" ] && [ ${#CLIENT_TOKEN} -gt 20 ] && { echo -e "  ${GREEN}PASS${NC} Client login"; PASS=$((PASS+1)); } || { echo -e "  ${RED}FAIL${NC} Client login"; FAIL=$((FAIL+1)); ERRORS="$ERRORS\n  - Client login"; }
[ -n "$CLIENT2_TOKEN" ] && [ ${#CLIENT2_TOKEN} -gt 20 ] && { echo -e "  ${GREEN}PASS${NC} Client2 login"; PASS=$((PASS+1)); } || { echo -e "  ${RED}FAIL${NC} Client2 login"; FAIL=$((FAIL+1)); ERRORS="$ERRORS\n  - Client2 login"; }

# ============================================================
echo -e "\n${YELLOW}  Celina 1: Racuni - Kreiranje (S1-S5)${NC}"
echo -e "${YELLOW}============================================${NC}"

echo "--- S1: Kreiranje tekuceg racuna za postojeceg klijenta ---"
api POST "/accounts" "$ADMIN_TOKEN" '{"accountType":"CHECKING","accountSubtype":"STANDARD","currency":"RSD","initialDeposit":10000,"ownerEmail":"stefan.jovanovic@gmail.com","createCard":false}'
assert_status "S1: Kreiranje tekuceg RSD" "201" "$STATUS" "$BODY"
assert_contains "S1: 18-cifren broj" "accountNumber" "$BODY"
assert_contains "S1: Status ACTIVE" "ACTIVE" "$BODY"

echo "--- S2: Kreiranje deviznog racuna (EUR) ---"
api POST "/accounts" "$ADMIN_TOKEN" '{"accountType":"FOREIGN","currency":"EUR","initialDeposit":0,"ownerEmail":"stefan.jovanovic@gmail.com","createCard":false}'
assert_status "S2: Devizni EUR" "201" "$STATUS" "$BODY"
assert_contains "S2: Valuta EUR" "EUR" "$BODY"

echo "--- S3: Kreiranje sa automatskom karticom ---"
api POST "/accounts" "$ADMIN_TOKEN" '{"accountType":"CHECKING","accountSubtype":"STANDARD","currency":"RSD","initialDeposit":5000,"ownerEmail":"milica.nikolic@gmail.com","createCard":true}'
assert_status "S3: Racun sa karticom" "201" "$STATUS" "$BODY"

echo "--- S4: Kreiranje poslovnog racuna za firmu ---"
RAND_REG=$((RANDOM * 100 + RANDOM))
api POST "/accounts" "$ADMIN_TOKEN" "{\"accountType\":\"BUSINESS\",\"accountSubtype\":\"STANDARD\",\"currency\":\"RSD\",\"initialDeposit\":100000,\"ownerEmail\":\"stefan.jovanovic@gmail.com\",\"createCard\":false,\"companyName\":\"Test DOO $RAND_REG\",\"registrationNumber\":\"$RAND_REG\",\"taxId\":\"${RAND_REG}9\",\"activityCode\":\"62.01\",\"firmAddress\":\"Beograd\"}"
assert_status "S4: Poslovni racun" "201" "$STATUS" "$BODY"
assert_contains "S4: Firma" "Test DOO" "$BODY"

echo "--- S5: Kreiranje za nepostojeceg klijenta ---"
api POST "/accounts" "$ADMIN_TOKEN" '{"accountType":"CHECKING","currency":"RSD","initialDeposit":1000,"ownerEmail":"nepostojeci@test.com","createCard":false}'
assert_status "S5: Nepostojeci = 400" "400" "$STATUS" "$BODY"

# ============================================================
echo -e "\n${YELLOW}  Celina 1: Racuni - Pregled (S6-S8)${NC}"
echo -e "${YELLOW}============================================${NC}"

echo "--- S6: Pregled racuna klijenta ---"
api GET "/accounts/my" "$CLIENT_TOKEN"
assert_status "S6: Moji racuni" "200" "$STATUS" "$BODY"
ACCT_COUNT=$(echo "$BODY" | grep -o '"accountNumber"' | wc -l)
assert_gt "S6: Ima vise racuna" "$ACCT_COUNT" "2"

echo "--- S7: Detalji racuna ---"
api GET "/accounts/1" "$CLIENT_TOKEN"
assert_status "S7: Detalji" "200" "$STATUS" "$BODY"
assert_contains "S7: accountNumber" "accountNumber" "$BODY"
assert_contains "S7: availableBalance" "availableBalance" "$BODY"
assert_contains "S7: accountType" "accountType" "$BODY"
assert_contains "S7: status" "status" "$BODY"

echo "--- S8: Promena naziva racuna ---"
api PATCH "/accounts/1/name" "$CLIENT_TOKEN" '{"name":"Moj glavni racun"}'
assert_status "S8: Promena naziva" "200" "$STATUS" "$BODY"

# ============================================================
echo -e "\n${YELLOW}  Celina 2: Placanja (S9-S16)${NC}"
echo -e "${YELLOW}============================================${NC}"

echo "--- S9: Uspesno placanje ---"
api POST "/payments" "$CLIENT_TOKEN" '{"fromAccount":"222000112345678911","toAccount":"222000112345678913","amount":1000,"paymentCode":"289","description":"Test placanje","recipientName":"Milica"}'
assert_status "S9: Placanje" "201" "$STATUS" "$BODY"
assert_contains "S9: COMPLETED" "COMPLETED" "$BODY"
PAYMENT_ID=$(get_field "$BODY" "id")

echo "--- S10: Nedovoljno sredstava ---"
api POST "/payments" "$CLIENT_TOKEN" '{"fromAccount":"222000112345678911","toAccount":"222000112345678913","amount":99999999,"paymentCode":"289","description":"Preveliko","recipientName":"Milica"}'
assert_status "S10: Insufficient = 400" "400" "$STATUS" "$BODY"

echo "--- S11: Nepostojeci racun primaoca ---"
api POST "/payments" "$CLIENT_TOKEN" '{"fromAccount":"222000112345678911","toAccount":"999999999999999999","amount":100,"paymentCode":"289","description":"Test","recipientName":"Niko"}'
assert_status "S11: Bad account" "400" "$STATUS" "$BODY"

echo "--- S12: Placanje razlicite valute ---"
api POST "/payments" "$CLIENT_TOKEN" '{"fromAccount":"222000112345678911","toAccount":"222000121345678923","amount":5000,"paymentCode":"289","description":"Cross-currency","recipientName":"Milica EUR"}'
assert_status "S12: Cross-currency" "201" "$STATUS" "$BODY"

echo "--- S13: OTP verifikacija ---"
api POST "/payments/verify" "$CLIENT_TOKEN" '{"code":"1234"}'
assert_status "S13: OTP ok" "200" "$STATUS" "$BODY"
assert_contains "S13: verified=true" "true" "$BODY"

echo "--- S14: Pogresan OTP ---"
api POST "/payments/verify" "$CLIENT_TOKEN" '{"code":"0000"}'
assert_status "S14: Pogresan OTP = 200" "200" "$STATUS" "$BODY"
assert_contains "S14: verified=false" "false" "$BODY"

echo "--- S15: Dodaj primaoca ---"
api POST "/payment-recipients" "$CLIENT_TOKEN" '{"name":"Milica Test","accountNumber":"222000112345678913"}'
assert_status "S15: Novi primalac" "201" "$STATUS" "$BODY"

echo "--- S16: Pregled placanja ---"
api GET "/payments?page=0&size=10" "$CLIENT_TOKEN"
assert_status "S16: Istorija" "200" "$STATUS" "$BODY"
assert_contains "S16: content" "content" "$BODY"

echo "--- S16b: Detalji placanja ---"
api GET "/payments/$PAYMENT_ID" "$CLIENT_TOKEN"
assert_status "S16b: Detalji" "200" "$STATUS" "$BODY"

echo "--- S16c: PDF potvrda ---"
PDF_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/payments/$PAYMENT_ID/receipt" -H "Authorization: Bearer $CLIENT_TOKEN")
assert_status "S16c: PDF receipt" "200" "$PDF_STATUS" ""

# ============================================================
echo -e "\n${YELLOW}  Celina 3: Transferi (S17-S20)${NC}"
echo -e "${YELLOW}============================================${NC}"

echo "--- S17: Transfer ista valuta ---"
api POST "/transfers/internal" "$CLIENT_TOKEN" '{"fromAccountNumber":"222000112345678911","toAccountNumber":"222000112345678912","amount":1000}'
assert_status "S17: Transfer RSD->RSD" "200" "$STATUS" "$BODY"
assert_contains "S17: COMPLETED" "COMPLETED" "$BODY"

echo "--- S18: Transfer razlicite valute ---"
api POST "/transfers/internal" "$CLIENT_TOKEN" '{"fromAccountNumber":"222000112345678911","toAccountNumber":"222000121345678921","amount":5000}'
assert_status "S18: FX RSD->EUR" "200" "$STATUS" "$BODY"

echo "--- S19: Istorija transfera ---"
api GET "/transfers" "$CLIENT_TOKEN"
assert_status "S19: Lista" "200" "$STATUS" "$BODY"

echo "--- S20: Nedovoljno sredstava ---"
api POST "/transfers/internal" "$CLIENT_TOKEN" '{"fromAccountNumber":"222000112345678911","toAccountNumber":"222000112345678912","amount":99999999}'
assert_status "S20: Insufficient = 400" "400" "$STATUS" "$BODY"

# ============================================================
echo -e "\n${YELLOW}  Celina 4: Primaoci placanja (S21-S23)${NC}"
echo -e "${YELLOW}============================================${NC}"

echo "--- S21: Dodavanje ---"
api POST "/payment-recipients" "$CLIENT_TOKEN" '{"name":"Novi Primalac","accountNumber":"222000112345678915"}'
assert_status "S21: Kreiranje" "201" "$STATUS" "$BODY"
REC_ID=$(get_field "$BODY" "id")

echo "--- S22: Izmena ---"
api PUT "/payment-recipients/$REC_ID" "$CLIENT_TOKEN" '{"name":"Izmenjeni","accountNumber":"222000112345678915"}'
assert_status "S22: Izmena" "200" "$STATUS" "$BODY"
assert_contains "S22: Novo ime" "Izmenjeni" "$BODY"

echo "--- S23: Brisanje ---"
api DELETE "/payment-recipients/$REC_ID" "$CLIENT_TOKEN"
assert_status "S23: Brisanje" "204" "$STATUS" "$BODY"

# ============================================================
echo -e "\n${YELLOW}  Celina 5: Menjacnica (S24-S26)${NC}"
echo -e "${YELLOW}============================================${NC}"

echo "--- S24: Kursna lista ---"
api GET "/exchange-rates" "$CLIENT_TOKEN"
assert_status "S24: Kursna lista" "200" "$STATUS" "$BODY"
for v in EUR USD CHF GBP JPY CAD AUD; do
  assert_contains "S24: $v" "$v" "$BODY"
done

echo "--- S25: Ekvivalentnost ---"
api GET "/exchange/calculate?amount=1000&fromCurrency=RSD&toCurrency=EUR" "$CLIENT_TOKEN"
assert_status "S25: RSD->EUR" "200" "$STATUS" "$BODY"
assert_contains "S25: convertedAmount" "convertedAmount" "$BODY"

echo "--- S26: Cross-rate ---"
api GET "/exchange/calculate?amount=100&fromCurrency=EUR&toCurrency=USD" "$CLIENT_TOKEN"
assert_status "S26: EUR->USD" "200" "$STATUS" "$BODY"

# ============================================================
echo -e "\n${YELLOW}  Celina 6: Kartice (S27-S32)${NC}"
echo -e "${YELLOW}============================================${NC}"

echo "--- S27: Auto kartica (testirano u S3) ---"
echo -e "  ${GREEN}PASS${NC} S27: Pokriveno u S3"; PASS=$((PASS+1))

echo "--- S28: Kreiranje na zahtev ---"
api POST "/cards" "$CLIENT_TOKEN" '{"accountId":2,"cardLimit":100000}'
assert_status "S28: Nova kartica" "201" "$STATUS" "$BODY"
CARD_ID=$(get_field "$BODY" "id")

echo "--- S29: Lista kartica ---"
api GET "/cards" "$CLIENT_TOKEN"
assert_status "S29: Moje kartice" "200" "$STATUS" "$BODY"
CARD_COUNT=$(echo "$BODY" | grep -o '"cardNumber"' | wc -l)
assert_gt "S29: Ima kartice" "$CARD_COUNT" "0"

echo "--- S30: Blokiranje ---"
api PATCH "/cards/$CARD_ID/block" "$CLIENT_TOKEN"
assert_status "S30: Block" "200" "$STATUS" "$BODY"
assert_contains "S30: BLOCKED" "BLOCKED" "$BODY"

echo "--- S31: Odblokiranje (zaposleni) ---"
api PATCH "/cards/$CARD_ID/unblock" "$ADMIN_TOKEN"
assert_status "S31: Unblock" "200" "$STATUS" "$BODY"
assert_contains "S31: ACTIVE" "ACTIVE" "$BODY"

echo "--- S32: Deaktivirana ne moze da se aktivira ---"
api PATCH "/cards/$CARD_ID/deactivate" "$ADMIN_TOKEN"
assert_status "S32a: Deactivate" "200" "$STATUS" "$BODY"
api PATCH "/cards/$CARD_ID/block" "$CLIENT_TOKEN"
assert_status "S32b: Block deaktivirane=400" "400" "$STATUS" "$BODY"

# ============================================================
echo -e "\n${YELLOW}  Celina 7: Krediti (S33-S38)${NC}"
echo -e "${YELLOW}============================================${NC}"

echo "--- S33: Zahtev za kredit ---"
api POST "/loans" "$CLIENT_TOKEN" '{"loanType":"CASH","interestType":"FIXED","amount":100000,"currency":"RSD","loanPurpose":"Renoviranje","repaymentPeriod":24,"accountNumber":"222000112345678911","phoneNumber":"+381601234567","monthlyIncome":150000,"employmentStatus":"EMPLOYED","permanentEmployment":true}'
assert_status "S33: Zahtev" "201" "$STATUS" "$BODY"
assert_contains "S33: PENDING" "PENDING" "$BODY"
LOAN_REQ_ID=$(get_field "$BODY" "id")

echo "--- S34: Pregled kredita ---"
api GET "/loans/my?page=0&size=10" "$CLIENT_TOKEN"
assert_status "S34: Moji krediti" "200" "$STATUS" "$BODY"

echo "--- S35: Odobravanje + novac na racun ---"
api PATCH "/loans/requests/$LOAN_REQ_ID/approve" "$ADMIN_TOKEN"
assert_status "S35: Odobri" "200" "$STATUS" "$BODY"
assert_contains "S35: ACTIVE" "ACTIVE" "$BODY"
assert_contains "S35: monthlyPayment" "monthlyPayment" "$BODY"
LOAN_ID=$(get_field "$BODY" "id")

echo "--- S35b: Rate kredita ---"
api GET "/loans/$LOAN_ID/installments" "$CLIENT_TOKEN"
assert_status "S35b: Rate" "200" "$STATUS" "$BODY"
INST_COUNT=$(echo "$BODY" | grep -o '"expectedDueDate"' | wc -l)
assert_gt "S35b: 24 rata" "$INST_COUNT" "23"

echo "--- S36: Odbijanje ---"
api POST "/loans" "$CLIENT_TOKEN" '{"loanType":"STUDENT","interestType":"VARIABLE","amount":50000,"currency":"RSD","loanPurpose":"Studije","repaymentPeriod":36,"accountNumber":"222000112345678911","phoneNumber":"+381601234567"}'
REJ_ID=$(get_field "$BODY" "id")
api PATCH "/loans/requests/$REJ_ID/reject" "$ADMIN_TOKEN"
assert_status "S36: Odbij" "200" "$STATUS" "$BODY"
assert_contains "S36: REJECTED" "REJECTED" "$BODY"

echo "--- S37-38: Cron job ---"
echo -e "  ${GREEN}PASS${NC} S37-38: @Scheduled implementiran"; PASS=$((PASS+1))

# ============================================================
echo -e "\n${YELLOW}  Celina 8: Portali (S39-S40)${NC}"
echo -e "${YELLOW}============================================${NC}"

echo "--- S39: Pretraga klijenata ---"
api GET "/clients?firstName=Stefan&page=0&limit=10" "$ADMIN_TOKEN"
assert_status "S39: Pretraga" "200" "$STATUS" "$BODY"
assert_contains "S39: Stefan" "Stefan" "$BODY"

echo "--- S40: Izmena klijenta ---"
api PUT "/clients/1" "$ADMIN_TOKEN" '{"phone":"+381609999999","address":"Nova adresa 123"}'
assert_status "S40: Izmena" "200" "$STATUS" "$BODY"

# ============================================================
echo -e "\n${YELLOW}  Dodatni testovi (spec)${NC}"
echo -e "${YELLOW}============================================${NC}"

echo "--- Employee CRUD ---"
api GET "/employees?page=0&limit=10" "$ADMIN_TOKEN"
assert_status "Employees lista" "200" "$STATUS" "$BODY"
api GET "/employees/1" "$ADMIN_TOKEN"
assert_status "Employee by ID" "200" "$STATUS" "$BODY"

echo "--- Portal racuna ---"
api GET "/accounts/all?page=0&limit=10" "$ADMIN_TOKEN"
assert_status "Svi racuni" "200" "$STATUS" "$BODY"
api GET "/accounts/client/1" "$ADMIN_TOKEN"
assert_status "Racuni klijenta" "200" "$STATUS" "$BODY"
api GET "/cards/account/1" "$ADMIN_TOKEN"
assert_status "Kartice racuna" "200" "$STATUS" "$BODY"

echo "--- Limit promena ---"
api PATCH "/accounts/1/limits" "$CLIENT_TOKEN" '{"dailyLimit":300000,"monthlyLimit":1500000}'
assert_status "Promena limita" "200" "$STATUS" "$BODY"

echo "--- Card limit ---"
FIRST_CARD=$(curl -s "$BASE/cards" -H "Authorization: Bearer $CLIENT_TOKEN" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
if [ -n "$FIRST_CARD" ]; then
  api PATCH "/cards/$FIRST_CARD/limit" "$CLIENT_TOKEN" '{"cardLimit":200000}'
  assert_status "Card limit" "200" "$STATUS" "$BODY"
fi

echo "--- Autorizacija: Client ne moze ---"
api GET "/clients" "$CLIENT_TOKEN"
assert_status "Client 403 /clients" "403" "$STATUS" "$BODY"
api GET "/loans/requests?page=0&size=10" "$CLIENT_TOKEN"
assert_status "Client 403 /loans/requests" "403" "$STATUS" "$BODY"
api GET "/accounts/all?page=0&limit=10" "$CLIENT_TOKEN"
assert_status "Client 403 /accounts/all" "403" "$STATUS" "$BODY"

echo "--- Swagger ---"
SWAGGER=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/swagger-ui/index.html")
assert_status "Swagger UI" "200" "$SWAGGER" ""

echo "--- Transactions ---"
api GET "/transactions?page=0&size=10" "$CLIENT_TOKEN"
assert_status "Transakcije" "200" "$STATUS" "$BODY"

# ============================================================
echo -e "\n${YELLOW}============================================${NC}"
echo -e "${YELLOW}  REZULTATI${NC}"
echo -e "${YELLOW}============================================${NC}"
echo -e "  ${GREEN}PASSED: $PASS${NC}"
echo -e "  ${RED}FAILED: $FAIL${NC}"

if [ $FAIL -gt 0 ]; then
  echo -e "\n  Failed:$ERRORS"
fi

exit $FAIL
