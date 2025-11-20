package com.fiap.azure.service;

import com.fiap.azure.exception.AzureCliException;
import com.fiap.azure.util.AzureCommandExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AzureAuthService {

    private final AzureCommandExecutor commandExecutor;
    private static boolean isAuthenticated = false;

    public void ensureAuthenticated() throws AzureCliException {
        if (isAuthenticated) {
            return;
        }

        try {
            // Verifica se tem sessão ativa
            commandExecutor.execute("account", "show");
            isAuthenticated = true;
            log.info("✅ Sessão Azure ativa");
        } catch (Exception e) {
            log.error("❌ Sessão não encontrada");
            throw new AzureCliException(
                "❌ SESSÃO NÃO ENCONTRADA\n\n" +
                "EXECUTE NO TERMINAL:\n" +
                "  az login\n\n" +
                "Depois recarregue o dashboard (F5)", e);
        }
    }

    public void resetAuthentication() {
        isAuthenticated = false;
    }
}
