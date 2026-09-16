package com.example.samdi.wallet;

import com.example.samdi.wallet.domain.Wallet;
import com.example.samdi.wallet.exception.InsufficientBalanceException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WalletTests {
    @Test
    void gamePurchaseStillRejectsInsufficientBalance() {
        Wallet wallet = new Wallet(1L);
        wallet.increase(10000L);
        assertThrows(InsufficientBalanceException.class, () -> wallet.decrease(12000L));
        assertEquals(10000L, wallet.getBalance());
    }

    @Test
    void invalidOrOverflowingAmountsLeaveBalanceUnchanged() {
        Wallet wallet = new Wallet(1L);
        assertThrows(IllegalArgumentException.class, () -> wallet.increase(null));
        assertThrows(IllegalArgumentException.class, () -> wallet.increase(0L));
        assertThrows(IllegalArgumentException.class, () -> wallet.increase(-1L));
        wallet.increase(Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> wallet.increase(1L));
        assertEquals(Long.MAX_VALUE, wallet.getBalance());
    }
}
