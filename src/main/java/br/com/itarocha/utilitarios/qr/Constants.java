package br.com.itarocha.utilitarios.qr;

public final class Constants {

    private Constants() {
    }

    public static final String QR_PREFIX = "qr_";
    public static final String TXT_PREFIX = "arquivo_";
    public static final int FILE_NAME_DIGITS = 3;

    public static String qrFileName(int index) {
        return ("%s%0" + FILE_NAME_DIGITS + "d.png").formatted(QR_PREFIX, index);
    }
}
