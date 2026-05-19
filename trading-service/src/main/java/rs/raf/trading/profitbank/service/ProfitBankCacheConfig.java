package rs.raf.trading.profitbank.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine cache za Profit Banke endpoint-e.
 *
 * <p>Cache <b>actuary-profit</b> se koristi u
 * {@link ActuaryProfitService#listAllActuariesProfit()} koji iterira sve DONE
 * ordere i konvertuje vrednosti u RSD. Posle 1000+ ordera u bazi, sirov racun
 * traje ~1-2s. TTL 5 min daje balans izmedju svezosti podataka i odziva
 * Profit Banke portala (supervizor refresh-uje stranu povremeno).</p>
 *
 * <p>Trenutno postoji jedan cache, ali config je pripremljen za dodavanje
 * novih (npr. fund-positions, fund-performance) bez ponovnog setupa.</p>
 *
 * <p>NAPOMENA (copy-first ekstrakcija, faza 2d-E): u monolitu je Spring cache
 * abstrakcija bila ukljucena globalno (banka-core ima druge cache-eve, npr.
 * actuary-limit). trading-service nema {@code @EnableCaching} nigde drugde —
 * {@code TradingUserResolver} koristi sirov Caffeine {@code Cache}, ne Spring
 * cache abstrakciju. Zato je {@code @EnableCaching} dodat ovde da bi
 * {@code @Cacheable}/{@code @CacheEvict} u {@code profitbank} paketu radili.
 * Ovo je jedini {@code CacheManager} u trading-service-u, pa {@code @Primary}
 * ne pravi konflikt.</p>
 */
@Configuration
@EnableCaching
public class ProfitBankCacheConfig {

    public static final String ACTUARY_PROFIT_CACHE = "actuary-profit";

    @Bean
    @Primary
    public CacheManager profitBankCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(ACTUARY_PROFIT_CACHE);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(100));
        return cacheManager;
    }
}
