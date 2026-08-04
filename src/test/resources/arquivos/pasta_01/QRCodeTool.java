package br.com.itarocha.utilitarios.qr;

public class QRCodeTool {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            usage();
            System.exit(1);
        }

        String mode = args[0];
        if ("-encode".equalsIgnoreCase(mode)) {
            if (args.length < 3) {
                usage();
                System.exit(1);
            }
            Encoder.encode(args[1], args[2]);
        } else if ("-decode".equalsIgnoreCase(mode)) {
            if (args.length < 3) {
                usage();
                System.exit(1);
            }
            Decoder.decode(args[1], args[2]);
        } else if ("-make_png".equalsIgnoreCase(mode)) {
            ImageMaker.makePng(args[1], args.length >= 3 ? args[2] : null);
        } else {
            System.err.println(Messages.INVALID_MODE);
            System.exit(1);
        }
    }

    private static void usage() {
        System.err.println(Messages.USAGE_HEADER);
        System.err.println(Messages.USAGE_ENCODE);
        System.err.println(Messages.USAGE_DECODE);
        System.err.println(Messages.USAGE_MAKE_PNG_TITLE);
        System.err.println(Messages.USAGE_MAKE_PNG_CMD);
        System.err.println(Messages.USAGE_MAKE_PNG_HINT);
    }
}
