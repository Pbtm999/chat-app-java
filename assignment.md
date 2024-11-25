# Introdução

O trabalho consiste no desenvolvimento em Java de um servidor de chat e de um cliente simples para comunicar com ele. O servidor deve basear-se no modelo multiplex, aconselhando-se usar como ponto de partida o programa desenvolvido na **Ficha de Exercícios nº 5** das aulas práticas. Quanto ao cliente, deve partir deste esqueleto, que implementa uma interface gráfica simples, e completá-lo com a implementação do lado cliente do protocolo.

O cliente deve usar **duas threads**, de modo a poder receber mensagens do servidor enquanto espera que o utilizador escreva a próxima mensagem ou comando (caso contrário, bloquearia na leitura da socket, tornando a interface inoperacional).

---

## Linha de Comando

- **Servidor**: deve estar implementado numa classe chamada `ChatServer` e aceitar como argumento da linha de comando o número da porta TCP na qual ficará à escuta. Exemplo:

  ```bash
  java ChatServer 8000
  ```

- **Cliente**: deve estar implementado numa classe chamada `ChatClient` e aceitar como argumentos da linha de comando o nome DNS do servidor ao qual se quer conectar e o número da porta TCP em que o servidor está à escuta. Exemplo:

  ```bash
  java ChatClient localhost 8000
  ```

---

## Protocolo

O protocolo de comunicação é orientado à linha de texto, ou seja, cada mensagem enviada pelo cliente ao servidor ou pelo servidor ao cliente deve terminar com uma mudança de linha. A mensagem não pode conter mudanças de linha.

> **Nota:** O TCP não faz delineação de mensagens, o que significa que uma operação de leitura pode retornar apenas parte de uma mensagem ou várias mensagens juntas. O servidor deve fazer **buffering** para lidar com mensagens parcialmente recebidas.

### Mensagens enviadas pelo cliente

- Podem ser **comandos** (iniciados por `/`) ou **mensagens simples**.
- Mensagens simples só podem ser enviadas quando o utilizador está numa sala de chat.
- Se começarem por `/`, deve ser feito o **escape** com um `/` adicional. Exemplo:
  - Mensagem: `/exemplo` → Enviada como `//exemplo`.

### Comandos suportados pelo servidor

1. `/nick nome`  
   Escolhe ou muda o nome de um utilizador (o nome deve ser único).
2. `/join sala`  
   Entra ou cria uma sala de chat.
3. `/leave`  
   Sai da sala de chat atual.
4. `/bye`  
   Encerra a sessão do chat.

### Mensagens enviadas pelo servidor

- **OK**: Comando enviado pelo cliente foi bem-sucedido.
- **ERROR**: Comando enviado pelo cliente falhou.
- **MESSAGE nome mensagem**: Difunde uma mensagem para os utilizadores da sala.
- **NEWNICK nome_antigo nome_novo**: Notifica os utilizadores da mudança de nome.
- **JOINED nome**: Notifica que um novo utilizador entrou na sala.
- **LEFT nome**: Notifica que um utilizador saiu da sala.
- **BYE**: Confirmação de saída do chat.

---

## Estados do Cliente

O servidor mantém, para cada cliente, o estado associado. Os possíveis estados são:

1. **`init`**: O utilizador acabou de estabelecer a conexão e ainda não escolheu um nome.
2. **`outside`**: O utilizador já tem um nome, mas não está em nenhuma sala.
3. **`inside`**: O utilizador está numa sala de chat.

---

## Quadro de Estados e Transições

| Estado Atual | Evento                      | Ação                                   | Próximo Estado | Notas                                                            |
| ------------ | --------------------------- | -------------------------------------- | -------------- | ---------------------------------------------------------------- |
| `init`       | `/nick nome` (disponível)   | OK                                     | `outside`      | Nome fica indisponível para outros utilizadores.                 |
| `init`       | `/nick nome` (indisponível) | ERROR                                  | `init`         |                                                                  |
| `outside`    | `/join sala`                | OK para o utilizador; JOINED nome      | `inside`       | Entrou na sala e começa a receber mensagens.                     |
| `outside`    | `/nick nome` (disponível)   | OK                                     | `outside`      |                                                                  |
| `outside`    | `/nick nome` (indisponível) | ERROR                                  | `outside`      | Mantém o nome antigo.                                            |
| `inside`     | Mensagem                    | MESSAGE nome mensagem para todos       | `inside`       | Necessário escape para `/` inicial (`/ → //`, `// → ///`, etc.). |
| `inside`     | `/nick nome` (disponível)   | OK; NEWNICK nome_antigo nome_novo      | `inside`       |                                                                  |
| `inside`     | `/nick nome` (indisponível) | ERROR                                  | `inside`       | Mantém o nome antigo.                                            |
| `inside`     | `/join sala`                | OK para o utilizador; LEFT nome antiga | `inside`       | Entra na nova sala; deixa de receber mensagens da sala antiga.   |
| `inside`     | `/leave`                    | OK; LEFT nome                          | `outside`      | Sai da sala e deixa de receber mensagens.                        |
| `inside`     | `/bye`                      | BYE                                    | —              | Conexão encerrada.                                               |
| `inside`     | Fechou conexão              | LEFT nome                              | —              | Servidor encerra a conexão.                                      |
| `qualquer`   | Comando não suportado       | ERROR                                  | Mantém estado  |                                                                  |

---

## Exemplo de Diálogo

```plaintext
(cliente estabelece conexão com o servidor)
C→S: /nick maria
S→C: ERROR
C→S: /nick miquinhas
S→C: OK
C→S: /join moda
S→C: OK
S→O: JOINED miquinhas
C→S: Olá a todos!
S→C: MESSAGE miquinhas Olá a todos!
S→O: MESSAGE miquinhas Olá a todos!
C→S: /nick micas
S→C: OK
S→O: NEWNICK miquinhas micas
C→S: Tchau!
S→C: MESSAGE micas Tchau!
S→O: MESSAGE micas Tchau!
C→S: /join C++
S→C: OK
S→O: LEFT micas (na sala moda)
S→O: JOINED micas (na sala C++)
C→S: /// marca o início de um comentário em C++
S→C: MESSAGE micas // marca o início de um comentário em C++
S→O: MESSAGE micas // marca o início de um comentário em C++
C→S: /bye
S→C: BYE
S→O: LEFT micas
```

---

## Valorização

- **Servidor correto**: 50% da nota.
- **Cliente correto**: 35% da nota.
- **Comando `/priv nome mensagem`**: +10%.
- **Interface amigável no cliente**: +5%.

---

## Entrega

A entrega deve incluir:

1. `ChatServer.java` (código do servidor).
2. `ChatClient.java` (código do cliente).
3. `grupo.txt` (identificação do grupo).

**Formato do grupo.txt:**

```plaintext
201012345 Ana Beatriz Carvalho Duarte
201054321 Eduardo Fernando Gonçalves Henriques
```

A submissão deverá ser feita no Moodle por apenas um membro do grupo.

---

## Código de Honra

A submissão implica o compromisso de que o trabalho resultou exclusivamente do esforço do grupo. É proibido partilhar código entre grupos ou usar código de terceiros.
