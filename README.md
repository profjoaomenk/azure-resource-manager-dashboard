# Azure Resource Manager Dashboard

Dashboard web para gerenciamento de recursos Azure de alunos. Interface intuitiva para visualizar, monitorar e deletar grupos de recursos de múltiplas assinaturas Azure.

![Java](https://img.shields.io/badge/Java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green)
![Azure CLI](https://img.shields.io/badge/Azure%20CLI-Required-blue)
![Copyright](https://img.shields.io/badge/Copyright-%C2%A9%202025-blue)

## 📋 Funcionalidades

- ✅ Visualização de múltiplas assinaturas Azure (apenas ativas com recursos)
- ✅ Listagem e expansão de grupos de recursos
- ✅ Deleção individual, múltipla ou total de grupos
- ✅ Cache inteligente (5 minutos) e processamento paralelo
- ✅ Interface moderna e responsiva

## 🚀 Instalação e Execução

git clone https://github.com/SEU-USUARIO/azure-resource-manager-dashboard.git

cd azure-resource-manager-dashboard
az login
mvn clean install
mvn spring-boot:run


Acesse: [**http://localhost:8080**](http://localhost:8080)

## 🎯 Como Usar

1. Selecione uma assinatura na sidebar esquerda
2. Clique no badge de recursos (📊) para expandir detalhes
3. Use os botões para deletar grupos individualmente ou em massa
4. Operações críticas requerem confirmação dupla

## 🛠️ Tecnologias

- Java 17, Spring Boot 3.2, Maven
- HTML5, CSS3, JavaScript
- Azure CLI
- Thymeleaf Template Engine

## 🐛 Troubleshooting

**Erro: "Sessão não encontrada"**

az login


**Assinatura não aparece**
- Apenas assinaturas ativas com recursos são exibidas

**Dashboard lento**
- Primeira carga: 1-2 segundos
- Com cache: ~100ms

## 👥 Autor

**Prof.João Menk**

## 📝 License & Copyright

Copyright © 2025 - João Menk and the Azure Resource Manager Dashboard contributors

This project is proprietary. All rights reserved.

No part of this project may be reproduced, distributed, or transmitted 
in any form or by any means without the prior written permission of 
the copyright holder.


---

⭐ Se este projeto foi útil, deixe uma estrela no GitHub! ⭐
