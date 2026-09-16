package com.example.samdi.wallet;

import com.example.samdi.wallet.domain.*;
import com.example.samdi.wallet.dto.*;
import com.example.samdi.wallet.exception.*;
import com.example.samdi.wallet.repository.*;
import com.example.samdi.wallet.service.WalletService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WalletServiceTests {
    private final WalletRepository wallets = mock(WalletRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final WalletService service = new WalletService(wallets, transactions, entityManager);
    private final List<Transaction> entries = new ArrayList<>();
    private final Wallet wallet = new Wallet(7L);
    private final LocalDate date = LocalDate.of(2026, 9, 16);

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(wallet, "id", 11L);
        ReflectionTestUtils.setField(wallet, "version", 3L);
        when(wallets.findByUserId(7L)).thenReturn(Optional.of(wallet));
        when(wallets.findByUserIdForUpdate(7L)).thenReturn(Optional.of(wallet));
        when(transactions.findByTypeAndReferenceId(any(), anyLong())).thenAnswer(invocation ->
                entries.stream().filter(t -> t.getType() == invocation.getArgument(0)
                        && Objects.equals(t.getReferenceId(), invocation.getArgument(1))).findFirst());
        when(transactions.saveAndFlush(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction entry = invocation.getArgument(0);
            ReflectionTestUtils.setField(entry, "id", (long) entries.size() + 1);
            entries.add(entry);
            return entry;
        });
        when(transactions.findByWalletIdOrderByCreatedAtAscIdAsc(11L)).thenAnswer(invocation ->
                entries.stream().filter(t -> t.getWalletId().equals(11L))
                        .sorted(Comparator.comparing(Transaction::getCreatedAt).thenComparing(Transaction::getId)).toList());
    }

    private TransactionRequest income(long amount, long key) {
        return new TransactionRequest(BigDecimal.valueOf(amount), date, TransactionType.DELIVERY_REWARD, key);
    }

    private TransactionRequest outcome(long amount, long key) {
        return new TransactionRequest(BigDecimal.valueOf(amount), date, TransactionType.ITEM_PURCHASE, key);
    }

    @Test
    void registersIncomeAndOutcomeAndAllowsNegativeBalance() {
        service.registerIncome(7L, income(10000, 1));
        var response = service.registerOutcome(7L, outcome(12000, 2));
        assertEquals(-12000L, response.amount());
        assertEquals(-2000L, response.balanceAfter());
        assertEquals(-2000L, wallet.getBalance());
        assertEquals(new WalletResponse(-2000L, 10000L, 12000L, 3L), service.getSummary(7L));
        verify(wallets, times(2)).findByUserIdForUpdate(7L);
    }

    @Test
    void duplicateRegistrationDoesNotApplyTwice() {
        var first = service.registerIncome(7L, income(5000, 1));
        var second = service.registerIncome(7L, income(5000, 1));
        assertEquals(first.id(), second.id());
        assertEquals(5000L, wallet.getBalance());
        assertEquals(1, entries.size());
        verify(transactions, times(1)).saveAndFlush(any());
    }

    @Test
    void duplicateOutcomeDoesNotDeductTwice() {
        service.registerIncome(7L, income(10000, 1));
        service.registerOutcome(7L, outcome(3000, 2));
        service.registerOutcome(7L, outcome(3000, 2));
        assertEquals(7000L, wallet.getBalance());
        assertEquals(2, entries.size());
    }

    @Test
    void duplicateKeyWithDifferentContentIsRejected() {
        service.registerIncome(7L, income(5000, 1));
        assertThrows(IllegalArgumentException.class, () -> service.registerIncome(7L, income(8000, 1)));
        assertEquals(5000L, wallet.getBalance());
    }

    @Test
    void duplicateKeyOwnedByAnotherWalletIsRejected() {
        Transaction other = new Transaction(99L, 1000L, TransactionType.DELIVERY_REWARD, 1000L, 88L);
        ReflectionTestUtils.setField(other, "id", 42L);
        entries.add(other);
        assertThrows(IllegalArgumentException.class, () -> service.registerIncome(7L, income(1000, 88)));
        assertEquals(0L, service.getSummary(7L).balance());
        assertEquals(1000L, other.getAmount());
    }

    @Test
    void emptyWalletReturnsZeroTotals() {
        assertEquals(new WalletResponse(0L, 0L, 0L, 3L), service.getSummary(7L));
    }

    @Test
    void rejectsWrongDirectionMissingFieldsAndFractionalAmounts() {
        assertThrows(IllegalArgumentException.class, () -> service.registerIncome(7L, outcome(1000, 1)));
        assertThrows(IllegalArgumentException.class, () -> service.registerOutcome(7L, income(1000, 1)));
        for (BigDecimal amount : Arrays.asList(null, BigDecimal.ZERO, new BigDecimal("-1"), new BigDecimal("1.5"),
                new BigDecimal("9223372036854775808"))) {
            assertThrows(IllegalArgumentException.class, () -> service.registerIncome(7L,
                    new TransactionRequest(amount, date, TransactionType.DELIVERY_REWARD, 1L)));
        }
        assertThrows(IllegalArgumentException.class, () -> service.registerIncome(7L,
                new TransactionRequest(BigDecimal.ONE, null, TransactionType.DELIVERY_REWARD, 1L)));
        assertThrows(IllegalArgumentException.class, () -> service.registerIncome(7L,
                new TransactionRequest(BigDecimal.ONE, date, TransactionType.DELIVERY_REWARD, null)));
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test
    void persistenceFailurePropagatesWithoutChangingCachedBalance() {
        when(transactions.saveAndFlush(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("test"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> service.registerIncome(7L, income(1000, 1)));
        assertEquals(0L, wallet.getBalance());
        verify(entityManager, never()).flush();
    }

    @Test
    void missingWalletDoesNotReturnMisleadingZero() {
        assertThrows(WalletNotFoundException.class, () -> service.getSummary(99L));
    }

    @Test
    void missingPrincipalIsRejectedBeforeRepositoryAccess() {
        assertThrows(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class,
                () -> service.getSummary(null));
        verify(wallets, never()).findByUserId(isNull());
    }

    @Test
    void insertingBackdatedIncomeRecalculatesHistoricalBalances() {
        service.registerOutcome(7L, outcome(3000, 1));
        service.registerIncome(7L, new TransactionRequest(BigDecimal.valueOf(5000), date.minusDays(1),
                TransactionType.DELIVERY_REWARD, 2L));
        assertEquals(2000L, entries.get(0).getBalanceAfter());
        assertEquals(5000L, entries.get(1).getBalanceAfter());
        assertEquals(2000L, wallet.getBalance());
    }
}
