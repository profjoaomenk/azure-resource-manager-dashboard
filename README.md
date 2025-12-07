# Azure Resource Manager Dashboard

Dashboard em **Java Spring Boot** para apoiar aulas de Cloud/DevOps, permitindo que alunos e instrutores visualizem **assinaturas**, **grupos de recursos** e **recursos Azure**, além de executar deleções em massa de forma controlada e visual.

> Copyright © Prof. João Menk

---

## Visão Geral

Este projeto implementa um painel web que consome a **Azure CLI** para gerenciar recursos de assinaturas Azure de forma visual e centralizada.

Principais recursos:

- Listagem de **assinaturas ativas com recursos**.
- Exibição de **grupos de recursos** e respectivos **recursos** da assinatura selecionada.
- Deleção de:
  - Um grupo de recursos específico.
  - Múltiplos grupos selecionados.
  - Todos os grupos de uma assinatura.
  - Todos os grupos de todas as assinaturas, com:
    - Execução assíncrona.
    - Indicadores visuais de deleção em andamento.
    - Filtro para **preservar grupos** pelo nome (exato, contém, inicia com).
- Destaque visual da **assinatura ativa** no momento da navegação.
- Interface compacta, focada em uso em laboratório/sala de aula.

---

## Tecnologias Utilizadas

- **Linguagem**: Java 17  
- **Framework**: Spring Boot 3.2.x  
- **Módulos Spring**:
  - spring-boot-starter-web  
  - spring-boot-starter-thymeleaf  
  - spring-boot-starter-logging  
  - spring-boot-starter-test (testes)  
- **Template Engine**: Thymeleaf  
- **Build**: Maven  
- **Bibliotecas**:
  - Lombok (DTOs e models)
  - Jackson (parse de JSON da Azure CLI)
  - Apache Commons Lang3  
- **Infra/CLI**:
  - Azure CLI (`az`), chamado via `ProcessBuilder`

---

## Pré-requisitos

Para executar o projeto localmente, é necessário:

- **Java 17** instalado e configurado (`JAVA_HOME`).
- **Maven** instalado (`mvn` no PATH).
- **Azure CLI** instalada e acessível:
  - Windows: `az`/`az.cmd`
  - Linux / MacOS: `az`
- Fazer login na CLI antes de usar o dashboard:

az login


Se você usa múltiplos tenants/contas, certifique-se de que a conta correta está ativa:

az account show


---

## Como Rodar o Projeto

### 1. Clonar o repositório

git clone https://github.com/profjoaomenk/azure-resource-manager-dashboard.git

cd azure-resource-manager-dashboard


### 2. Compilar

mvn clean compile



### 3. Rodar a aplicação

mvn spring-boot:run



Por padrão, a aplicação sobe em:

http://localhost:8080



---

## Funcionamento Interno

### Integração com Azure CLI

Toda a comunicação com o Azure é feita via **Azure CLI**, não diretamente via SDK:

- Listar assinaturas: `az account list --output json`
- Listar grupos: `az group list --subscription <id> --output json`
- Listar recursos de um grupo:  
  `az resource list --resource-group <nome> --subscription <id> --output json`
- Deletar grupo:  
  `az group delete --name <nome> --subscription <id> --yes`

Esses comandos são orquestrados por:

- `AzureCommandExecutor`  
- `AzureCliService`  
- `ResourceParserService`  
- `AzureAuthService`

Deleções em massa são executadas de forma **assíncrona** utilizando `CompletableFuture`, com um serviço de status (`DeletionStatusService`) que permite ao frontend acompanhar visualmente o progresso.

---

## Manual de Utilização

### 1. Acesso ao Dashboard

1. Abra o navegador em `http://localhost:8080`.
2. Certifique-se de que **já fez** `az login` no terminal que tem acesso à sua conta Azure.
3. Se a sessão não estiver ativa, o back-end retornará uma mensagem instruindo a rodar `az login`.

---

### 2. Lista de Assinaturas

Na coluna esquerda (sidebar):

