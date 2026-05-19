package rs.raf.trading.margin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rs.raf.trading.margin.dto.MarginAccountCheckDto;
import rs.raf.trading.margin.model.MarginAccount;
import rs.raf.trading.margin.model.MarginAccountStatus;

import java.util.List;

@Repository
public interface MarginAccountRepository extends JpaRepository<MarginAccount, Long> {

    /**
     * Pronalazi sve margin racune za datog korisnika
     */
    List<MarginAccount> findByUserId(Long userId);

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
}
