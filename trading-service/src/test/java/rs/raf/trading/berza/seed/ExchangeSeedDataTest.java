package rs.raf.trading.berza.seed;

import org.junit.jupiter.api.Test;
import rs.raf.trading.berza.repository.ExchangeRepository;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExchangeSeedDataTest {

    @Test
    void run_seedsWhenEmpty() {
        ExchangeRepository repo = mock(ExchangeRepository.class);
        when(repo.count()).thenReturn(0L);
        when(repo.saveAll(anyIterable())).thenReturn(List.of());

        new ExchangeSeedData(repo).run(null);

        verify(repo).saveAll(anyIterable());
    }

    @Test
    void run_skipsWhenNonEmpty() {
        ExchangeRepository repo = mock(ExchangeRepository.class);
        when(repo.count()).thenReturn(6L);

        new ExchangeSeedData(repo).run(null);

        verify(repo, never()).saveAll(anyIterable());
    }
}
