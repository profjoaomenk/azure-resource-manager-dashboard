package com.fiap.azure.util;

import com.fiap.azure.config.AzureCliConfig;
import com.fiap.azure.exception.AzureCliException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

@Slf4j
@Component
@RequiredArgsConstructor
public class AzureCommandExecutor {

    private final AzureCliConfig azureCliConfig;

    public String execute(String... args) throws AzureCliException {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(buildCommand(args));

            log.debug("Executando comando Azure CLI: {}", String.join(" ", args));

            Process process = processBuilder.start();
            String result = readOutput(process);

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String error = readError(process);
                log.error("Erro ao executar comando Azure: {}", error);
                throw new AzureCliException("Azure CLI retornou código " + exitCode + ": " + error);
            }

            return result;
        } catch (IOException | InterruptedException e) {
            log.error("Erro ao executar comando Azure CLI", e);
            throw new AzureCliException("Erro na execução do comando Azure CLI", e);
        }
    }

    private String[] buildCommand(String[] args) {
        String[] command = new String[args.length + 1];
        command[0] = azureCliConfig.getPath();
        System.arraycopy(args, 0, command, 1, args.length);
        return command;
    }

    private String readOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }

    private String readError(Process process) throws IOException {
        StringBuilder error = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                error.append(line).append("\n");
            }
        }
        return error.toString();
    }
}
