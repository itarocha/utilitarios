package br.com.itarocha.utilitarios.qr;

public final class Messages {

    private Messages() {
    }

    // QRCodeTool (fachada CLI)
    public static final String INVALID_MODE = "Modo inválido. Use -encode, -decode ou -make_png.";
    public static final String USAGE_HEADER = "Uso:";
    public static final String USAGE_ENCODE = "  Codificar: java QRCodeTool -encode <arquivo_entrada> <diretorio_saida>";
    public static final String USAGE_DECODE = "  Decodificar: java QRCodeTool -decode <diretorio_entrada> <arquivo_saida>";
    public static final String USAGE_MAKE_PNG_TITLE = "  Gerar PNGs a partir de arquivos texto Base64:";
    public static final String USAGE_MAKE_PNG_CMD = "    java QRCodeTool -make_png <diretorio_entrada> [<diretorio_saida>]";
    public static final String USAGE_MAKE_PNG_HINT = "    (se <diretorio_saida> for omitido, usa o próprio diretório de entrada)";

    // Encoder
    public static final String EMPTY_INPUT_FILE = "Arquivo de entrada vazio: %s";
    public static final String ENCODE_DONE = "Codificação concluída. %d QR Codes gerados em: %s";

    // Decoder
    public static final String NO_QR_FOUND = "Nenhum arquivo QR Code encontrado em: %s";
    public static final String BAD_CHECKSUM = "Checksum inválido! Dados corrompidos.";
    public static final String DECODE_DONE = "Decodificação concluída. Arquivo recuperado: %s";

    // ImageMaker
    public static final String INPUT_DIR_NOT_FOUND = "Diretório de entrada não existe: %s";
    public static final String NO_TEXT_FILES = "Nenhum arquivo %s*.txt encontrado em: %s";
    public static final String PNGS_GENERATED = "PNGs gerados: %d em: %s";

    // ChunkCodec
    public static final String CHUNK_TOTAL_MISMATCH = "Inconsistência no número total de chunks: esperado %d, encontrado %d";
    public static final String CHUNK_DUPLICATED = "Chunk duplicado: %d";
    public static final String CHUNK_MISSING = "Faltam chunks: esperado %d, recebido %d";
    public static final String CHUNK_NOT_FOUND = "Chunk %d não encontrado.";

    // QrCodeImage
    public static final String EMPTY_BASE64 = "Conteúdo Base64 vazio.";
    public static final String INVALID_BASE64 = "Conteúdo não é Base64 válido.";
    public static final String QR_NOT_BASE64 = "Conteúdo do QR Code não é Base64 válido.";
}
