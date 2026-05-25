package rs.raf.trading.margin.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rs.raf.trading.margin.dto.MarginAccountCheckDto;
import rs.raf.trading.margin.model.MarginAccount;
import rs.raf.trading.margin.model.MarginAccountStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface MarginAccountRepository extends JpaRepository<MarginAccount, Long> {

    /**
     * Pronalazi sve margin racune za datog korisnika (USER vlasnistvo).
     */
    List<MarginAccount> findByUserId(Long userId);

    /**
     * BE-STK-05: pronalazi prvi ACTIVE margin racun za korisnika.
     * Po Marzni_Racuni.txt §57: "korisnik moze imati samo jedan marzni racun".
     */
    Optional<MarginAccount> findFirstByUserIdAndStatus(Long userId, MarginAccountStatus status);

    /**
     * BE-STK-05: pessimistic lock za concurrent BUY/SELL settlement.
     * Sprecava race izmedju paralelnih order fill commit-a, deposit-a i margin call check-a.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MarginAccount m WHERE m.id = :id")
    Optional<MarginAccount> findByIdForUpdate(@Param("id") Long id);

    /**
     * Pronalazi sve margin racune sa datim statusom
     */
    List<MarginAccount> findByStatus(MarginAccountStatus status);

    /**
     * Pronalazi margin racun vezan za dati obicni racun
     */
    List<MarginAccount> findByAccountId(Long accountId);

    /**
     * Pronalazi sve margin racune koji treba da se blokiraju.
     *
     * <p>NAPOMENA (copy-first ekstrakcija, faza 2d-D): monolitna verzija je
     * JOIN-ovala {@code clients} tabelu da odmah popuni vlasnikov email. Pod
     * arhitekturom baza-po-servisu trading-service nema {@code clients} tabelu —
     * cross-schema JOIN bi pukao. Zato query bira samo {@code margin}-owned
     * kolone i puni {@code ownerUserId} (margin_accounts.user_id);
     * {@code ownerEmail} ostaje {@code NULL} i razresava ga
     * {@code MarginAccountService} preko banka-core internog API-ja.
     */
    @Query(value = """
                 SELECT\s
                     account.id AS marginAccountId,
                     account.user_id AS ownerUserId,
                     CAST(NULL AS VARCHAR) AS ownerEmail,
                     account.maintenance_margin AS maintenanceMargin,
                     account.initial_margin AS initialMargin
                 FROM margin_accounts account
                 WHERE account.status = :active
                   AND account.maintenance_margin > account.initial_margin
            \s""", nativeQuery = true)
    List<MarginAccountCheckDto> findAccountsForMarginCheck(
            @Param("active") String active
    );

    /**
     * Postavlja status racuna koji treba da se blokiraju u "BLOCKED"
     */
    @Modifying
    @Query(
            value = "UPDATE margin_accounts SET status = :blocked WHERE maintenance_margin > initial_margin AND status = :active",
            nativeQuery = true
    )
    void blockAccountsWhereMaintenanceExceedsInitial(@Param("active") String active, @Param("blocked") String blocked);

    /**
     * BE-STK-04 (2-step block, H2 test compat): pronalazi id-eve svih margin
     * racuna sa {@code maintenance_margin > initial_margin} koji su jos uvek
     * ACTIVE. Caller (servis) potom poziva {@link #bulkUpdateStatus} sa istom
     * listom da ih atomicno flipne na BLOCKED.
     *
     * <p>Was atomic UPDATE...RETURNING (PG-only). Now 2-step JPA for H2 test
     * compat. Race window: between SELECT and UPDATE, concurrent deposit moze
     * deactivate a row — handled via per-row check inside transaction context
     * (depozit ide nad ACTIVE redom sa pessimistic lock; ako je u medjuvremenu
     * flipnut na BLOCKED, depozit puca; sa druge strane scheduler trci unutar
     * @Transactional pa svi nadjeni id-evi se UPDATE-uju u istoj Tx).
     */
    @Query("SELECT m.id FROM MarginAccount m WHERE m.maintenanceMargin > m.initialMargin AND m.status = :activeStatus")
    List<Long> findEligibleForBlock(@Param("activeStatus") MarginAccountStatus activeStatus);

    /**
     * BE-STK-04: bulk UPDATE statusa za listu id-eva. Caller je
     * {@link #findEligibleForBlock} → flip ACTIVE → BLOCKED unutar iste Tx.
     */
    @Modifying
    @Query("UPDATE MarginAccount m SET m.status = :blockedStatus WHERE m.id IN :ids")
    int bulkUpdateStatus(@Param("ids") List<Long> ids, @Param("blockedStatus") MarginAccountStatus blockedStatus);
}
