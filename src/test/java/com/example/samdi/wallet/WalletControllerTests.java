package com.example.samdi.wallet;

import com.example.samdi.wallet.controller.WalletController;
import com.example.samdi.wallet.domain.TransactionType;
import com.example.samdi.wallet.dto.*;
import com.example.samdi.wallet.exception.*;
import com.example.samdi.wallet.service.WalletService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WalletControllerTests {
    private static final String BASE = "/api/users/me/wallet";
    private final WalletService service = mock(WalletService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(7L, null, "ROLE_USER"));
        mvc = MockMvcBuilders.standaloneSetup(new WalletController(service))
                .setControllerAdvice(new WalletExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void balanceIncludesTotalsAndVersion() throws Exception {
        when(service.getSummary(7L)).thenReturn(new WalletResponse(38000L, 50000L, 12000L, 4L));
        mvc.perform(get(BASE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(38000))
                .andExpect(jsonPath("$.totalIncome").value(50000))
                .andExpect(jsonPath("$.totalOutcome").value(12000))
                .andExpect(jsonPath("$.walletVersion").value(4));
    }

    @Test
    void incomeUsesAuthenticatedUserAndPreservesIntegerPrecision() throws Exception {
        mvc.perform(post(BASE + "/transactions/income").contentType(MediaType.APPLICATION_JSON).content("""
                {"amount":9007199254740993,"transactionDate":"2026-09-16",
                 "type":"DELIVERY_REWARD","referenceId":123,"userId":99}
                """ )).andExpect(status().isCreated());
        verify(service).registerIncome(eq(7L), eq(new TransactionRequest(new BigDecimal("9007199254740993"),
                LocalDate.of(2026, 9, 16), TransactionType.DELIVERY_REWARD, 123L)));
    }

    @Test
    void outcomeUsesOutcomeApi() throws Exception {
        mvc.perform(post(BASE + "/transactions/outcome").contentType(MediaType.APPLICATION_JSON).content("""
                {"amount":12000,"transactionDate":"2026-09-16",
                 "type":"ITEM_PURCHASE","referenceId":124}
                """ )).andExpect(status().isCreated());
        verify(service).registerOutcome(eq(7L), eq(new TransactionRequest(new BigDecimal("12000"),
                LocalDate.of(2026, 9, 16), TransactionType.ITEM_PURCHASE, 124L)));
    }

    @Test
    void malformedIncomeReturns400() throws Exception {
        mvc.perform(post(BASE + "/transactions/income").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":\"invalid\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        verifyNoInteractions(service);
    }

    @Test
    void persistenceConflictDoesNotExposeDatabaseDetails() throws Exception {
        when(service.registerIncome(eq(7L), any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("private database detail"));
        mvc.perform(post(BASE + "/transactions/income").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":1000,\"transactionDate\":\"2026-09-16\",\"type\":\"DELIVERY_REWARD\",\"referenceId\":1}"))
                .andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private database detail"))));
    }
}
