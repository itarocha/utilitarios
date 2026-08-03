# Utilitarios

Projeto **Spring Boot 4.1.0** / **Java 21** que converte **qualquer arquivo** em uma sequência de QR Codes PNG e reconstrói o arquivo original a partir das imagens geradas.

O objetivo é permitir o transporte de arquivos arbitrários (binários ou texto) através de QR Codes, que podem ser lidos por dispositivos móveis e recombinados na ordem correta.

---

## Índice

- [Visão geral](#visão-geral)
- [Fluxo de dados](#fluxo-de-dados)
- [Detalhes de projeto e algoritmos](#detalhes-de-projeto-e-algoritmos)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Classes e métodos](#classes-e-métodos)
- [Uso (CLI)](#uso-cli)
- [Testes](#testes)
- [Dependências e build](#dependências-e-build)
- [Limitações e considerações](#limitações-e-considerações)

---

## Visão geral

A ferramenta opera em três modos:

1. **Codificação (`-encode`)**: lê um arquivo, aplica um pipeline de transformações e gera `N` imagens QR Code (`qr_001.png`, `qr_002.png`, …) num diretório de saída.
2. **Decodificação (`-decode`)**: lê as imagens `qr_*.png` de um diretório, reconstrói os dados e grava o arquivo original.
3. **Geração de PNGs a partir de textos Base64 (`-make_png`)**: converte todos os arquivos `arquivo_*.txt` de um diretório (por exemplo, copiados de um leitor de QR no celular) em QR Codes PNG (`qr_001.png`, `qr_002.png`, …). O diretório de saída é opcional; se omitido, usa o próprio diretório de entrada.

O código é organizado em classes pequenas e focadas, cada uma com responsabilidade única, permitindo que cada etapa do pipeline seja testada isoladamente.

---

## Fluxo de dados

### Codificação (encode)

```
Arquivo de entrada
      │
      ▼
Ler bytes (Files.readAllBytes)
      │
      ├──► Checksum.checksum      → CRC32 dos dados originais (para verificação posterior)
      │
      ▼
Compression.compress              → compactação GZIP
      │
      ▼
ChunkCodec.split                  → divisão em chunks com cabeçalho (índice/total/checksum)
      │
      ▼
QrCodeImage.write                 → Base64 + ZXing → PNG (imagem ≥ 500×500)
      │
      ▼
Diretório de saída (qr_001.png, qr_002.png, …)
```

### Decodificação (decode)

```
Diretório de entrada (qr_*.png)
      │
      ▼
Listar e ordenar por número
      │
      ▼
QrCodeImage.read                  → PNG → texto Base64 → payload (bytes)
      │
      ▼
ChunkCodec.reassemble             → remontagem + validação de chunks → bytes compactados
      │
      ▼
Compression.decompress            → descompactação GZIP
      │
      ▼
Checksum.verify                   → compara CRC32 com o armazenado no chunk 0
      │
      ▼
Arquivo de saída
```

### Regenerar PNGs a partir de textos Base64 (-make_png)

```
Diretório de entrada (arquivo_001.txt … arquivo_nnn.txt)
      │
      ▼
Listar e ordenar arquivo_*.txt
      │
      ▼
Para cada arquivo:
  QrCodeImage.writeFromBase64     → remove quebras de linha, decodifica Base64,
      │                             valida o conteúdo
      ▼
QrCodeImage.write                 → gera qr_001.png, qr_002.png, …
      │
      ▼
Diretório de saída (padrão: o próprio diretório de entrada)
```

---

## Detalhes de projeto e algoritmos

### Formato do payload de cada QR Code

Cada chunk é montado como um **payload binário** com cabeçalho seguido dos dados:

| Campo              | Tamanho  | Descrição                                  |
|--------------------|----------|---------------------------------------------|
| `índice`           | 2 bytes  | `short` big-endian com a posição do chunk   |
| `total`            | 2 bytes  | `short` big-endian com o nº total de chunks |
| `checksum` (CRC32) | 4 bytes  | `int` big-endian — **somente no chunk 0**   |
| `dados`            | variável | fatia dos dados compactados (GZIP)          |

- O cabeçalho completo do primeiro chunk tem **8 bytes** (índice + total + checksum).
- Os demais chunks têm cabeçalho de **4 bytes** (índice + total).
- O checksum é calculado sobre os **dados originais não compactados** e transportado apenas no primeiro chunk para economizar espaço.

### `MAX_QR_BYTES = 2100`

Limite de **bytes de dados** (após GZIP) por chunk. O valor considera:

- A capacidade máxima do QR Code versão 40 com correção de erro nível L: **2953 bytes** (modo byte).
- O overhead do **Base64**, que expande os dados em **4/3**.
- O cabeçalho do chunk (4–8 bytes).

Com 2100 bytes de dados:
- Payload (incluindo cabeçalho) ≈ **2108 bytes**.
- Base64 resultante ≈ **2812 caracteres** — dentro do limite de 2953, com margem de segurança de ~140 caracteres.

### Base64 e UTF-8

- O payload binário é convertido para **Base64** antes de ser inserido no QR Code.
- Isso garante que o conteúdo do QR seja **texto ASCII legível** por leitores móveis (que interpretam o QR como texto), evitando exibição de bytes binários ilegíveis.
- O charset usado na geração e na leitura é **UTF-8**.

### GZIP (compressão)

- Os dados originais são compactados com **GZIP** antes de serem divididos em chunks.
- Para dados repetitivos/texto, reduz bastante o número de QR Codes necessários.
- Para dados aleatórios (ex.: binários já compactados), o GZIP pode até **aumentar** ligeiramente o tamanho — comportamento esperado.

### CRC32 (integridade)

- Um **checksum CRC32** dos dados originais é calculado na codificação e guardado no primeiro chunk.
- Na decodificação, o checksum é recalculado sobre os dados recuperados e comparado ao armazenado.
- Se houver divergência, o processo falha com `IOException: Checksum inválido! Dados corrompidos.` — detectando corrupção/erro no transporte.

### Tamanho das imagens (ZXing)

- **`QR_PIXEL_SCALE = 2`**: cada módulo do QR é renderizado com **exatamente 2 pixels**. Testes empíricos (sweeps com dados aleatórios) mostraram que 1 pixel/módulo não é decodificável de forma confiável e que escalas maiores (ex.: 5 px/módulo) também **reduzem** a robustez de decodificação — o ZXing pode falhar ou devolver conteúdo errado em imagens grandes demais para certos payloads densos.
- **`MIN_IMAGE_SIZE = 500`**: a imagem final tem pelo menos **500×500 pixels**. Esse mínimo é atingido com **margem branca (padding)**, e **não** aumentando o tamanho do módulo.
- A versão do QR é escolhida **automaticamente** pelo ZXing (a menor que comporta o payload).
- **Nível de correção de erro L** (baixo): maximiza a quantidade de dados por QR.
- A leitura usa `TRY_HARDER` primeiro e, se não encontrar o QR, tenta novamente com `PURE_BARCODE` (modo que lê a matriz limpa de imagens geradas pela própria ferramenta).

### Nomeação e ordenação dos arquivos

- Imagens geradas: `qr_001.png`, `qr_002.png`, …, `qr_NNN.png` (3 dígitos, prefixo `qr_`, sufixo `.png`).
- Na decodificação, os arquivos são listados com glob `qr_*.png` e **ordenados pelo número** extraído do nome.
- A remontagem usa o **índice do cabeçalho** de cada chunk (campo autoritativo), independentemente da ordem de leitura.

---

## Estrutura do projeto

```
utilitarios/
├── pom.xml
├── mvnw / mvnw.cmd
└── src
    ├── main
    │   ├── java/br/com/itarocha/utilitarios/
    │   │   ├── UtilitariosApplication.java        # Bootstrap Spring Boot
    │   │   └── qr/
    │   │       ├── QRCodeTool.java                # Fachada + CLI
    │   │       ├── Constants.java                 # Constantes de nomes de arquivo
    │   │       ├── Messages.java                  # Constantes de mensagens
    │   │       ├── Compression.java               # GZIP
    │   │       ├── Checksum.java                  # CRC32
    │   │       ├── ChunkCodec.java                # Formato/remontagem de chunks
    │   │       └── QrCodeImage.java               # Geração/leitura de imagem
    │   └── resources/application.properties
    └── test
        ├── java/br/com/itarocha/utilitarios/
        │   ├── UtilitariosApplicationTests.java
        │   └── qr/
        │       ├── QRCodeToolTest.java
        │       ├── QRCodeToolIntegrationTest.java
        │       ├── CompressionTest.java
        │       ├── ChecksumTest.java
        │       ├── ChunkCodecTest.java
        │       └── QrCodeImageTest.java
        └── resources/
            └── arquivos/
                ├── pasta_01/   # QRCodeTool.java (1 QR)
                ├── pasta_02/   # README.md (4 QRs)
                └── pasta_03/   # texto_longo.txt (4 QRs)
```

---

## Classes e métodos

Todas as classes do pacote `br.com.itarocha.utilitarios.qr`, exceto a fachada, são `final` com construtor privado e métodos `static` (utilitários puros).

### `QRCodeTool` — fachada e ponto de entrada (CLI)

Classe de orquestração. Recebe as etapas realizadas pelas demais classes. Os nomes de arquivos são montados a partir das constantes de `Constants`.

**Métodos**

- **`main(String[] args)`** — ponto de entrada da CLI.
  - Parâmetros: `-encode <arquivo_entrada> <diretorio_saida>`, `-decode <diretorio_entrada> <arquivo_saida>` ou `-make_png <diretorio_entrada> [<diretorio_saida>]`.
  - Com menos de 2 argumentos imprime o uso e sai com código 1; `-encode`/`-decode` exigem 3 argumentos.
  - Modo desconhecido imprime erro e sai com código 1.

- **`encode(String inputFile, String outputDir)`**
  - Lê o arquivo de entrada; lança `IllegalArgumentException` se vazio.
  - Calcula o CRC32 dos dados originais.
  - Compacta com GZIP e divide em chunks (`ChunkCodec.split`).
  - Cria o diretório de saída, se necessário, e grava um PNG por chunk (`QrCodeImage.write`).
  - Imprime a quantidade de QR Codes gerados.

- **`decode(String inputDir, String outputFile)`**
  - Lista e ordena os arquivos `qr_*.png`.
  - Se não houver imagens, imprime aviso e retorna.
  - Lê o payload de cada imagem (`QrCodeImage.read`).
  - Remonta os chunks (`ChunkCodec.reassemble`).
  - Descompacta (`Compression.decompress`).
  - Verifica o checksum; lança `IOException` se divergente.
  - Grava o arquivo recuperado.

- **`makePng(String inputDir, String outputDir)`**
  - Lista e ordena os arquivos `arquivo_*.txt` do diretório de entrada.
  - Se `outputDir` for nulo ou vazio, usa o próprio diretório de entrada.
  - Para cada arquivo, chama `QrCodeImage.writeFromBase64` e gera `qr_001.png`, `qr_002.png`, … no diretório de saída.
  - Sem arquivos `arquivo_*.txt` → imprime aviso e retorna; diretório de entrada inexistente → `IllegalArgumentException`.
  - Imprime a quantidade de PNGs gerados.

- **`listQrFiles(Path dir)`** *(privado)* — lista os arquivos do diretório que casam com `qr_*.png`.

- **`listTextFiles(Path dir)`** *(privado)* — lista e ordena os arquivos do diretório que casam com `arquivo_*.txt`.

- **`extractChunkNumber(Path file)`** *(privado)* — extrai o número do nome (`qr_001.png` → `1`).

### `Constants` — constantes de nomenclatura

Classe `final` com construtor privado, contendo apenas constantes `public static final` usadas na montagem dos nomes de arquivos.

| Constante          | Valor        | Descrição                                                    |
|--------------------|--------------|--------------------------------------------------------------|
| `QR_PREFIX`        | `"qr_"`      | Prefixo dos arquivos de QR Code.                             |
| `TXT_PREFIX`       | `"arquivo_"` | Prefixo dos arquivos texto com conteúdo Base64.              |
| `FILE_NAME_DIGITS` | `3`          | Quantidade de dígitos do índice no nome (`qr_001`, `arquivo_001`, …). |

### `Messages` — constantes de mensagens

Classe `final` com construtor privado, contendo as mensagens de saída e de erro como constantes `public static final`. Mensagens com argumentos usam placeholders (`%s`, `%d`) e são formatadas na chamada via `String.formatted(...)`.

### `Compression` — compactação GZIP

**Métodos**

- **`compress(byte[] data)`** — compacta com `GZIPOutputStream` e retorna os bytes compactados. Lança `IOException`.
- **`decompress(byte[] data)`** — descompacta com `GZIPInputStream` e retorna os bytes originais. Lança `IOException` para dados inválidos.

### `Checksum` — integridade CRC32

**Métodos**

- **`checksum(byte[] data)`** — retorna o CRC32 (valor `long` sem sinal de 32 bits, 0–`0xFFFFFFFF`) dos dados.
- **`verify(byte[] data, long expected)`** — retorna `true` se o CRC32 de `data` for igual a `expected`.

### `ChunkCodec` — formato e remontagem de chunks

**Constantes e tipos**

- **`MAX_QR_BYTES = 2100`** — máximo de bytes de dados por chunk.
- **`record Reassembled(byte[] compressed, long storedChecksum)`** — resultado da remontagem: bytes compactados e o checksum lido do chunk 0.

**Métodos**

- **`split(byte[] compressed, int chunkSize, long checksum)`**
  - Divide os dados compactados em chunks de até `chunkSize` bytes.
  - Monta o payload de cada chunk com cabeçalho (`buildChunk`); o checksum vai apenas no índice 0.
  - Retorna `List<byte[]>` com os payloads prontos para o QR.
  - Lança `IOException`.

- **`buildChunk(int index, int total, Long checksum, byte[] data)`**
  - Monta um payload binário: `index` (short) + `total` (short) + `checksum` (int, se não-nulo) + `data`.
  - Lança `IOException`.

- **`reassemble(List<byte[]> payloads)`**
  - Lê o cabeçalho de cada payload (índice, total e checksum do chunk 0).
  - Validações:
    - **total inconsistente** → `IOException`.
    - **chunk duplicado** → `IOException`.
    - **chunk faltante** (tamanho do mapa ≠ total) → `IOException`.
  - Concatena os dados na ordem dos índices e retorna `Reassembled`.

### `QrCodeImage` — geração e leitura de imagens QR

**Constantes**

| Constante        | Valor | Descrição                                              |
|------------------|-------|--------------------------------------------------------|
| `QR_PIXEL_SCALE` | `2`   | Pixels por módulo (robustez de leitura; escala fixa).  |
| `MIN_IMAGE_SIZE` | `500` | Tamanho mínimo da imagem em pixels (margem branca).    |

**Métodos**

- **`write(byte[] payload, Path file)`**
  - Converte o payload para **Base64** (conteúdo texto legível).
  - Codifica o QR com ZXing (`QRCodeWriter`), correção de erro nível L e charset UTF-8, versão automática.
  - Renderiza com `QR_PIXEL_SCALE` px/módulo e adiciona margem branca centralizada até `MIN_IMAGE_SIZE`.
  - Grava o PNG. Lança `WriterException`/`IOException`.

- **`writeFromBase64(String base64, Path file)`**
  - Remove quebras de linha/espaços (`\s+`) do texto.
  - Decodifica o Base64 para os bytes do payload; conteúdo vazio → `IllegalArgumentException`, Base64 inválido → `IOException`.
  - Delega a renderização para `write`.

- **`read(Path file)`**
  - Lê a imagem (`ImageIO`), binariza e decodifica com `MultiFormatReader` — tenta `TRY_HARDER` e, em caso de `NotFoundException`, repete com `PURE_BARCODE` (charset UTF-8).
  - Converte o texto Base64 de volta para os bytes do payload.
  - Base64 inválido → `IOException` com mensagem. Lança `NotFoundException` se não houver QR na imagem.

### `UtilitariosApplication` — bootstrap Spring Boot

Classe anotada com `@SpringBootApplication`. `main` inicia o contexto Spring via `SpringApplication.run`. O Spring Boot é usado apenas como base do projeto; a funcionalidade de QR não depende do container.

---

## Uso (CLI)

Sintaxe:

```
# Codificar (gera os QR Codes)
java br.com.itarocha.utilitarios.qr.QRCodeTool -encode <arquivo_entrada> <diretorio_saida>

# Decodificar (recupera o arquivo)
java br.com.itarocha.utilitarios.qr.QRCodeTool -decode <diretorio_entrada> <arquivo_saida>

# Regenerar PNGs a partir dos arquivos texto com Base64 (saída = mesma pasta)
java br.com.itarocha.utilitarios.qr.QRCodeTool -make_png <diretorio_entrada>

# Regenerar PNGs em um diretório de saída específico
java br.com.itarocha.utilitarios.qr.QRCodeTool -make_png <diretorio_entrada> <diretorio_saida>
```

Exemplos:

```sh
# Codifica o arquivo documento.pdf no diretório ./qrs
java br.com.itarocha.utilitarios.qr.QRCodeTool -encode documento.pdf ./qrs

# Recupera o arquivo a partir de ./qrs
java br.com.itarocha.utilitarios.qr.QRCodeTool -decode ./qrs documento_recuperado.pdf

# Regenera os QRs na mesma pasta (saída omitida)
java br.com.itarocha.utilitarios.qr.QRCodeTool -make_png ./qrs

# Regenera os QRs em outra pasta
java br.com.itarocha.utilitarios.qr.QRCodeTool -make_png ./qrs ./qrs/pngs
```

Para executar com o Maven (com o plugin `exec` configurado ou via classpath):

```sh
./mvnw compile dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp "target/classes:$(cat cp.txt)" br.com.itarocha.utilitarios.qr.QRCodeTool -encode entrada.bin ./qrs
```

Comportamento de saída:

| Situação                    | Comportamento                          |
|-----------------------------|----------------------------------------|
| Poucos argumentos           | Imprime uso; **código de saída 1**.    |
| Modo inválido               | Imprime erro; **código de saída 1**.   |
| Arquivo de entrada vazio    | `IllegalArgumentException`.            |
| Diretório de entrada inexistente (`make_png`) | `IllegalArgumentException`. |
| Sem arquivos `arquivo_*.txt` (`make_png`)     | Imprime aviso e retorna sem erro. |
| Texto Base64 vazio          | `IllegalArgumentException`.            |
| Texto Base64 inválido       | `IOException`.                         |
| QR inválido/corrompido      | Exceção `IOException`/`NotFoundException`. |
| Sem imagens no diretório    | Imprime aviso e retorna sem erro.      |

---

## Testes

Execute todos os testes com:

```sh
./mvnw test
```

As classes de teste do pacote `qr` cobrem cada etapa isoladamente (testes unitários), o fluxo fim-a-fim via fachada e **cenários integrados completos por pasta**:

| Classe de teste          | Cobertura                                                                                                                                                                                                                          |
|--------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `QRCodeToolTest`         | Fim-a-fim via fachada: round-trips (pequeno, múltiplos chunks, binário), arquivo vazio lança exceção, chunk faltante e QR corrompido falham; `makePng` converte todos os `arquivo_*.txt` de um diretório em `qr_%03d.png` (saída omitida usa a mesma pasta), Base64 inválido lança `IOException` e diretório sem `arquivo_*.txt` não gera erro. |
| `QRCodeToolIntegrationTest` | Cenários completos por pasta: gera os QR Codes de um arquivo original, lê o conteúdo em `arquivo_NNN.txt`, regenera PNGs via `-make_png` (subpasta `make_png/`) e reconstrói o `<original>_traduzido.<ext>`, comparando o **MD5** com o original.           |
| `CompressionTest`        | Round-trip compress/decompress (pequeno, grande, vazio); dados inválidos lançam `IOException`.                                                                                                                                     |
| `ChecksumTest`           | Vetor padrão CRC32 (`"123456789"` → `0xCBF43926`); `verify` verdadeiro/falso; checksum de dados vazios.                                                                                                                            |
| `ChunkCodecTest`         | `split` gera quantidade/tamanhos esperados (cabeçalho 8/4 bytes); round-trip split→reassemble; chunk faltante/duplicado/inconsistente lançam `IOException`.                                                                        |
| `QrCodeImageTest`        | Imagem ≥500×500; `read` devolve o mesmo payload; conteúdo é Base64 legível; arquivo corrompido lança exceção; `writeFromBase64` (round-trip, com quebras de linha, Base64 inválido e conteúdo vazio).                              |

### Testes integrados — cenários por pasta

O `QRCodeToolIntegrationTest` usa `@ParameterizedTest` + `@MethodSource` para **ler todas as pastas `pasta_*`** em `src/test/resources/arquivos`. Cada pasta é um cenário completo e autocontido: adicionar uma nova `pasta_XX` com um arquivo original faz o cenário rodar automaticamente.

**Conteúdo de cada pasta após a execução:**

| Arquivo                                  | Origem                                                        |
|------------------------------------------|---------------------------------------------------------------|
| arquivo **original**                     | Único arquivo versionado na pasta (o ponto de partida).       |
| `qr_001.png … qr_00n.png`                | Gerados pelo encoder a partir do original.                    |
| `arquivo_001.txt … arquivo_nnn.txt`     | Conteúdo Base64 lido de cada QR (simula a leitura por celular). |
| `make_png/qr_001.png … qr_00n.png`       | Regenerados via `-make_png` a partir de cada arquivo texto.   |
| `<nome>_traduzido.<ext>`                 | Arquivo reconstruído via `decode`, com **MD5 igual ao original**. |

**Passos executados por cenário:**

1. Limpa os artefatos gerados anteriormente (`qr_*.png`, `arquivo_*.txt`, `arquivo_png_*.png`, `*_traduzido.*` e a subpasta `make_png/`), restando apenas o original.
2. `QRCodeTool.encode(original, pasta)` → gera os `qr_*.png`; verifica que a quantidade atende ao mínimo esperado do cenário.
3. Para cada `k`, lê `qr_00k.png` (`QrCodeImage.read`) e grava `arquivo_00k.txt` com `Base64.encodeToString(payload)`; valida que o conteúdo é Base64 válido e igual ao do payload.
4. `QRCodeTool.makePng(pasta, pasta/make_png)` → gera `make_png/qr_%03d.png`; valida que cada PNG decodifica para o mesmo payload de `qr_00k.png`.
5. `QRCodeTool.decode(pasta, <nome>_traduzido.<ext>)`; valida que o conteúdo é **idêntico** ao original.
6. Compara o **MD5** do original com o do arquivo traduzido.

**Cenários disponíveis:**

| Cenário    | Arquivo original | QR Codes | Observação                                   |
|------------|------------------|----------|----------------------------------------------|
| `pasta_01` | `QRCodeTool.java` | 1      | Fonte real, arquivo pequeno.                 |
| `pasta_02` | `README.md`      | 4      | Texto real grande do repositório.            |
| `pasta_03` | `texto_longo.txt`| 4      | Fixture texto determinístico (~24 KB).       |

> Observações: apenas o arquivo original é versionado; os demais artefatos são **regenerados a cada execução** (o teste remove os anteriores, tornando o cenário idempotente). O MD5 é a asserção final de integridade de cada cenário.

---

## Dependências e build

Dependências principais (`pom.xml`):

| Dependência                      | Versão      | Finalidade                              |
|----------------------------------|-------------|-----------------------------------------|
| `org.springframework.boot:spring-boot-starter` | 4.1.0 | Base do projeto Spring Boot.            |
| `com.google.zxing:core`          | 3.5.2       | Codificação/decodificação de QR Codes.  |
| `com.google.zxing:javase`        | 3.5.2       | Escrita/leitura de imagens (`MatrixToImageWriter`, `BufferedImageLuminanceSource`). |
| `spring-boot-starter-test`       | 4.1.0       | Testes (JUnit 5, escopo `test`).        |

Comandos úteis:

```sh
./mvnw compile      # compila
./mvnw test         # executa os testes
./mvnw package      # gera o artefato (jar)
```

---

## Limitações e considerações

- **Capacidade por QR Code**: cada QR transporta ~**2812 caracteres Base64** (≈2100 bytes de dados compactados). Arquivos maiores exigem mais imagens.
- **Número de QR Codes**: cresce com o tamanho do arquivo (após GZIP). Dados altamente repetitivos geram poucos QRs; dados aleatórios geram mais (o GZIP não reduz nesses casos).
- **Leitura por dispositivos móveis**: leitores que interpretam o QR como texto mostrarão a sequência Base64; a reconstrução do arquivo exige o uso da ferramenta (decode).
- **Integridade**: a verificação CRC32 detecta corrupção, mas não corrige erros — o processo falha em caso de divergência.
- **Correção de erro nível L**: prioriza capacidade de dados sobre tolerância a danos na imagem. Para imagens impressas sujeitas a danos, o nível poderia ser aumentado com redução de capacidade.