- São exibidas as **assinaturas ativas que possuem grupos de recursos**.
- Cada item mostra o nome (displayName) em formato compacto.
- Ao clicar em uma assinatura:
  - Ela é destacada (cor e ícone de seleção).
  - O header mostra um indicador: **“Assinatura Ativa: ...”**.
  - O painel principal é recarregado com os grupos daquela assinatura.

---

### 3. Grupos de Recursos

Na área principal:

- No topo, aparece:
  - `Grupos de Recursos [N] em [Assinatura X]`.
- Cada grupo é exibido como um card compacto, contendo:
  - Nome do grupo.
  - Localização.
  - Quantidade de recursos.

Funcionalidades por card:

- Checkbox para seleção em ações em massa.
- Botão individual **🗑️** para deletar apenas aquele grupo.
- Ao clicar no badge de quantidade de recursos, o card expande e mostra:
  - Nome de cada recurso.
  - Tipo de recurso.

---

### 4. Deleção de Recursos

#### 4.1 Deletar um único grupo

1. Clique no botão **🗑️** do card.
2. Confirme o alerta.
3. O card entra em estado **“Excluindo...”** com overlay vermelho e spinner.
4. Um toast informa que a deleção está em segundo plano.
5. Ao concluir:
   - Aparece toast de sucesso.
   - O card desaparece com animação.
   - O contador de grupos é atualizado.

---

#### 4.2 Deletar grupos selecionados

1. Marque os checkboxes dos grupos desejados.
2. Clique em **“🗑️ Deletar Selecionados”**.
3. Confirme a deleção.
4. Os cards selecionados entram em estado **“Excluindo...”**.
5. A deleção roda em segundo plano, com toasts de feedback por grupo.

---

#### 4.3 Deletar todos os grupos de uma assinatura

1. Selecione uma assinatura.
2. Clique em **“🗑️ Deletar Todos da Assinatura”**.
3. Confirme o alerta crítico.
4. Todos os cards da assinatura são marcados como **“Excluindo...”**.
5. O backend chama `az group delete` para cada grupo da assinatura.

---

#### 4.4 Deletar todos os grupos de todas as assinaturas (com exclusões)

1. Clique em **“🗑️ Deletar Tudo”** na toolbar.
2. Um modal é aberto com:
   - Texto de alerta crítico.
   - Textarea para configurar **grupos excluídos da deleção**.
   - Opções de modo de correspondência:
     - **Exato**: nome do grupo igual ao texto.
     - **Contém**: nome contém o texto digitado.
     - **Inicia com**: nome começa com o texto.
3. Conforme você digita nomes/padrões, uma seção de **preview** mostra quais grupos (visíveis) serão preservados pelos filtros.
4. Ao confirmar:
   - É solicitado que digite `CONFIRMAR` para validação.
5. O backend:
   - Lista os grupos de todas as assinaturas.
   - Pula (preserva) os grupos que corresponderem aos filtros.
   - Deleta todos os demais, em modo assíncrono.
6. O frontend:
   - Marca todos os cards não excluídos como **“Excluindo...”**.
   - Exibe toasts de progresso e resultado.
   - Remove visualmente os grupos deletados ao finalizar.

---

## Tratamento de Erros

- Falta de sessão ativa na Azure CLI:
  - Gera mensagem clara para o usuário com instruções de executar `az login`.
- Erros de CLI (código de saída != 0):
  - São encapsulados em `AzureCliException`.
  - São logados com detalhamento.
  - São mostrados ao usuário em mensagens amigáveis, via alert/ toast.

---

## Evoluções na lista

- Autenticação e autorização (Spring Security) para controlar quem pode deletar recursos.
- Documentação OpenAPI/Swagger dos endpoints REST.
- Testes unitários e de integração.
- Filtros avançados por tipo de recurso, tags, ambiente (dev/homolog/prod).

---

## Licença e Direitos Autorais

Este projeto é de uso educacional, focado em práticas de DevOps e Cloud Computing com Azure.

- **Copyright ©**
  - Prof. João Menk

Considere manter esta atribuição ao reutilizar ou adaptar este código em outros contextos acadêmicos ou profissionais.
